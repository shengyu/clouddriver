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

import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.HEALTH_CHECKS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.LoadBalancerClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerTargetHealth;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.TencentCloudInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.tencentcloudapi.clb.v20180317.models.ListenerHealth;
import com.tencentcloudapi.clb.v20180317.models.LoadBalancer;
import com.tencentcloudapi.clb.v20180317.models.LoadBalancerHealth;
import com.tencentcloudapi.clb.v20180317.models.RuleHealth;
import com.tencentcloudapi.clb.v20180317.models.TargetHealth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TencentCloudLoadBalancerInstanceStateCachingAgent
    implements CachingAgent, HealthProvidingCachingAgent, AccountAware {
  private static final String healthId = "tencent-load-balancer-instance-health";

  private final String providerName = TencentCloudInfrastructureProvider.class.getName();
  private TencentCloudNamedAccountCredentials credentials;
  private final String accountName;
  private final String region;
  private final ObjectMapper objectMapper;
  private LoadBalancerClient lbClient;

  public TencentCloudLoadBalancerInstanceStateCachingAgent(
      LoadBalancerClient lbClient,
      TencentCloudNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      String region) {
    this.credentials = credentials;
    this.accountName = credentials.getName();
    this.region = region;
    this.objectMapper = objectMapper;
    this.lbClient = lbClient;
  }

  @Override
  public String getHealthId() {
    return healthId;
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getRegion() + "/" + this.getClass().getSimpleName();
  }

  @Override
  public String getAccountName() {
    return accountName;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Enter loadData in " + getAgentType());

    List<TencentCloudLoadBalancerTargetHealth> targetHealths = getLoadBalancerTargetHealth();
    Collection<String> evictions =
        providerCache.filterIdentifiers(
            HEALTH_CHECKS.ns, Keys.getTargetHealthKey("*", "*", "*", accountName, region));

    List<CacheData> cacheDataList = new ArrayList<>();
    for (TencentCloudLoadBalancerTargetHealth targetHealth : targetHealths) {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("targetHealth", targetHealth);
      String targetHealthKey =
          Keys.getTargetHealthKey(
              targetHealth.getLoadBalancerId(),
              targetHealth.getListenerId(),
              targetHealth.getInstanceId(),
              getAccountName(),
              getRegion());

      String keepKey =
          evictions.stream().filter(it -> it.equals(targetHealthKey)).findFirst().orElse(null);
      if (keepKey != null) {
        evictions.remove(keepKey);
      }
      cacheDataList.add(new DefaultCacheData(targetHealthKey, attributes, new HashMap<>()));
    }
    log.info(
        "Caching "
            + cacheDataList.size()
            + " items evictions "
            + evictions.size()
            + " items in "
            + getAgentType());
    Map<String, Collection<CacheData>> cacheResultsMap = new HashMap<>();
    cacheResultsMap.put(HEALTH_CHECKS.ns, cacheDataList);
    Map<String, Collection<String>> evictionsMap = new HashMap<>();
    evictionsMap.put(HEALTH_CHECKS.ns, evictions);
    return new DefaultCacheResult(cacheResultsMap, evictionsMap);
  }

  private List<TencentCloudLoadBalancerTargetHealth> getLoadBalancerTargetHealth() {
    List<LoadBalancer> loadBalancerSet = lbClient.getAllLoadBalancer();
    List<String> loadBalancerIds =
        loadBalancerSet.stream().map(LoadBalancer::getLoadBalancerId).collect(Collectors.toList());

    int totalLBCount = loadBalancerIds.size();
    log.info("Total loadBalancer Count " + totalLBCount);

    List<LoadBalancerHealth> targetHealthSet = new ArrayList<>();
    if (totalLBCount > 0) {
      targetHealthSet = lbClient.getLBTargetHealth(loadBalancerIds);
    }

    List<TencentCloudLoadBalancerTargetHealth> tencentLBTargetHealths = new ArrayList<>();
    for (LoadBalancerHealth lbHealth : targetHealthSet) {
      String loadBalancerId = lbHealth.getLoadBalancerId();
      ListenerHealth[] listenerHealths = lbHealth.getListeners();
      for (ListenerHealth listenerHealth : listenerHealths) {
        String listenerId = listenerHealth.getListenerId();
        RuleHealth[] ruleHealths = listenerHealth.getRules();
        for (RuleHealth ruleHealth : ruleHealths) {
          String locationId = ruleHealth.getLocationId();
          TargetHealth[] instanceHealths = ruleHealth.getTargets();
          for (TargetHealth instanceHealth : instanceHealths) {
            String targetId = instanceHealth.getTargetId();
            Boolean healthStatus = instanceHealth.getHealthStatus();
            Long port = instanceHealth.getPort();
            TencentCloudLoadBalancerTargetHealth health =
                new TencentCloudLoadBalancerTargetHealth();
            health.setInstanceId(targetId);
            health.setLoadBalancerId(loadBalancerId);
            health.setListenerId(listenerId);
            health.setLocationId(locationId);
            health.setHealthStatus(healthStatus);
            health.setPort(port);
            tencentLBTargetHealths.add(health);
          }
        }
      }
    }

    return tencentLBTargetHealths;
  }

  public TencentCloudNamedAccountCredentials getCredentials() {
    return credentials;
  }

  public void setCredentials(TencentCloudNamedAccountCredentials credentials) {
    this.credentials = credentials;
  }

  public final String getRegion() {
    return region;
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
