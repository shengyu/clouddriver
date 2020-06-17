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

package com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.ON_DEMAND;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.LoadBalancerClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.exception.TencentCloudOperationException;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudBasicResource;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerCertificate;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerHealthCheck;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerListener;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerRule;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerTarget;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.TencentCloudInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.MutableCacheData;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import com.tencentcloudapi.clb.v20180317.models.Backend;
import com.tencentcloudapi.clb.v20180317.models.Listener;
import com.tencentcloudapi.clb.v20180317.models.ListenerBackend;
import com.tencentcloudapi.clb.v20180317.models.LoadBalancer;
import com.tencentcloudapi.clb.v20180317.models.RuleOutput;
import com.tencentcloudapi.clb.v20180317.models.RuleTargets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
public class TencentCloudLoadBalancerCachingAgent
    implements OnDemandAgent, CachingAgent, AccountAware {

  private final String accountName;
  private final String region;
  private final ObjectMapper objectMapper;
  private final String providerName = TencentCloudInfrastructureProvider.class.getName();
  private TencentCloudNamedAccountCredentials credentials;
  private final OnDemandMetricsSupport metricsSupport;
  private final Namer<TencentCloudBasicResource> namer;
  private String onDemandAgentType = getAgentType() + "-OnDemand";
  private final List<AgentDataType> providedDataTypes =
      new ArrayList<>(
          Arrays.asList(
              AUTHORITATIVE.forType(APPLICATIONS.ns),
              AUTHORITATIVE.forType(LOAD_BALANCERS.ns),
              INFORMATIVE.forType(INSTANCES.ns)));

  private LoadBalancerClient lbClient;

  public TencentCloudLoadBalancerCachingAgent(
      LoadBalancerClient lbClient,
      TencentCloudNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    this.credentials = credentials;
    this.accountName = credentials.getName();
    this.region = region;
    this.objectMapper = objectMapper;
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry, this, TencentCloudProvider.ID + ":" + OnDemandType.LoadBalancer);
    this.namer =
        NamerRegistry.lookup()
            .withProvider(TencentCloudProvider.ID)
            .withAccount(credentials.getName())
            .withResource(TencentCloudBasicResource.class);
    this.lbClient = lbClient;
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getRegion() + "/" + this.getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  @Override
  public String getAccountName() {
    return accountName;
  }

  public List<TencentCloudLoadBalancer> loadLoadBalancerData(String loadBalancerName) {

    List<LoadBalancer> loadBalancerList;
    if (!StringUtils.isEmpty(loadBalancerName)) {
      loadBalancerList = lbClient.getLoadBalancerByName(loadBalancerName);
    } else {
      loadBalancerList = lbClient.getAllLoadBalancer();
    }
    List<TencentCloudLoadBalancer> result = new ArrayList<>();
    for (LoadBalancer loadBalancer : loadBalancerList) {
      TencentCloudLoadBalancer tencentLoadBalancer = new TencentCloudLoadBalancer();
      tencentLoadBalancer.setRegion(getRegion());
      tencentLoadBalancer.setAccountName(getAccountName());
      tencentLoadBalancer.setName(loadBalancer.getLoadBalancerName());
      tencentLoadBalancer.setLoadBalancerName(loadBalancer.getLoadBalancerName());
      tencentLoadBalancer.setId(loadBalancer.getLoadBalancerId());
      tencentLoadBalancer.setLoadBalancerId(loadBalancer.getLoadBalancerId());
      tencentLoadBalancer.setLoadBalancerType(loadBalancer.getLoadBalancerType());
      tencentLoadBalancer.setVpcId(loadBalancer.getVpcId());
      tencentLoadBalancer.setSubnetId(loadBalancer.getSubnetId());
      tencentLoadBalancer.setCreateTime(loadBalancer.getCreateTime());

      tencentLoadBalancer.setLoadBalancerVips(
          Arrays.stream(loadBalancer.getLoadBalancerVips()).collect(Collectors.toList()));

      tencentLoadBalancer.setSecurityGroups(
          Arrays.stream(loadBalancer.getSecureGroups()).collect(Collectors.toList()));

      List<Listener> queryListeners = lbClient.getAllLBListener(tencentLoadBalancer.getId());
      List<String> listenerIdList =
          queryListeners.stream().map(Listener::getListenerId).collect(Collectors.toList());
      List<ListenerBackend> lbTargetList =
          lbClient.getLBTargetList(tencentLoadBalancer.getId(), listenerIdList);

      List<TencentCloudLoadBalancerListener> listenerList = new ArrayList<>();
      for (Listener listener : queryListeners) {
        TencentCloudLoadBalancerListener tencentListener = new TencentCloudLoadBalancerListener();
        tencentListener.setListenerId(listener.getListenerId());
        tencentListener.setProtocol(listener.getProtocol());
        tencentListener.setPort(listener.getPort());
        tencentListener.setScheduler(listener.getScheduler());
        tencentListener.setSessionExpireTime(listener.getSessionExpireTime());
        tencentListener.setSniSwitch(listener.getSniSwitch());
        tencentListener.setListenerName(listener.getListenerName());
        if (listener.getCertificate() != null) { // listener.certificate
          tencentListener.setCertificate(new TencentCloudLoadBalancerCertificate());
          tencentListener.getCertificate().setSslMode(listener.getCertificate().getSSLMode());
          tencentListener.getCertificate().setCertId(listener.getCertificate().getCertId());
          tencentListener.getCertificate().setCertCaId(listener.getCertificate().getCertCaId());
        }

        if (listener.getHealthCheck() != null) { // listener healtch check
          tencentListener.setHealthCheck(new TencentCloudLoadBalancerHealthCheck());
          tencentListener
              .getHealthCheck()
              .setHealthSwitch(listener.getHealthCheck().getHealthSwitch());
          tencentListener.getHealthCheck().setTimeOut(listener.getHealthCheck().getTimeOut());
          tencentListener
              .getHealthCheck()
              .setIntervalTime(listener.getHealthCheck().getIntervalTime());
          tencentListener.getHealthCheck().setHealthNum(listener.getHealthCheck().getHealthNum());
          tencentListener
              .getHealthCheck()
              .setUnHealthNum(listener.getHealthCheck().getUnHealthNum());
          tencentListener.getHealthCheck().setHttpCode(listener.getHealthCheck().getHttpCode());
          tencentListener
              .getHealthCheck()
              .setHttpCheckPath(listener.getHealthCheck().getHttpCheckPath());
          tencentListener
              .getHealthCheck()
              .setHttpCheckDomain(listener.getHealthCheck().getHttpCheckDomain());
          tencentListener
              .getHealthCheck()
              .setHttpCheckMethod(listener.getHealthCheck().getHttpCheckMethod());
        }

        List<ListenerBackend> lbTargets =
            lbTargetList.stream()
                .filter(it -> it.getListenerId().equals(tencentListener.getListenerId()))
                .collect(Collectors.toList());
        List<TencentCloudLoadBalancerTarget> targetList = new ArrayList<>();
        for (ListenerBackend listenBackend : lbTargets) {
          if (listenBackend.getTargets() != null) {
            for (Backend targetEntry : listenBackend.getTargets()) {
              if (targetEntry != null) {
                TencentCloudLoadBalancerTarget target = new TencentCloudLoadBalancerTarget();
                target.setInstanceId(targetEntry.getInstanceId());
                target.setPort(targetEntry.getPort());
                target.setWeight(targetEntry.getWeight());
                target.setType(targetEntry.getType());
                targetList.add(target);
              }
            }
          }
        }

        tencentListener.setTargets(targetList);
        List<TencentCloudLoadBalancerRule> ruleList = new ArrayList<>();
        if (listener.getRules() != null) {
          for (RuleOutput ruleOutput : listener.getRules()) {
            TencentCloudLoadBalancerRule rule = new TencentCloudLoadBalancerRule();
            rule.setLocationId(ruleOutput.getLocationId());
            rule.setDomain(ruleOutput.getDomain());
            rule.setUrl(ruleOutput.getUrl());
            if (ruleOutput.getCertificate() != null) { // rule.certificate
              rule.setCertificate(new TencentCloudLoadBalancerCertificate());
              rule.getCertificate().setSslMode(ruleOutput.getCertificate().getSSLMode());
              rule.getCertificate().setCertId(ruleOutput.getCertificate().getCertId());
              rule.getCertificate().setCertCaId(ruleOutput.getCertificate().getCertCaId());
            }

            if (ruleOutput.getHealthCheck() != null) { // rule healthCheck
              rule.setHealthCheck(new TencentCloudLoadBalancerHealthCheck());
              rule.getHealthCheck().setHealthSwitch(ruleOutput.getHealthCheck().getHealthSwitch());
              rule.getHealthCheck().setTimeOut(ruleOutput.getHealthCheck().getTimeOut());
              rule.getHealthCheck().setIntervalTime(ruleOutput.getHealthCheck().getIntervalTime());
              rule.getHealthCheck().setHealthNum(ruleOutput.getHealthCheck().getHealthNum());
              rule.getHealthCheck().setUnHealthNum(ruleOutput.getHealthCheck().getUnHealthNum());
              rule.getHealthCheck().setHttpCode(ruleOutput.getHealthCheck().getHttpCode());
              rule.getHealthCheck()
                  .setHttpCheckPath(ruleOutput.getHealthCheck().getHttpCheckPath());
              rule.getHealthCheck()
                  .setHttpCheckDomain(ruleOutput.getHealthCheck().getHttpCheckDomain());
              rule.getHealthCheck()
                  .setHttpCheckMethod(ruleOutput.getHealthCheck().getHttpCheckMethod());
            }

            List<TencentCloudLoadBalancerTarget> ruleTargetList = new ArrayList<>();
            for (ListenerBackend listenerBackend : lbTargets) {
              for (RuleTargets ruleTarget : listenerBackend.getRules()) {
                if (ruleTarget.getLocationId().equals(rule.getLocationId())) {
                  for (Backend ruleTargetEntry : ruleTarget.getTargets()) {
                    TencentCloudLoadBalancerTarget target = new TencentCloudLoadBalancerTarget();
                    target.setInstanceId(ruleTargetEntry.getInstanceId());
                    target.setPort(ruleTargetEntry.getPort());
                    target.setWeight(ruleTargetEntry.getWeight());
                    target.setType(ruleTargetEntry.getType());
                    ruleTargetList.add(target);
                  }
                }
              }
            }
            rule.setTargets(ruleTargetList);
            ruleList.add(rule);
          }
        }
        tencentListener.setRules(ruleList);
        listenerList.add(tencentListener);
      }

      tencentLoadBalancer.setListeners(listenerList);
      result.add(tencentLoadBalancer);
    }

    return result;
  }

  public List<TencentCloudLoadBalancer> loadLoadBalancerData() {
    return loadLoadBalancerData(null);
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.LoadBalancer) && cloudProvider.equals(TencentCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    log.info("Enter handle, data = " + data);
    String accountNameData = (String) data.get("account");
    String regionData = (String) data.get("region");
    String loadBalancerName = (String) data.get("loadBalancerName");
    if (StringUtils.isEmpty(loadBalancerName)
        || StringUtils.isEmpty(regionData)
        || StringUtils.isEmpty(accountNameData)
        || !accountName.equals(accountNameData)
        || !region.equals(regionData)) {
      return null;
    }

    TencentCloudLoadBalancer loadBalancer =
        metricsSupport.readData(
            () -> {
              try {
                List<TencentCloudLoadBalancer> lbList = loadLoadBalancerData(loadBalancerName);
                if (CollectionUtils.isEmpty(lbList)) {
                  return null;
                } else {
                  return lbList.get(0);
                }
              } catch (TencentCloudOperationException e) {
                return null;
              }
            });

    if (loadBalancer == null) {
      log.info("Can not find loadBalancer " + loadBalancerName);
      return null;
    }

    CacheResult cacheResult =
        metricsSupport.transformData(
            () -> buildCacheResult(Arrays.asList(loadBalancer), null, null));

    String cacheResultAsJson;
    try {
      cacheResultAsJson = objectMapper.writeValueAsString(cacheResult.getCacheResults());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("CacheResult deserialization failed.");
    }
    String loadBalancerKey = Keys.getLoadBalancerKey(loadBalancerName, accountName, region);

    List<CacheData> cacheDataList = new ArrayList<>();
    for (Collection<CacheData> dataCollection : cacheResult.getCacheResults().values()) {
      cacheDataList.addAll(dataCollection);
    }
    if (cacheDataList.isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously
      // existed).
      providerCache.evictDeletedItems(ON_DEMAND.ns, Arrays.asList(loadBalancerKey));
    } else {
      String finalCacheResultAsJson = cacheResultAsJson;
      metricsSupport.onDemandStore(
          () -> {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("cacheTime", System.currentTimeMillis());
            attributes.put("cacheResults", finalCacheResultAsJson);
            Map<String, Collection<String>> relationships = new HashMap<>();
            DefaultCacheData cacheData =
                new DefaultCacheData(loadBalancerKey, 10 * 60, attributes, relationships);
            providerCache.putCacheData(ON_DEMAND.ns, cacheData);
            return null;
          });
    }

    Map<String, Collection<String>> evictions = new HashMap<>();
    return new OnDemandResult(getOnDemandAgentType(), cacheResult, evictions);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Enter LoadBalancerCachingAgent loadData ");

    List<TencentCloudLoadBalancer> loadBalancerSet = loadLoadBalancerData();
    log.info("Total loadBalancer Number = " + loadBalancerSet.size() + " in " + getAgentType());
    Collection<CacheData> toEvictOnDemandCacheData = new ArrayList<>();
    List<CacheData> toKeepOnDemandCacheData = new ArrayList<>();

    long start = System.currentTimeMillis();
    Set<String> loadBalancerKeys =
        loadBalancerSet.stream()
            .map(it -> Keys.getLoadBalancerKey(it.getId(), credentials.getName(), region))
            .collect(Collectors.toSet());

    List<String> pendingOnDemandRequestKeys =
        providerCache
            .filterIdentifiers(
                ON_DEMAND.ns, Keys.getLoadBalancerKey("*", credentials.getName(), region))
            .stream()
            .filter(loadBalancerKeys::contains)
            .collect(Collectors.toList());

    Collection<CacheData> pendingOnDemandRequestsForwardLoadBalancer =
        providerCache.getAll(ON_DEMAND.ns, pendingOnDemandRequestKeys);

    for (CacheData cacheData : pendingOnDemandRequestsForwardLoadBalancer) {
      if ((long) cacheData.getAttributes().get("cacheTime") < start
          && (long) cacheData.getAttributes().get("processedCount") > 0) {
        toEvictOnDemandCacheData.add(cacheData);
      } else {
        toKeepOnDemandCacheData.add(cacheData);
      }
    }

    CacheResult result =
        buildCacheResult(loadBalancerSet, toKeepOnDemandCacheData, toEvictOnDemandCacheData);
    if (result.getCacheResults().containsKey(ON_DEMAND.ns)) {
      for (CacheData cacheData : result.getCacheResults().get(ON_DEMAND.ns)) {
        cacheData.getAttributes().put("processedTime", System.currentTimeMillis());
        Integer processedCount = (Integer) cacheData.getAttributes().get("processedCount");
        cacheData
            .getAttributes()
            .put("processedCount", processedCount != null ? processedCount + 1 : 1);
      }
    }
    return result;
  }

  private CacheResult buildCacheResult(
      Collection<TencentCloudLoadBalancer> loadBalancerSet,
      Collection<CacheData> toKeepOnDemandCacheData,
      Collection<CacheData> toEvictOnDemandCacheData) {
    log.info("Start build cache for " + getAgentType());

    Map<String, Collection<CacheData>> cacheResultsMap = new HashMap<>();
    Map<String, Collection<String>> evictions = new HashMap<>();
    if (!CollectionUtils.isEmpty(toEvictOnDemandCacheData)) {
      List<String> idList =
          toEvictOnDemandCacheData.stream().map(CacheData::getId).collect(Collectors.toList());
      evictions.put(ON_DEMAND.ns, idList);
    }

    Map<String, Map<String, CacheData>> namespaceCache = new HashMap<>();
    namespaceCache.put(APPLICATIONS.ns, new HashMap<>());
    namespaceCache.put(LOAD_BALANCERS.ns, new HashMap<>());

    for (TencentCloudLoadBalancer tlb : loadBalancerSet) {
      Moniker moniker = namer.deriveMoniker(tlb);
      String applicationName = moniker.getApp();
      if (applicationName == null) {
        continue;
      }

      String loadBalancerKey = Keys.getLoadBalancerKey(tlb.getId(), getAccountName(), getRegion());
      String appKey = Keys.getApplicationKey(applicationName);
      // application
      Map<String, CacheData> applications = namespaceCache.get(APPLICATIONS.ns);
      if (!applications.containsKey(appKey)) {
        CacheData cacheData = new MutableCacheData(appKey);
        applications.put(appKey, cacheData);
      }
      applications.get(appKey).getAttributes().put("name", applicationName);
      if (applications.get(appKey).getRelationships().containsKey(LOAD_BALANCERS.ns)) {
        applications.get(appKey).getRelationships().get(LOAD_BALANCERS.ns).add(loadBalancerKey);
      } else {
        List<String> lbKeys = new ArrayList<>();
        lbKeys.add(loadBalancerKey);
        applications.get(appKey).getRelationships().put(LOAD_BALANCERS.ns, lbKeys);
      }

      CacheData lbData = new MutableCacheData(loadBalancerKey);
      lbData.getAttributes().put("application", applicationName);
      lbData.getAttributes().put("name", tlb.getName());
      lbData.getAttributes().put("region", tlb.getRegion());
      lbData.getAttributes().put("id", tlb.getId());
      lbData.getAttributes().put("loadBalancerId", tlb.getLoadBalancerId());
      lbData.getAttributes().put("accountName", tlb.getAccountName());
      lbData.getAttributes().put("vpcId", tlb.getVpcId());
      lbData.getAttributes().put("subnetId", tlb.getSubnetId());
      lbData.getAttributes().put("loadBalancerType", tlb.getLoadBalancerType());
      lbData.getAttributes().put("createTime", tlb.getCreateTime());
      lbData.getAttributes().put("loadBalancerVips", new ArrayList<>(tlb.getLoadBalancerVips()));
      lbData.getAttributes().put("securityGroups", new ArrayList<>(tlb.getSecurityGroups()));
      lbData.getAttributes().put("listeners", new ArrayList<>(tlb.getListeners()));
      lbData.getRelationships().put(APPLICATIONS.ns, Arrays.asList(appKey));
      namespaceCache.get(LOAD_BALANCERS.ns).put(loadBalancerKey, lbData);
    }

    namespaceCache.forEach(
        (namespace, cacheDataMap) -> cacheResultsMap.put(namespace, cacheDataMap.values()));

    if (!CollectionUtils.isEmpty(toKeepOnDemandCacheData)) {
      cacheResultsMap.put(ON_DEMAND.ns, toKeepOnDemandCacheData);
    }

    return new DefaultCacheResult(cacheResultsMap, evictions);
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return new ArrayList<>();
  }

  public final String getRegion() {
    return region;
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public TencentCloudNamedAccountCredentials getCredentials() {
    return credentials;
  }

  public void setCredentials(TencentCloudNamedAccountCredentials credentials) {
    this.credentials = credentials;
  }

  public final OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  public final Namer<TencentCloudBasicResource> getNamer() {
    return namer;
  }

  public String getOnDemandAgentType() {
    return onDemandAgentType;
  }

  public void setOnDemandAgentType(String onDemandAgentType) {
    this.onDemandAgentType = onDemandAgentType;
  }

  public final List<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }

  private static <Value extends List<TencentCloudLoadBalancerTarget>> Value setTargets(
      TencentCloudLoadBalancerListener propOwner, Value targets) {
    propOwner.setTargets(targets);
    return targets;
  }
}
