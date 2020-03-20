package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.LoadBalancerClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerHealthCheck;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerListener;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerRule;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerTarget;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudLoadBalancerProvider;
import com.tencentcloudapi.clb.v20180317.models.Backend;
import com.tencentcloudapi.clb.v20180317.models.HealthCheck;
import com.tencentcloudapi.clb.v20180317.models.Listener;
import com.tencentcloudapi.clb.v20180317.models.ListenerBackend;
import com.tencentcloudapi.clb.v20180317.models.LoadBalancer;
import com.tencentcloudapi.clb.v20180317.models.RuleOutput;
import com.tencentcloudapi.clb.v20180317.models.RuleTargets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertLoadBalancer":
 * {"application":"myapplication", "account":"account-test", "loadBalancerName": "fengCreate5",
 * "region":"ap-guangzhou", "loadBalancerType":"OPEN"
 * ,"listener":[{"listenerName":"listen-create","port":80,"protocol":"TCP",
 * "targets":[{"instanceId":"ins-lq6o6xyc", "port":8080}]}]}} ]' localhost:7004/tencentcloud/ops
 */
@Slf4j
public class UpsertTencentCloudLoadBalancerAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "UPSERT_LOAD_BALANCER";

  private LoadBalancerClient lbClient;
  private UpsertTencentCloudLoadBalancerDescription description;
  @Autowired private TencentCloudLoadBalancerProvider tencentCloudLoadBalancerProvider;

  public UpsertTencentCloudLoadBalancerAtomicOperation(
      LoadBalancerClient lbClient, UpsertTencentCloudLoadBalancerDescription description) {
    this.lbClient = lbClient;
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Map operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing upsert of Tencent Cloud loadBalancer "
                + description.getLoadBalancerName()
                + " in "
                + description.getRegion()
                + "...");
    log.info("UpsertTencentCloudLoadBalancerAtomicOperation operate params = " + description);

    if (StringUtils.isEmpty(description.getLoadBalancerId())) {
      // create new loadBalancer
      insertLoadBalancer(description);
    } else {
      updateLoadBalancer(description);
    }

    Map<String, Map<String, Map<String, String>>> map = new HashMap<>();
    Map<String, Map<String, String>> map1 = new HashMap<>();
    Map<String, String> map2 = new HashMap<>();
    map2.put("name", description.getLoadBalancerName());
    map1.put(description.getRegion(), map2);
    map.put("loadBalancers", map1);
    return map;
  }

  private void insertLoadBalancer(UpsertTencentCloudLoadBalancerDescription description) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start create new loadBalancer " + description.getLoadBalancerName() + " ...");
    String loadBalancerId = lbClient.createLoadBalancer(description).get(0);
    // wait for create loadBalancer success
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      return;
    }

    // query is create success
    List<LoadBalancer> loadBalancer = lbClient.getLoadBalancerById(loadBalancerId);
    if (loadBalancer.isEmpty()) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Create new loadBalancer " + description.getLoadBalancerName() + " failed!");
      return;
    }

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Create new loadBalancer "
                + description.getLoadBalancerName()
                + " success, id is "
                + loadBalancerId
                + ".");

    // set securityGroups to loadBalancer
    if (description.getLoadBalancerType().equals("OPEN")
        && (description.getSecurityGroups().size() > 0)) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Start set securityGroups "
                  + description.getSecurityGroups()
                  + " to loadBalancer "
                  + loadBalancerId
                  + " ...");
      lbClient.setLBSecurityGroups(loadBalancerId, description.getSecurityGroups());
      getTask()
          .updateStatus(BASE_PHASE, "set securityGroups toloadBalancer " + loadBalancerId + " end");
    }

    // create listener
    List<TencentCloudLoadBalancerListener> lbListener = description.getListener();
    if (lbListener.size() > 0) {
      for (TencentCloudLoadBalancerListener listener : lbListener) {
        insertListener(loadBalancerId, listener);
      }
    }

    getTask()
        .updateStatus(
            BASE_PHASE, "Create new loadBalancer " + description.getLoadBalancerName() + " end");
  }

  private void updateLoadBalancer(UpsertTencentCloudLoadBalancerDescription description) {
    getTask()
        .updateStatus(
            BASE_PHASE, "Start update loadBalancer " + description.getLoadBalancerId() + " ...");
    String loadBalancerName = description.getLoadBalancerName();

    List<LoadBalancer> loadBalancerList = lbClient.getLoadBalancerByName(loadBalancerName);
    if (CollectionUtils.isEmpty(loadBalancerList)) {
      getTask().updateStatus(BASE_PHASE, "LoadBalancer " + loadBalancerName + " not exist!");
      return;
    }

    // update securityGroup
    LoadBalancer loadBalancer = loadBalancerList.get(0);
    String loadBalancerId = loadBalancer.getLoadBalancerId();
    if (loadBalancer.getLoadBalancerType().equals("OPEN")) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Start update securityGroups "
                  + description.getSecurityGroups()
                  + " to loadBalancer "
                  + loadBalancerName
                  + " ...");
      lbClient.setLBSecurityGroups(loadBalancerId, description.getSecurityGroups());
      getTask()
          .updateStatus(
              BASE_PHASE, "update securityGroups to loadBalancer " + loadBalancerId + " end");
    }

    List<TencentCloudLoadBalancerListener> newListeners = description.getListener();

    // get all listeners info
    List<Listener> queryListeners = lbClient.getAllLBListener(loadBalancerId);
    List<String> listenerIdList =
        queryListeners.stream().map(Listener::getListenerId).collect(Collectors.toList());

    List<ListenerBackend> queryLBTargetList =
        lbClient.getLBTargetList(loadBalancerId, listenerIdList);

    // delete listener
    for (Listener oldListener : queryListeners) {
      TencentCloudLoadBalancerListener keepListener =
          newListeners.stream()
              .filter(it -> oldListener.getListenerId().equals(it.getListenerId()))
              .findAny()
              .orElse(null);
      if (keepListener == null) {
        getTask()
            .updateStatus(
                BASE_PHASE,
                "Start delete listener "
                    + oldListener.getListenerId()
                    + " in "
                    + loadBalancerId
                    + " ...");
        String ret = lbClient.deleteLBListenerById(loadBalancerId, oldListener.getListenerId());
        getTask()
            .updateStatus(
                BASE_PHASE,
                "Delete listener "
                    + oldListener.getListenerId()
                    + " in "
                    + loadBalancerId
                    + " "
                    + ret
                    + " end");
      }
    }

    // compare listener
    for (TencentCloudLoadBalancerListener inputListener : newListeners) {
      if (!StringUtils.isEmpty(inputListener.getListenerId())) {
        Listener oldListener =
            queryListeners.stream()
                .filter(it -> it.getListenerId().equals(inputListener.getListenerId()))
                .findAny()
                .orElse(null);
        if (oldListener != null) {
          ListenerBackend oldTargets =
              queryLBTargetList.stream()
                  .filter(it -> it.getListenerId().equals(inputListener.getListenerId()))
                  .findAny()
                  .orElse(null);
          updateListener(loadBalancerId, oldListener, inputListener, oldTargets);
        } else {
          getTask()
              .updateStatus(
                  BASE_PHASE, "Input listener " + inputListener.getListenerId() + " not exist!");
        }
      } else {
        insertListener(loadBalancerId, inputListener);
      }
    }

    getTask()
        .updateStatus(
            BASE_PHASE, "Update loadBalancer " + description.getLoadBalancerId() + " end");
  }

  private void insertListener(String loadBalancerId, TencentCloudLoadBalancerListener listener) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start create new "
                + listener.getProtocol()
                + " listener in "
                + loadBalancerId
                + " ...");

    String listenerId = lbClient.createLBListener(loadBalancerId, listener).get(0);

    if (!StringUtils.isEmpty(listenerId)) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Create new "
                  + listener.getProtocol()
                  + " listener in "
                  + loadBalancerId
                  + " success, id is "
                  + listenerId
                  + ".");
      String protocol = listener.getProtocol();
      if (protocol.equals("TCP") || protocol.equals("UDP")) {
        List<TencentCloudLoadBalancerTarget> targets = new ArrayList<>();
        if (!CollectionUtils.isEmpty(targets)) {
          getTask()
              .updateStatus(
                  BASE_PHASE,
                  String.format("Start Register targets to listener %s ...", listenerId));
          String ret = lbClient.registerTarget4Layer(loadBalancerId, listenerId, targets);
          getTask()
              .updateStatus(
                  BASE_PHASE,
                  String.format("Register targets to listener %s %s end.", listenerId, ret));
        }
      } else if (protocol.equals("HTTP") || protocol.equals("HTTPS")) {
        List<TencentCloudLoadBalancerRule> rules = listener.getRules();
        if (!CollectionUtils.isEmpty(rules)) {
          for (TencentCloudLoadBalancerRule rule : rules) {
            insertLBListenerRule(loadBalancerId, listenerId, rule);
          }
        }
      } else {
        getTask().updateStatus(BASE_PHASE, "Create new listener failed!");
      }
      getTask()
          .updateStatus(
              BASE_PHASE, "Create new ${listener.protocol} listener in ${loadBalancerId} end");
    }
  }

  private void updateListener(
      String loadBalancerId,
      Listener oldListener,
      TencentCloudLoadBalancerListener newListener,
      ListenerBackend targets) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start update listener "
                + newListener.getListenerId()
                + " in "
                + loadBalancerId
                + " ...");

    if (!isEqualListener(oldListener, newListener)) {
      modifyListenerAttr(loadBalancerId, newListener);
    }

    RuleOutput[] oldRules = oldListener.getRules();
    List<TencentCloudLoadBalancerRule> newRules = newListener.getRules();

    String protocol = newListener.getProtocol();
    if (protocol.equals("TCP") || protocol.equals("UDP")) {
      // tcp/udp 4 layer, targets
      Backend[] oldTargets = targets.getTargets();
      List<TencentCloudLoadBalancerTarget> newTargets = newListener.getTargets();

      // delete targets
      List<TencentCloudLoadBalancerTarget> delTargets = new ArrayList<>();
      for (Backend oldTarget : oldTargets) {
        TencentCloudLoadBalancerTarget keepTarget =
            newTargets.stream()
                .filter(it -> oldTarget.getInstanceId().equals(it.getInstanceId()))
                .findAny()
                .orElse(null);
        if (keepTarget == null) {
          TencentCloudLoadBalancerTarget delTarget = new TencentCloudLoadBalancerTarget();
          delTarget.setInstanceId(oldTarget.getInstanceId());
          delTarget.setPort(oldTarget.getPort());
          delTarget.setWeight(oldTarget.getWeight());
          delTarget.setType(oldTarget.getType());
          delTargets.add(delTarget);
        }
      }
      if (!delTargets.isEmpty()) {
        getTask()
            .updateStatus(
                BASE_PHASE,
                String.format(
                    "delete listener target in %s.%s ...",
                    loadBalancerId, newListener.getListenerId()));
      }

      // add targets
      List<TencentCloudLoadBalancerTarget> addTargets =
          newTargets.stream()
              .filter(newTarget -> !StringUtils.isEmpty(newTarget.getInstanceId()))
              .collect(Collectors.toList());

      if (!addTargets.isEmpty()) {
        getTask()
            .updateStatus(
                BASE_PHASE,
                String.format(
                    "add listener target to %s.%s ...",
                    loadBalancerId, newListener.getListenerId()));
        lbClient.registerTarget4Layer(loadBalancerId, newListener.getListenerId(), addTargets);
      }
    } else if (protocol.equals("HTTP") || protocol.equals("HTTPS")) {
      // 7 layer, rules, targets
      // delete rule
      for (RuleOutput oldRule : oldRules) {
        TencentCloudLoadBalancerRule keepRule =
            newRules.stream()
                .filter(it -> oldRule.getLocationId().equals(it.getLocationId()))
                .findAny()
                .orElse(null);
        if (keepRule == null) {
          lbClient.deleteLBListenerRule(
              loadBalancerId, newListener.getListenerId(), oldRule.getLocationId());
        }
      }
      // modify rule
      for (TencentCloudLoadBalancerRule newRule : newRules) {
        if (!StringUtils.isEmpty(newRule.getLocationId())) {
          RuleOutput oldRule =
              Arrays.stream(oldRules)
                  .filter(rule -> newRule.getLocationId().equals(rule.getLocationId()))
                  .findFirst()
                  .orElse(null);
          if (oldRule != null) {
            RuleTargets ruleTargets =
                Arrays.stream(targets.getRules())
                    .filter(it -> it.getLocationId().equals(newRule.getLocationId()))
                    .findFirst()
                    .orElse(null);
            updateLBListenerRule(
                loadBalancerId, newListener.getListenerId(), oldRule, newRule, ruleTargets);
          } else {
            getTask()
                .updateStatus(BASE_PHASE, "Input rule " + newRule.getLocationId() + " not exist!");
          }
        } else {
          // create new rule
          lbClient.createLBListenerRule(loadBalancerId, newListener.getListenerId(), newRule);
        }
      }
    }
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "update listener %s in %s end", newListener.getListenerId(), loadBalancerId));
  }

  private boolean isEqualListener(
      Listener oldListener, TencentCloudLoadBalancerListener newListener) {
    HealthCheck oldHealth = oldListener.getHealthCheck();
    TencentCloudLoadBalancerHealthCheck newHealth = newListener.getHealthCheck();

    return isEqualHealthCheck(oldHealth, newHealth);
  }

  private boolean isEqualHealthCheck(
      HealthCheck oldHealth, TencentCloudLoadBalancerHealthCheck newHealth) {
    if ((oldHealth != null) && (newHealth != null)) {
      return Objects.equals(oldHealth.getHealthSwitch(), newHealth.getHealthSwitch())
          && Objects.equals(oldHealth.getTimeOut(), newHealth.getTimeOut())
          && Objects.equals(oldHealth.getIntervalTime(), newHealth.getIntervalTime())
          && Objects.equals(oldHealth.getHealthNum(), newHealth.getHealthNum())
          && Objects.equals(oldHealth.getUnHealthNum(), newHealth.getUnHealthNum())
          && Objects.equals(oldHealth.getHttpCode(), newHealth.getHttpCode())
          && Objects.equals(oldHealth.getHttpCheckPath(), newHealth.getHttpCheckPath())
          && Objects.equals(oldHealth.getHttpCheckDomain(), newHealth.getHttpCheckDomain())
          && Objects.equals(oldHealth.getHttpCheckMethod(), newHealth.getHttpCheckMethod());
    }
    return true;
  }

  private void modifyListenerAttr(
      String loadBalancerId, TencentCloudLoadBalancerListener listener) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start modify listener "
                + listener.getListenerId()
                + " attr in "
                + loadBalancerId
                + " ...");
    String ret = lbClient.modifyListener(loadBalancerId, listener);
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "modify listener %s attr in %s %s end",
                listener.getListenerId(), loadBalancerId, ret));
  }

  private void insertLBListenerRule(
      String loadBalancerId, String listenerId, TencentCloudLoadBalancerRule rule) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "Start create new rule %s %s in %s", rule.getDomain(), rule.getUrl(), listenerId));

    String ret = lbClient.createLBListenerRule(loadBalancerId, listenerId, rule);
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "Create new rule %s %s in %s %s end.",
                rule.getDomain(), rule.getUrl(), listenerId, ret));

    List<TencentCloudLoadBalancerTarget> ruleTargets = rule.getTargets();
    if (!CollectionUtils.isEmpty(ruleTargets)) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              String.format("Start Register targets to listener %s rule ...", listenerId));
      String retVal =
          lbClient.registerTarget7Layer(
              loadBalancerId, listenerId, rule.getDomain(), rule.getUrl(), ruleTargets);
      getTask()
          .updateStatus(
              BASE_PHASE,
              String.format("Register targets to listener %s rule %s end.", listenerId, retVal));
    }
  }

  private void updateLBListenerRule(
      String loadBalancerId,
      String listenerId,
      RuleOutput oldRule,
      TencentCloudLoadBalancerRule newRule,
      RuleTargets targets) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "Start update rule %s in %s.%s ...",
                newRule.getLocationId(), loadBalancerId, listenerId));

    if (!isEqualRule(oldRule, newRule)) {
      modifyRuleAttr(loadBalancerId, listenerId, newRule);
    }

    List<TencentCloudLoadBalancerTarget> newTargets = newRule.getTargets();
    Backend[] oldTargets = targets.getTargets();

    // delete target
    List<TencentCloudLoadBalancerTarget> delTargets = new ArrayList<>();
    for (Backend oldTarget : oldTargets) {
      TencentCloudLoadBalancerTarget keepTarget =
          newTargets.stream()
              .filter(it -> oldTarget.getInstanceId().equals(it.getInstanceId()))
              .findFirst()
              .orElse(null);
      if (keepTarget == null) {
        TencentCloudLoadBalancerTarget delTarget = new TencentCloudLoadBalancerTarget();
        delTarget.setInstanceId(oldTarget.getInstanceId());
        delTarget.setPort(oldTarget.getPort());
        delTarget.setWeight(oldTarget.getWeight());
        delTarget.setType(oldTarget.getType());
        delTargets.add(delTarget);
      }
    }

    if (!delTargets.isEmpty()) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              String.format(
                  "del rule target in %s.%s.%s ...",
                  loadBalancerId, listenerId, newRule.getLocationId()));
      lbClient.deRegisterTarget7Layer(
          loadBalancerId, listenerId, newRule.getLocationId(), delTargets);
    }

    // add target
    List<TencentCloudLoadBalancerTarget> addTargets =
        newTargets.stream()
            .filter(it -> !StringUtils.isEmpty(it.getInstanceId()))
            .collect(Collectors.toList());

    if (!addTargets.isEmpty()) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              String.format(
                  "add rule target to %s.%s.%s ...",
                  loadBalancerId, listenerId, newRule.getLocationId()));
      lbClient.registerTarget7Layer(
          loadBalancerId, listenerId, newRule.getLocationId(), addTargets);
    }

    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "update rule %s in %s.%s end",
                newRule.getLocationId(), loadBalancerId, listenerId));
  }

  private void modifyRuleAttr(
      String loadBalancerId, String listenerId, TencentCloudLoadBalancerRule newRule) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "Start modify rule %s attr in %s.%s ...",
                newRule.getLocationId(), loadBalancerId, listenerId));
    String ret = lbClient.modifyLBListenerRule(loadBalancerId, listenerId, newRule);
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "modify rule %s attr in %s.%s %s end",
                newRule.getLocationId(), loadBalancerId, listenerId, ret));
  }

  private boolean isEqualRule(RuleOutput oldRule, TencentCloudLoadBalancerRule newRule) {
    HealthCheck oldHealth = oldRule.getHealthCheck();
    TencentCloudLoadBalancerHealthCheck newHealth = newRule.getHealthCheck();

    return isEqualHealthCheck(oldHealth, newHealth);
  }
}
