package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.LoadBalancerClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.DeleteTencentCloudLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerListener;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerRule;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerTarget;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudLoadBalancerProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer":
 * {"application":"myapplication", "account":"account-test","loadBalancerId": "lb-kf2lp6cj",
 * "region":"ap-guangzhou", "listener":[{"listenerId":"lbl-d2no6v2c",
 * "targets":[{"instanceId":"ins-lq6o6xyc","port":8080}]}] }} ]' localhost:7004/tencentcloud/ops
 *
 * <p>curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer":
 * {"application":"myapplication", "account":"account-test","loadBalancerId": "lb-kf2lp6cj",
 * "region":"ap-guangzhou",
 * "listener":[{"listenerId":"lbl-hzrdz86n","rules":[{"locationId":"loc-lbcmvnlt","targets":[{"instanceId":"ins-lq6o6xyc","port":8080}]}]}]
 * }} ]' localhost:7004/tencentcloud/ops
 */
@Slf4j
public class DeleteTencentCloudLoadBalancerAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER";

  private DeleteTencentCloudLoadBalancerDescription description;
  private LoadBalancerClient lbClient;
  @Autowired private TencentCloudLoadBalancerProvider tencentCloudLoadBalancerProvider;

  public DeleteTencentCloudLoadBalancerAtomicOperation(
      LoadBalancerClient lbClient, DeleteTencentCloudLoadBalancerDescription description) {
    this.description = description;
    this.lbClient = lbClient;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing delete of Tencent Cloud loadBalancer "
                + description.getLoadBalancerId()
                + " in "
                + description.getRegion()
                + "...");
    log.info("params = " + description);

    List<TencentCloudLoadBalancerListener> lbListeners = description.getListeners();
    String loadBalancerId = description.getLoadBalancerId();
    if (!CollectionUtils.isEmpty(lbListeners)) {
      for (TencentCloudLoadBalancerListener listener : lbListeners) {
        String listenerId = listener.getListenerId();
        List<TencentCloudLoadBalancerRule> rules = listener.getRules();
        List<TencentCloudLoadBalancerTarget> targets = listener.getTargets();

        if (!CollectionUtils.isEmpty(rules)) {
          for (TencentCloudLoadBalancerRule rule : rules) {
            List<TencentCloudLoadBalancerTarget> ruleTargets = rule.getTargets();

            if (CollectionUtils.isEmpty(ruleTargets)) {
              // delete rule's targets
              deleteRuleTargets(loadBalancerId, listenerId, rule.getLocationId(), ruleTargets);
            } else {
              // delete rule
              deleteListenerRule(loadBalancerId, listenerId, rule);
            }
          }
        } else if (!CollectionUtils.isEmpty(targets)) {
          // delete listener's targets
          deleteListenerTargets(loadBalancerId, listenerId, targets);
        } else {
          // delete listener
          deleteListener(loadBalancerId, listenerId);
        }
      }
    } else {
      // no listener, delete loadBalancer
      deleteLoadBalancer(description.getLoadBalancerId());
    }
    return null;
  }

  private void deleteLoadBalancer(String loadBalancerId) {
    getTask().updateStatus(BASE_PHASE, "Start delete loadBalancer " + loadBalancerId + " ...");
    String ret = lbClient.deleteLoadBalancerByIds(new String[] {loadBalancerId});
    getTask()
        .updateStatus(BASE_PHASE, "Delete loadBalancer " + loadBalancerId + " " + ret + " end");
  }

  private void deleteListener(String loadBalancerId, String listenerId) {
    getTask().updateStatus(BASE_PHASE, "Start delete Listener " + listenerId + " ...");
    String ret = lbClient.deleteLBListenerById(loadBalancerId, listenerId);
    getTask().updateStatus(BASE_PHASE, "Delete loadBalancer " + listenerId + " " + ret + " end");
  }

  private void deleteListenerTargets(
      String loadBalancerId, String listenerId, List<TencentCloudLoadBalancerTarget> targets) {
    getTask().updateStatus(BASE_PHASE, "Start delete Listener " + listenerId + " targets ...");
    String ret = lbClient.deRegisterTarget4Layer(loadBalancerId, listenerId, targets);
    getTask()
        .updateStatus(BASE_PHASE, "Delete loadBalancer " + listenerId + " targets " + ret + " end");
  }

  private void deleteListenerRule(
      String loadBalancerId, String listenerId, TencentCloudLoadBalancerRule rule) {
    getTask().updateStatus(BASE_PHASE, "Start delete Listener " + listenerId + " rules ...");
    List<TencentCloudLoadBalancerRule> rules = new ArrayList<>(Arrays.asList(rule));
    String ret = lbClient.deleteLBListenerRules(loadBalancerId, listenerId, rules);
    getTask()
        .updateStatus(BASE_PHASE, "Delete loadBalancer " + listenerId + " rules " + ret + " end");
  }

  private void deleteRuleTargets(
      String loadBalancerId,
      String listenerId,
      String locationId,
      List<TencentCloudLoadBalancerTarget> targets) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start delete Listener " + listenerId + " rule " + locationId + " targets ...");
    String ret = lbClient.deRegisterTarget7Layer(loadBalancerId, listenerId, locationId, targets);
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Delete loadBalancer "
                + listenerId
                + " rule "
                + locationId
                + " targets "
                + ret
                + " end");
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
