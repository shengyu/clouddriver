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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.TencentCloudDeployDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.handlers.TencentCloudDeployHandler;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudServerGroup;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudClusterProvider;
import com.tencentcloudapi.as.v20180419.models.AutoScalingGroup;
import com.tencentcloudapi.as.v20180419.models.DataDisk;
import com.tencentcloudapi.as.v20180419.models.EnhancedService;
import com.tencentcloudapi.as.v20180419.models.ForwardLoadBalancer;
import com.tencentcloudapi.as.v20180419.models.InstanceMarketOptionsRequest;
import com.tencentcloudapi.as.v20180419.models.InternetAccessible;
import com.tencentcloudapi.as.v20180419.models.LaunchConfiguration;
import com.tencentcloudapi.as.v20180419.models.LoginSettings;
import com.tencentcloudapi.as.v20180419.models.SystemDisk;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class CloneTencentCloudServerGroupAtomicOperation
    implements AtomicOperation<DeploymentResult> {

  private static final String BASE_PHASE = "CLONE_SERVER_GROUP";

  private TencentCloudDeployDescription description;
  @Autowired private TencentCloudClusterProvider tencentCloudClusterProvider;
  @Autowired private TencentCloudDeployHandler tencentCloudDeployHandler;

  public CloneTencentCloudServerGroupAtomicOperation(TencentCloudDeployDescription description) {
    this.description = description;
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    TencentCloudDeployDescription newDescription = cloneAndOverrideDescription();
    return tencentCloudDeployHandler.handle(newDescription, priorOutputs);
  }

  private TencentCloudDeployDescription cloneAndOverrideDescription() {
    TencentCloudDeployDescription newDescription = new TencentCloudDeployDescription();
    BeanUtils.copyProperties(description, newDescription);

    if (description != null && description.getSource() != null) {
      if (description.getSource().getRegion() == null
          || description.getSource().getServerGroupName() == null) {
        return newDescription;
      }
    }
    if (description == null) {
      return newDescription;
    }
    String sourceServerGroupName = description.getSource().getServerGroupName();
    String sourceRegion = description.getSource().getRegion();
    String accountName = description.getAccountName();
    getTask()
        .updateStatus(
            BASE_PHASE, "Initializing copy of server group " + sourceServerGroupName + "...");

    // look up source server group
    TencentCloudServerGroup sourceServerGroup =
        tencentCloudClusterProvider.getServerGroup(
            accountName, sourceRegion, sourceServerGroupName);

    if (sourceServerGroup == null) {
      return newDescription;
    }

    // start override source description
    String region = description.getRegion();
    newDescription.setRegion(region != null ? region : sourceRegion);
    String application = description.getApplication();
    newDescription.setApplication(
        application != null ? application : sourceServerGroup.getMoniker().getApp());
    String stack = description.getStack();
    newDescription.setStack(stack != null ? stack : sourceServerGroup.getMoniker().getStack());

    ObjectMapper objectMapper = new ObjectMapper();
    LaunchConfiguration sourceLaunchConfig =
        objectMapper.convertValue(sourceServerGroup.getLaunchConfig(), LaunchConfiguration.class);
    if (sourceLaunchConfig != null) {
      String type = description.getInstanceType();
      String newType = type != null ? type : sourceLaunchConfig.getInstanceType();
      newDescription.setInstanceType(newType);

      String imageId = description.getImageId();
      String newImageId = imageId != null ? imageId : sourceLaunchConfig.getImageId();
      newDescription.setImageId(newImageId);

      Long projectId = description.getProjectId();
      Long newProjectId = projectId != null ? projectId : sourceLaunchConfig.getProjectId();
      newDescription.setProjectId(newProjectId);

      SystemDisk systemDisk = description.getSystemDisk();
      SystemDisk newSystemDisk =
          systemDisk != null ? systemDisk : sourceLaunchConfig.getSystemDisk();
      newDescription.setSystemDisk(newSystemDisk);

      List<DataDisk> dataDisks = description.getDataDisks();
      List<DataDisk> newDataDisks =
          dataDisks != null
              ? dataDisks
              : Arrays.stream(sourceLaunchConfig.getDataDisks()).collect(Collectors.toList());
      newDescription.setDataDisks(newDataDisks);

      InternetAccessible accessible = description.getInternetAccessible();
      InternetAccessible newInternetAccessible =
          accessible != null ? accessible : sourceLaunchConfig.getInternetAccessible();
      newDescription.setInternetAccessible(newInternetAccessible);

      LoginSettings settings = description.getLoginSettings();
      LoginSettings newSettings = settings;
      if (settings == null && sourceLaunchConfig.getLoginSettings().getKeyIds().length > 0) {
        newSettings = new LoginSettings();
        newSettings.setKeyIds(sourceLaunchConfig.getLoginSettings().getKeyIds());
      }
      newDescription.setLoginSettings(newSettings);

      List<String> securityGroupIds = description.getSecurityGroupIds();
      List<String> newSecurityGroupIds =
          securityGroupIds != null
              ? securityGroupIds
              : Arrays.stream(sourceLaunchConfig.getSecurityGroupIds())
                  .collect(Collectors.toList());
      newDescription.setSecurityGroupIds(newSecurityGroupIds);

      EnhancedService service = description.getEnhancedService();
      EnhancedService newEnhancedService =
          service != null ? service : sourceLaunchConfig.getEnhancedService();
      newDescription.setEnhancedService(newEnhancedService);

      String userData = description.getUserData();
      String newUserData = userData != null ? userData : sourceLaunchConfig.getUserData();
      newDescription.setUserData(newUserData);

      String instanceChargeType = description.getInstanceChargeType();
      String newInstanceChargeType =
          instanceChargeType != null
              ? instanceChargeType
              : sourceLaunchConfig.getInstanceChargeType();
      newDescription.setInstanceChargeType(newInstanceChargeType);

      InstanceMarketOptionsRequest request = description.getInstanceMarketOptionsRequest();
      InstanceMarketOptionsRequest newRequest =
          request != null ? request : sourceLaunchConfig.getInstanceMarketOptions();
      newDescription.setInstanceMarketOptionsRequest(newRequest);

      String policy = description.getInstanceTypesCheckPolicy();
      String newPolicy =
          policy != null ? policy : sourceLaunchConfig.getLastOperationInstanceTypesCheckPolicy();
      newDescription.setInstanceTypesCheckPolicy(newPolicy);
    }

    AutoScalingGroup sourceAutoScalingGroup = sourceServerGroup.getAsg();
    if (sourceAutoScalingGroup != null) {
      Long maxSize = description.getMaxSize();
      Long newMaxSize = maxSize != null ? maxSize : sourceAutoScalingGroup.getMaxSize();
      newDescription.setMaxSize(newMaxSize);

      Long minSize = description.getMinSize();
      Long newMinSize = minSize != null ? minSize : sourceAutoScalingGroup.getMinSize();
      newDescription.setMinSize(newMinSize);

      Long capacity = description.getDesiredCapacity();
      Long newCapacity = capacity != null ? capacity : sourceAutoScalingGroup.getDesiredCapacity();
      newDescription.setDesiredCapacity(newCapacity);

      String vpcId = description.getVpcId();
      String newVpcId = vpcId != null ? vpcId : sourceAutoScalingGroup.getVpcId();
      newDescription.setVpcId(newVpcId);

      if (newDescription.getVpcId() != null) {
        List<String> subnetIds = description.getSubnetIds();
        List<String> newSubnetIds =
            subnetIds != null
                ? subnetIds
                : Arrays.stream(sourceAutoScalingGroup.getSubnetIdSet())
                    .collect(Collectors.toList());
        newDescription.setSubnetIds(newSubnetIds);
      } else {
        List<String> zones = description.getZones();
        List<String> newZones =
            zones != null
                ? zones
                : Arrays.stream(sourceAutoScalingGroup.getZoneSet()).collect(Collectors.toList());
        newDescription.setZones(newZones);
      }

      Long cooldown = description.getDefaultCooldown();
      Long newCooldown = cooldown != null ? cooldown : sourceAutoScalingGroup.getDefaultCooldown();
      newDescription.setDefaultCooldown(newCooldown);

      List<String> policies = description.getTerminationPolicies();
      List<String> newPolices =
          policies != null
              ? policies
              : Arrays.stream(sourceAutoScalingGroup.getTerminationPolicySet())
                  .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(newPolices)) {
        newDescription.setTerminationPolicies(newPolices);
      }

      List<String> lbIds = description.getLoadBalancerIds();
      List<String> newLBIds =
          lbIds != null
              ? lbIds
              : Arrays.stream(sourceAutoScalingGroup.getLoadBalancerIdSet())
                  .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(newLBIds)) {
        newDescription.setLoadBalancerIds(newLBIds);
      }

      List<ForwardLoadBalancer> balancers = description.getForwardLoadBalancers();
      List<ForwardLoadBalancer> newBalancers =
          balancers != null
              ? balancers
              : Arrays.stream(sourceAutoScalingGroup.getForwardLoadBalancerSet())
                  .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(newBalancers)) {
        newDescription.setForwardLoadBalancers(newBalancers);
      }

      String retryPolicy = description.getRetryPolicy();
      String newRetryPolicy =
          retryPolicy != null ? retryPolicy : sourceAutoScalingGroup.getRetryPolicy();
      if (!StringUtils.isEmpty(newRetryPolicy)) {
        newDescription.setRetryPolicy(newRetryPolicy);
      }

      String zoneCheckPolicy = description.getZonesCheckPolicy();
      if (!StringUtils.isEmpty(zoneCheckPolicy)) {
        newDescription.setZonesCheckPolicy(zoneCheckPolicy);
      }
    }

    return newDescription;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
