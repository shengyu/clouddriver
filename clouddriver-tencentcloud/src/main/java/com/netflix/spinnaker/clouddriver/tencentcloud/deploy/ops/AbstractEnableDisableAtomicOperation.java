/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.EnableDisableTencentCloudServerGroupDescription;
import com.tencentcloudapi.as.v20180419.models.AutoScalingGroup;
import com.tencentcloudapi.as.v20180419.models.ForwardLoadBalancer;
import com.tencentcloudapi.as.v20180419.models.Instance;
import com.tencentcloudapi.as.v20180419.models.TargetAttribute;
import com.tencentcloudapi.clb.v20180317.models.Backend;
import com.tencentcloudapi.clb.v20180317.models.ListenerBackend;
import com.tencentcloudapi.clb.v20180317.models.RuleTargets;
import com.tencentcloudapi.clb.v20180317.models.Target;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

public abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {

  private EnableDisableTencentCloudServerGroupDescription description;
  private AutoScalingClient asClient;

  public abstract boolean isDisable();

  public abstract String getBasePhase();

  public AbstractEnableDisableAtomicOperation(
      AutoScalingClient asClient, EnableDisableTencentCloudServerGroupDescription description) {
    this.asClient = asClient;
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    String basePhase = getBasePhase();
    getTask()
        .updateStatus(
            basePhase,
            "Initializing disable server group $description.serverGroupName in $description.region...");

    String serverGroupName = description.getServerGroupName();
    String region = description.getRegion();
    // find auto scaling group
    AutoScalingGroup asg = getAutoScalingGroup(serverGroupName);
    if (asg == null) {
      return null;
    }
    String asgId = asg.getAutoScalingGroupId();

    // enable or disable auto scaling group
    enableOrDisableAutoScalingGroup(asgId);

    // get in service instances in auto scaling group
    List<String> inServiceInstanceIds = getInServiceAutoScalingInstances(asgId);

    if (CollectionUtils.isEmpty(inServiceInstanceIds)) {
      getTask().updateStatus(basePhase, "Auto scaling group has no IN_SERVICE instance.");
      return null;
    }

    // enable or disable load balancer
    if (asg.getLoadBalancerIdSet() == null && asg.getForwardLoadBalancerSet() == null) {
      getTask().updateStatus(basePhase, "Auto scaling group does not have a load balancer.");
      return null;
    }

    try {
      enableOrDisableClassicLoadBalancer(asg, inServiceInstanceIds);
      enableOrDisableForwardLoadBalancer(asg, inServiceInstanceIds);
      if (isDisable()) {
        asClient.removeInstances(asgId, inServiceInstanceIds);
      }
    } catch (TencentCloudSDKException e) {
      return null;
    }
    if (isDisable()) {
      getTask()
          .updateStatus(basePhase, "Complete disable server group $serverGroupName in $region.");
    } else {
      getTask()
          .updateStatus(basePhase, "Complete enable server group $serverGroupName in $region.");
    }

    return null;
  }

  private AutoScalingGroup getAutoScalingGroup(String serverGroupName) {
    List<AutoScalingGroup> asgs = asClient.getAutoScalingGroupsByName(serverGroupName);
    if (!CollectionUtils.isEmpty(asgs)) {
      AutoScalingGroup asg = asgs.get(0);
      String asgId = asg.getAutoScalingGroupId();
      getTask()
          .updateStatus(
              getBasePhase(), "Server group $serverGroupName's auto scaling group id is " + asgId);
      return asg;
    } else {
      getTask().updateStatus(getBasePhase(), "Server group $serverGroupName is not found.");
      return null;
    }
  }

  private void enableOrDisableAutoScalingGroup(String asgId) {
    String basePhase = getBasePhase();
    if (isDisable()) {
      getTask().updateStatus(basePhase, String.format("Disabling auto scaling group %s...", asgId));
      asClient.disableAutoScalingGroup(asgId);
      getTask()
          .updateStatus(
              basePhase, String.format("Auto scaling group %s status is disabled.", asgId));
    } else {
      getTask().updateStatus(basePhase, String.format("Enabling auto scaling group %s...", asgId));
      asClient.enableAutoScalingGroup(asgId);
      getTask()
          .updateStatus(
              basePhase, String.format("Auto scaling group %s status is enabled.", asgId));
    }
  }

  private List<String> getInServiceAutoScalingInstances(String asgId) {
    String basePhase = getBasePhase();
    getTask()
        .updateStatus(
            basePhase, String.format("Get instances managed by auto scaling group %s", asgId));

    List<Instance> instances = asClient.getAutoScalingInstances(asgId);

    if (CollectionUtils.isEmpty(instances)) {
      getTask().updateStatus(basePhase, String.format("Found no instance in %s.", asgId));
      return null;
    }

    List<String> inServiceInstanceIds =
        instances.stream()
            .filter(
                it ->
                    it.getHealthStatus().equals("HEALTHY")
                        && it.getLifeCycleState().equals("IN_SERVICE"))
            .map(Instance::getInstanceId)
            .collect(Collectors.toList());

    getTask()
        .updateStatus(
            basePhase,
            String.format(
                "Auto scaling group %s has InService instances %s",
                asgId, inServiceInstanceIds.toString()));

    return inServiceInstanceIds;
  }

  private void enableOrDisableClassicLoadBalancer(
      AutoScalingGroup asg, List<String> inServiceInstanceIds) {

    if (asg.getLoadBalancerIdSet() == null) {
      return;
    }

    String[] classicLbs = asg.getLoadBalancerIdSet();
    getTask()
        .updateStatus(
            getBasePhase(),
            "Auto scaling group is attached to classic load balancers "
                + Arrays.toString(classicLbs));

    for (String lbId : classicLbs) {
      if (isDisable()) {
        deregisterInstancesFromClassicalLb(lbId, inServiceInstanceIds);
      } else {
        registerInstancesWithClassicalLb(lbId, inServiceInstanceIds);
      }
    }
  }

  private void deregisterInstancesFromClassicalLb(String lbId, List<String> inServiceInstanceIds) {
    String basePhase = getBasePhase();
    getTask()
        .updateStatus(
            basePhase,
            "Start detach instances $inServiceInstanceIds from classic load balancers " + lbId);

    Set<String> classicLbInstanceIds = asClient.getClassicLbInstanceIds(lbId);
    List<String> instanceIds =
        inServiceInstanceIds.stream()
            .filter(classicLbInstanceIds::contains)
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(instanceIds)) {
      getTask()
          .updateStatus(
              basePhase,
              "Classic load balancer has instances $classicLbInstanceIds "
                  + "instances "
                  + instanceIds
                  + " in both auto scaling group and load balancer will be detached from load balancer.");
      asClient.detachAutoScalingInstancesFromClassicClb(lbId, instanceIds);
    } else {
      getTask()
          .updateStatus(
              basePhase,
              "Instances $inServiceInstanceIds are not attached with load balancer " + lbId);
    }
    getTask()
        .updateStatus(
            basePhase,
            "Finish detach instances $inServiceInstanceIds from classic load balancers " + lbId);
  }

  private void registerInstancesWithClassicalLb(String lbId, List<String> inServiceInstanceIds) {
    String basePhase = getBasePhase();
    getTask()
        .updateStatus(
            basePhase,
            "Start attach instances $inServiceInstanceIds to classic load balancers " + lbId);
    List<Target> inServiceClassicTargets =
        inServiceInstanceIds.stream()
            .map(
                id -> {
                  Target target = new Target();
                  target.setInstanceId(id);
                  target.setWeight(10L);
                  return target;
                })
            .collect(Collectors.toList());

    asClient.attachAutoScalingInstancesToClassicClb(lbId, inServiceClassicTargets);
    getTask()
        .updateStatus(
            basePhase,
            "Finish attach instances $inServiceInstanceIds to classic load balancers " + lbId);
  }

  private void enableOrDisableForwardLoadBalancer(
      AutoScalingGroup asg, List<String> inServiceInstanceIds) throws TencentCloudSDKException {
    if (asg.getForwardLoadBalancerSet() == null) {
      return;
    }

    ForwardLoadBalancer[] forwardLbs = asg.getForwardLoadBalancerSet();

    for (ForwardLoadBalancer flb : forwardLbs) {
      if (isDisable()) {
        deregisterInstancesFromForwardLb(flb, inServiceInstanceIds);
      } else {
        registerInstancesWithForwardLb(flb, inServiceInstanceIds);
      }
    }
  }

  private void deregisterInstancesFromForwardLb(
      ForwardLoadBalancer flb, List<String> inServiceInstanceIds) throws TencentCloudSDKException {
    String basePhase = getBasePhase();
    String flbId = flb.getLoadBalancerId();
    getTask()
        .updateStatus(
            basePhase,
            "Start detach instances $inServiceInstanceIds from forward load balancers " + flbId);

    List<ListenerBackend> listeners = asClient.getForwardLbTargets(flb);
    Set<Target> forwardLbTargets = new HashSet<>();
    List<Target> inServiceTargets = new ArrayList<>();

    for (String instanceId : inServiceInstanceIds) {
      for (TargetAttribute attribute : flb.getTargetAttributes()) {
        Target target = new Target();
        target.setInstanceId(instanceId);
        target.setWeight(attribute.getWeight());
        target.setPort(attribute.getPort());
        inServiceTargets.add(target);
      }
    }

    for (ListenerBackend listenerBackend : listeners) {
      String protocol = listenerBackend.getProtocol();
      if (protocol.equals("HTTP") || protocol.equals("HTTPS")) {
        for (RuleTargets rule : listenerBackend.getRules()) {
          if (rule.getLocationId().equals(flb.getLocationId())) {
            for (Backend backend : rule.getTargets()) {
              Target target = new Target();
              target.setInstanceId(backend.getInstanceId());
              target.setWeight(backend.getWeight());
              target.setPort(backend.getPort());
              forwardLbTargets.add(target);
            }
          }
        }
      } else if (protocol.equals("TCP") || protocol.equals("UDP")) {
        for (Backend backend : listenerBackend.getTargets()) {
          Target target = new Target();
          target.setInstanceId(backend.getInstanceId());
          target.setWeight(backend.getWeight());
          target.setPort(backend.getPort());
          forwardLbTargets.add(target);
        }
      } else {
        return;
      }
    }

    List<Target> targets =
        inServiceTargets.stream().filter(forwardLbTargets::contains).collect(Collectors.toList());

    if (!CollectionUtils.isEmpty(targets)) {
      getTask()
          .updateStatus(
              basePhase,
              "Forward load balancer has targets "
                  + forwardLbTargets
                  + "targets $targets in both auto scaling group and load balancer will be detached "
                  + "from load balancer "
                  + flbId);
      asClient.detachAutoScalingInstancesFromForwardClb(flb, targets);
    } else {
      getTask()
          .updateStatus(
              basePhase,
              "Instances $inServiceInstanceIds are not attached with load balancer " + flbId);
    }

    getTask()
        .updateStatus(
            basePhase,
            "Finish detach instances $inServiceInstanceIds from forward load balancers " + flbId);
  }

  private void registerInstancesWithForwardLb(
      ForwardLoadBalancer flb, List<String> inServiceInstanceIds) throws TencentCloudSDKException {
    String basePhase = getBasePhase();
    String flbId = flb.getLoadBalancerId();
    getTask()
        .updateStatus(
            basePhase,
            "Start attach instances $inServiceInstanceIds from forward load balancers " + flbId);

    List<Target> inServiceTargets = new ArrayList<>();
    for (String instanceId : inServiceInstanceIds) {
      for (TargetAttribute attribute : flb.getTargetAttributes()) {
        Target target = new Target();
        target.setInstanceId(instanceId);
        target.setWeight(attribute.getWeight());
        target.setPort(attribute.getPort());
        inServiceTargets.add(target);
      }
    }

    if (!CollectionUtils.isEmpty(inServiceTargets)) {
      getTask()
          .updateStatus(
              basePhase,
              "In service targets $inServiceTargets will be attached to forward load balancer "
                  + flbId);
      asClient.attachAutoScalingInstancesToForwardClb(flb, inServiceTargets);
    } else {
      getTask()
          .updateStatus(
              basePhase, "No instances need to be attached to forward load balancer " + flbId);
    }
    getTask()
        .updateStatus(
            basePhase,
            "Finish attach instances $inServiceInstanceIds from forward load balancers " + flbId);
  }
}
