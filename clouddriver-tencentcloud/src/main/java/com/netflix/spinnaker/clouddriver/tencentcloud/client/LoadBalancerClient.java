package com.netflix.spinnaker.clouddriver.tencentcloud.client;

import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.exception.TencentCloudOperationException;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerCertificate;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerHealthCheck;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerListener;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerRule;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerTarget;
import com.tencentcloudapi.clb.v20180317.ClbClient;
import com.tencentcloudapi.clb.v20180317.models.CertificateInput;
import com.tencentcloudapi.clb.v20180317.models.CreateListenerRequest;
import com.tencentcloudapi.clb.v20180317.models.CreateListenerResponse;
import com.tencentcloudapi.clb.v20180317.models.CreateLoadBalancerRequest;
import com.tencentcloudapi.clb.v20180317.models.CreateLoadBalancerResponse;
import com.tencentcloudapi.clb.v20180317.models.CreateRuleRequest;
import com.tencentcloudapi.clb.v20180317.models.CreateRuleResponse;
import com.tencentcloudapi.clb.v20180317.models.DeleteListenerRequest;
import com.tencentcloudapi.clb.v20180317.models.DeleteListenerResponse;
import com.tencentcloudapi.clb.v20180317.models.DeleteLoadBalancerRequest;
import com.tencentcloudapi.clb.v20180317.models.DeleteLoadBalancerResponse;
import com.tencentcloudapi.clb.v20180317.models.DeleteRuleRequest;
import com.tencentcloudapi.clb.v20180317.models.DeleteRuleResponse;
import com.tencentcloudapi.clb.v20180317.models.DeregisterTargetsRequest;
import com.tencentcloudapi.clb.v20180317.models.DeregisterTargetsResponse;
import com.tencentcloudapi.clb.v20180317.models.DescribeListenersRequest;
import com.tencentcloudapi.clb.v20180317.models.DescribeListenersResponse;
import com.tencentcloudapi.clb.v20180317.models.DescribeLoadBalancersRequest;
import com.tencentcloudapi.clb.v20180317.models.DescribeLoadBalancersResponse;
import com.tencentcloudapi.clb.v20180317.models.DescribeTargetHealthRequest;
import com.tencentcloudapi.clb.v20180317.models.DescribeTargetHealthResponse;
import com.tencentcloudapi.clb.v20180317.models.DescribeTargetsRequest;
import com.tencentcloudapi.clb.v20180317.models.DescribeTargetsResponse;
import com.tencentcloudapi.clb.v20180317.models.DescribeTaskStatusRequest;
import com.tencentcloudapi.clb.v20180317.models.DescribeTaskStatusResponse;
import com.tencentcloudapi.clb.v20180317.models.HealthCheck;
import com.tencentcloudapi.clb.v20180317.models.Listener;
import com.tencentcloudapi.clb.v20180317.models.ListenerBackend;
import com.tencentcloudapi.clb.v20180317.models.LoadBalancer;
import com.tencentcloudapi.clb.v20180317.models.LoadBalancerHealth;
import com.tencentcloudapi.clb.v20180317.models.ModifyListenerRequest;
import com.tencentcloudapi.clb.v20180317.models.ModifyListenerResponse;
import com.tencentcloudapi.clb.v20180317.models.ModifyRuleRequest;
import com.tencentcloudapi.clb.v20180317.models.ModifyRuleResponse;
import com.tencentcloudapi.clb.v20180317.models.RegisterTargetsRequest;
import com.tencentcloudapi.clb.v20180317.models.RegisterTargetsResponse;
import com.tencentcloudapi.clb.v20180317.models.RuleInput;
import com.tencentcloudapi.clb.v20180317.models.SetLoadBalancerSecurityGroupsRequest;
import com.tencentcloudapi.clb.v20180317.models.Target;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class LoadBalancerClient {

  private static final long DEFAULT_LIMIT = 100;
  private static final int MAX_TRY_COUNT = 20;
  private static final int MAX_RULE_TRY_COUNT = 40;
  private static final int REQ_TRY_INTERVAL = 500;
  private static final int DESCRIBE_TARGET_HEALTH_LIMIT = 30;
  private static final long FORWARD_TYPE = 1L;
  private ClbClient client;

  public LoadBalancerClient(String secretId, String secretKey, String region) {
    this.client = new ClbClient(new Credential(secretId, secretKey), region);
  }

  public List<LoadBalancer> getAllLoadBalancer() {
    try {
      DescribeLoadBalancersRequest request = new DescribeLoadBalancersRequest();
      request.setLimit(DEFAULT_LIMIT);
      request.setForward(FORWARD_TYPE);
      DescribeLoadBalancersResponse resp = client.DescribeLoadBalancers(request);
      List<LoadBalancer> loadBalancerAll =
          new ArrayList<>(Arrays.asList(resp.getLoadBalancerSet()));
      long totalCount = resp.getTotalCount();
      long getCount = DEFAULT_LIMIT;

      while (totalCount > getCount) {
        request.setOffset(getCount);
        DescribeLoadBalancersResponse respMore = client.DescribeLoadBalancers(request);
        loadBalancerAll.addAll(Arrays.asList(respMore.getLoadBalancerSet()));
        getCount += respMore.getLoadBalancerSet().length;
      }

      return loadBalancerAll;
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<LoadBalancer> getLoadBalancerByName(String name) {
    try {
      DescribeLoadBalancersRequest req = new DescribeLoadBalancersRequest();
      req.setLimit(DEFAULT_LIMIT);
      req.setForward(FORWARD_TYPE);
      req.setLoadBalancerName(name);
      DescribeLoadBalancersResponse resp = client.DescribeLoadBalancers(req);
      return Arrays.asList(resp.getLoadBalancerSet());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<LoadBalancer> getLoadBalancerById(String id) {
    try {
      DescribeLoadBalancersRequest req = new DescribeLoadBalancersRequest();
      req.setLoadBalancerIds(new String[] {id});
      DescribeLoadBalancersResponse resp = client.DescribeLoadBalancers(req);
      return Arrays.asList(resp.getLoadBalancerSet());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<String> createLoadBalancer(UpsertTencentCloudLoadBalancerDescription description) {
    try {
      CreateLoadBalancerRequest req = new CreateLoadBalancerRequest();
      req.setLoadBalancerType(description.getLoadBalancerType()); // OPEN：公网属性， INTERNAL：内网属性
      req.setLoadBalancerName(description.getLoadBalancerName());
      req.setForward(FORWARD_TYPE);
      if (!StringUtils.isEmpty(description.getVpcId())) {
        req.setVpcId(description.getVpcId());
      }

      if (!StringUtils.isEmpty(description.getSubnetId())) {
        req.setSubnetId(description.getSubnetId());
      }

      if (description.getProjectId() != null) {
        req.setProjectId(description.getProjectId());
      }

      CreateLoadBalancerResponse resp = client.CreateLoadBalancer(req);
      return Arrays.asList(resp.getLoadBalancerIds());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public String deleteLoadBalancerByIds(String[] loadBalancerIds) {
    try {
      DeleteLoadBalancerRequest req = new DeleteLoadBalancerRequest();
      req.setLoadBalancerIds(loadBalancerIds);
      DeleteLoadBalancerResponse resp = client.DeleteLoadBalancer(req);

      // DescribeTaskStatus is success
      for (int i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }
    return "failed";
  }

  public List<Listener> getAllLBListener(String loadBalancerId) {
    try {
      DescribeListenersRequest req = new DescribeListenersRequest();
      req.setLoadBalancerId(loadBalancerId);
      DescribeListenersResponse resp = client.DescribeListeners(req);
      return Arrays.asList(resp.getListeners());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<Listener> getLBListenerById(String listenerId) {
    try {
      DescribeListenersRequest req = new DescribeListenersRequest();
      req.setLoadBalancerId(listenerId);
      DescribeListenersResponse resp = client.DescribeListeners(req);
      return Arrays.asList(resp.getListeners());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<String> createLBListener(
      String loadBalancerId, TencentCloudLoadBalancerListener listener) {
    try {
      CreateListenerRequest req = new CreateListenerRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setPorts(new Long[] {listener.getPort()});
      req.setProtocol(listener.getProtocol());
      String listenerName = listener.getProtocol() + listener.getPort();
      if (!StringUtils.isEmpty(listener.getListenerName())) {
        listenerName = listener.getListenerName();
      }

      req.setListenerNames(new String[] {listenerName});
      String protocol = listener.getProtocol();
      if (protocol.equals("TCP") || protocol.equals("UDP")) {
        if (listener.getSessionExpireTime() != null) {
          req.setSessionExpireTime(listener.getSessionExpireTime());
        }

        if (!StringUtils.isEmpty(listener.getScheduler())) {
          req.setScheduler(listener.getScheduler());
        }

        if (listener.getHealthCheck() != null) {
          TencentCloudLoadBalancerHealthCheck tencentHealthCheck = listener.getHealthCheck();
          HealthCheck check = new HealthCheck();

          check.setHealthSwitch(tencentHealthCheck.getHealthSwitch());
          check.setTimeOut(tencentHealthCheck.getTimeOut());
          check.setIntervalTime(tencentHealthCheck.getIntervalTime());
          check.setHealthNum(tencentHealthCheck.getHealthNum());
          check.setUnHealthNum(tencentHealthCheck.getUnHealthNum());
          check.setHttpCode(tencentHealthCheck.getHttpCode());
          check.setHttpCheckPath(tencentHealthCheck.getHttpCheckPath());
          check.setHttpCheckDomain(tencentHealthCheck.getHttpCheckDomain());
          check.setHttpCheckMethod(tencentHealthCheck.getHttpCheckMethod());

          req.setHealthCheck(check);
        }
      } else if (protocol.equals("HTTPS")) {
        TencentCloudLoadBalancerCertificate tencentCertificate = listener.getCertificate();
        if (tencentCertificate != null) { // cert
          if (tencentCertificate.getSslMode().equals("UNIDIRECTIONAL")) {
            tencentCertificate.setCertCaId(null); // not need
          }
          CertificateInput input = new CertificateInput();
          input.setSSLMode(tencentCertificate.getSslMode());
          input.setCertId(tencentCertificate.getCertId());
          input.setCertCaId(tencentCertificate.getCertCaId());
          input.setCertName(tencentCertificate.getCertName());
          input.setCertKey(tencentCertificate.getCertKey());
          input.setCertContent(tencentCertificate.getCertContent());
          input.setCertCaName(tencentCertificate.getCertCaName());
          input.setCertCaContent(tencentCertificate.getCertCaContent());
          req.setCertificate(input);
        }
      }

      CreateListenerResponse resp = client.CreateListener(req);

      // DescribeTaskStatus is success
      for (int i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return Arrays.asList(resp.getListenerIds());
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return new ArrayList<>();
  }

  public String deleteLBListenerById(String loadBalancerId, String listenerId) {
    try {
      DeleteListenerRequest req = new DeleteListenerRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      DeleteListenerResponse resp = client.DeleteListener(req);

      // DescribeTaskStatus is success
      for (int i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }

    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return "";
  }

  public String modifyListener(String loadBalancerId, TencentCloudLoadBalancerListener listener) {
    try {
      boolean isModify = false;
      ModifyListenerRequest req = new ModifyListenerRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listener.getListenerId());

      TencentCloudLoadBalancerHealthCheck tencentHealthCheck = listener.getHealthCheck();
      if (tencentHealthCheck != null) {
        HealthCheck check = new HealthCheck();
        check.setHealthSwitch(tencentHealthCheck.getHealthSwitch());
        check.setTimeOut(tencentHealthCheck.getTimeOut());
        check.setIntervalTime(tencentHealthCheck.getIntervalTime());
        check.setHealthNum(tencentHealthCheck.getHealthNum());
        check.setUnHealthNum(tencentHealthCheck.getUnHealthNum());
        check.setHttpCode(tencentHealthCheck.getHttpCode());
        check.setHttpCheckPath(tencentHealthCheck.getHttpCheckPath());
        check.setHttpCheckDomain(tencentHealthCheck.getHttpCheckDomain());
        check.setHttpCheckMethod(tencentHealthCheck.getHttpCheckMethod());
        req.setHealthCheck(check);
        isModify = true;
      }

      if (!isModify) {
        return "no modify";
      }

      ModifyListenerResponse resp = client.ModifyListener(req);

      // DescribeTaskStatus is success
      for (int i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }

    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return "";
  }

  public String registerTarget4Layer(
      String loadBalancerId, String listenerId, List<TencentCloudLoadBalancerTarget> targets) {
    try {
      RegisterTargetsRequest req = new RegisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      List<Target> targetList = new ArrayList<>();
      for (TencentCloudLoadBalancerTarget tencentTarget : targets) {
        Target target = new Target();
        target.setInstanceId(tencentTarget.getInstanceId());
        target.setPort(tencentTarget.getPort());
        target.setType(tencentTarget.getType());
        target.setWeight(tencentTarget.getWeight());
        targetList.add(target);
      }
      req.setTargets(targetList.toArray(new Target[0]));

      RegisterTargetsResponse resp = client.RegisterTargets(req);

      // DescribeTaskStatus task is success
      int maxTryCount = targets.size() * MAX_TRY_COUNT;
      for (int i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return "";
  }

  public String deRegisterTarget4Layer(
      String loadBalancerId, String listenerId, List<TencentCloudLoadBalancerTarget> targets) {
    try {
      DeregisterTargetsRequest req = new DeregisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      List<Target> targetList = new ArrayList<>();
      for (TencentCloudLoadBalancerTarget tencentTarget : targets) {
        Target target = new Target();
        target.setInstanceId(tencentTarget.getInstanceId());
        target.setPort(tencentTarget.getPort());
        target.setType(tencentTarget.getType());
        target.setWeight(tencentTarget.getWeight());
        targetList.add(target);
      }
      req.setTargets(targetList.toArray(new Target[0]));

      DeregisterTargetsResponse resp = client.DeregisterTargets(req);

      // DescribeTaskStatus task is success
      int maxTryCount = targets.size() * MAX_TRY_COUNT;
      for (int i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return "";
  }

  public String createLBListenerRule(
      String loadBalancerId, String listenerId, TencentCloudLoadBalancerRule rule) {
    try {
      CreateRuleRequest req = new CreateRuleRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      RuleInput ruleInput = new RuleInput();
      ruleInput.setDomain(rule.getDomain());
      ruleInput.setUrl(rule.getUrl());
      if (rule.getSessionExpireTime() != null) {
        ruleInput.setSessionExpireTime(rule.getSessionExpireTime());
      }

      if (rule.getScheduler() != null) {
        ruleInput.setScheduler(rule.getScheduler());
      }

      if (rule.getHealthCheck() != null) {
        TencentCloudLoadBalancerHealthCheck tencentHealthCheck = rule.getHealthCheck();
        HealthCheck check = new HealthCheck();
        check.setHealthSwitch(tencentHealthCheck.getHealthSwitch());
        check.setTimeOut(tencentHealthCheck.getTimeOut());
        check.setIntervalTime(tencentHealthCheck.getIntervalTime());
        check.setHealthNum(tencentHealthCheck.getHealthNum());
        check.setUnHealthNum(tencentHealthCheck.getUnHealthNum());
        check.setHttpCode(tencentHealthCheck.getHttpCode());
        check.setHttpCheckPath(tencentHealthCheck.getHttpCheckPath());
        check.setHttpCheckDomain(tencentHealthCheck.getHttpCheckDomain());
        check.setHttpCheckMethod(tencentHealthCheck.getHttpCheckMethod());
        ruleInput.setHealthCheck(check);
      }

      req.setRules(new RuleInput[] {ruleInput});
      CreateRuleResponse resp = client.CreateRule(req);

      // DescribeTaskStatus task is success
      for (int i = 0; i < MAX_RULE_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return "";
  }

  public String deleteLBListenerRules(
      String loadBalancerId, String listenerId, List<TencentCloudLoadBalancerRule> rules) {
    try {
      DeleteRuleRequest req = new DeleteRuleRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);

      req.setLocationIds(
          rules.stream().map(TencentCloudLoadBalancerRule::getLocationId).toArray(String[]::new));

      DeleteRuleResponse resp = client.DeleteRule(req);

      // DescribeTaskStatus task is success
      int maxTryCount = rules.size() * MAX_TRY_COUNT;
      for (int i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return "";
  }

  public String deleteLBListenerRule(String loadBalancerId, String listenerId, String locationId) {
    try {
      DeleteRuleRequest req = new DeleteRuleRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setLocationIds(new String[] {locationId});
      DeleteRuleResponse resp = client.DeleteRule(req);

      // DescribeTaskStatus task is success
      for (int i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return "";
  }

  public String modifyLBListenerRule(
      String loadBalancerId, String listenerId, TencentCloudLoadBalancerRule rule) {
    try {
      boolean isModify = false;
      ModifyRuleRequest req = new ModifyRuleRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setLocationId(rule.getLocationId());

      if (rule.getHealthCheck() != null) {
        HealthCheck check = new HealthCheck();
        TencentCloudLoadBalancerHealthCheck tencentHealthCheck = rule.getHealthCheck();
        check.setHealthSwitch(tencentHealthCheck.getHealthSwitch());
        check.setTimeOut(tencentHealthCheck.getTimeOut());
        check.setIntervalTime(tencentHealthCheck.getIntervalTime());
        check.setHealthNum(tencentHealthCheck.getHealthNum());
        check.setUnHealthNum(tencentHealthCheck.getUnHealthNum());
        check.setHttpCode(tencentHealthCheck.getHttpCode());
        check.setHttpCheckPath(tencentHealthCheck.getHttpCheckPath());
        check.setHttpCheckDomain(tencentHealthCheck.getHttpCheckDomain());
        check.setHttpCheckMethod(tencentHealthCheck.getHttpCheckMethod());

        req.setHealthCheck(check);
        isModify = true;
      }

      if (!isModify) {
        return "no modify";
      }

      ModifyRuleResponse resp = client.ModifyRule(req);

      // DescribeTaskStatus task is success
      for (int i = 0; i < MAX_RULE_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return "";
  }

  public String registerTarget7Layer(
      String loadBalancerId,
      String listenerId,
      String domain,
      String url,
      List<TencentCloudLoadBalancerTarget> targets) {
    try {
      RegisterTargetsRequest req = new RegisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setDomain(domain);
      req.setUrl(url);
      List<Target> targetList = new ArrayList<>();
      for (TencentCloudLoadBalancerTarget tencentTarget : targets) {
        Target target = new Target();
        target.setInstanceId(tencentTarget.getInstanceId());
        target.setPort(tencentTarget.getPort());
        target.setType(tencentTarget.getType());
        target.setWeight(tencentTarget.getWeight());
        targetList.add(target);
      }
      req.setTargets(targetList.toArray(new Target[0]));

      RegisterTargetsResponse resp = client.RegisterTargets(req);

      // DescribeTaskStatus task is success
      int maxTryCount = targets.size();
      for (int i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return "";
  }

  public String registerTarget7Layer(
      String loadBalancerId,
      String listenerId,
      String locationId,
      List<TencentCloudLoadBalancerTarget> targets) {
    try {
      RegisterTargetsRequest req = new RegisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setLocationId(locationId);
      List<Target> targetList = new ArrayList<>();
      for (TencentCloudLoadBalancerTarget tencentTarget : targets) {
        Target target = new Target();
        target.setInstanceId(tencentTarget.getInstanceId());
        target.setPort(tencentTarget.getPort());
        target.setType(tencentTarget.getType());
        target.setWeight(tencentTarget.getWeight());
        targetList.add(target);
      }

      req.setTargets(targetList.toArray(new Target[0]));
      RegisterTargetsResponse resp = client.RegisterTargets(req);

      // DescribeTaskStatus task is success
      int maxTryCount = targets.size();
      for (int i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return "";
  }

  public String deRegisterTarget7Layer(
      String loadBalancerId,
      String listenerId,
      String locationId,
      List<TencentCloudLoadBalancerTarget> targets) {
    try {
      DeregisterTargetsRequest req = new DeregisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setLocationId(locationId);
      List<Target> targetList = new ArrayList<>();
      for (TencentCloudLoadBalancerTarget tencentTarget : targets) {
        Target target = new Target();
        target.setInstanceId(tencentTarget.getInstanceId());
        target.setPort(tencentTarget.getPort());
        target.setType(tencentTarget.getType());
        target.setWeight(tencentTarget.getWeight());
        targetList.add(target);
      }
      req.setTargets(targetList.toArray(new Target[0]));
      DeregisterTargetsResponse resp = client.DeregisterTargets(req);

      // DescribeTaskStatus task is success
      int maxTryCount = targets.size();
      for (int i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return "";
  }

  public List<ListenerBackend> getLBTargets(String loadBalancerId, String listenerId) {
    try {
      DescribeTargetsRequest req = new DescribeTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerIds(new String[] {listenerId});
      DescribeTargetsResponse resp = client.DescribeTargets(req);
      return Arrays.asList(resp.getListeners());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<ListenerBackend> getLBTargetList(String loadBalancerId, List<String> listenerIds) {
    if (CollectionUtils.isEmpty(listenerIds)) {
      return new ArrayList<>();
    }
    try {
      DescribeTargetsRequest req = new DescribeTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerIds(listenerIds.toArray(new String[0]));
      DescribeTargetsResponse resp = client.DescribeTargets(req);
      return Arrays.asList(resp.getListeners());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void setLBSecurityGroups(String loadBalancerId, List<String> securityGroups) {
    try {
      SetLoadBalancerSecurityGroupsRequest req = new SetLoadBalancerSecurityGroupsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setSecurityGroups(securityGroups.toArray(new String[0]));
      client.SetLoadBalancerSecurityGroups(req);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<LoadBalancerHealth> getLBTargetHealth(List<String> loadBalancerIds) {
    List<LoadBalancerHealth> loadBalancerHealths = new ArrayList<>();
    try {
      DescribeTargetHealthRequest req = new DescribeTargetHealthRequest();
      int totalCount = loadBalancerIds.size();
      int reqCount = totalCount;
      int startIndex = 0;
      int endIndex = DESCRIBE_TARGET_HEALTH_LIMIT;
      while (reqCount > 0) {
        if (endIndex > totalCount) {
          endIndex = totalCount;
        }

        List<String> batchIds = loadBalancerIds.subList(startIndex, endIndex - 1);
        if (CollectionUtils.isEmpty(batchIds)) {
          return loadBalancerHealths;
        }
        req.setLoadBalancerIds(batchIds.toArray(new String[0]));
        DescribeTargetHealthResponse resp = client.DescribeTargetHealth(req);
        loadBalancerHealths.addAll(Arrays.asList(resp.getLoadBalancers()));
        reqCount -= DESCRIBE_TARGET_HEALTH_LIMIT;
        startIndex += DESCRIBE_TARGET_HEALTH_LIMIT;
        endIndex = startIndex + DESCRIBE_TARGET_HEALTH_LIMIT;
      }

      return loadBalancerHealths;
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public ClbClient getClient() {
    return client;
  }

  public void setClient(ClbClient client) {
    this.client = client;
  }
}
