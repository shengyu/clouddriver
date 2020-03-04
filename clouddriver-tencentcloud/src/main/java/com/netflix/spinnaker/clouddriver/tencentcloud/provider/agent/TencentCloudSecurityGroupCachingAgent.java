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
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.ON_DEMAND;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.SECURITY_GROUPS;

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
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.VirtualPrivateCloudClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudSecurityGroupRule;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.TencentCloudInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroup;
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroupPolicySet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
public class TencentCloudSecurityGroupCachingAgent
    implements CachingAgent, OnDemandAgent, AccountAware {

  private final ObjectMapper objectMapper;
  private final String region;
  private final String accountName;
  private final TencentCloudNamedAccountCredentials credentials;
  private final String providerName = TencentCloudInfrastructureProvider.class.getName();
  private final Registry registry;
  private final OnDemandMetricsSupport metricsSupport;
  private String onDemandAgentType = getAgentType() + "-OnDemand";
  private final List<AgentDataType> providedDataTypes =
      new ArrayList<>(Arrays.asList(AUTHORITATIVE.forType(SECURITY_GROUPS.ns)));
  private VirtualPrivateCloudClient vpcClient;

  public TencentCloudSecurityGroupCachingAgent(
      VirtualPrivateCloudClient vpcClient,
      TencentCloudNamedAccountCredentials creds,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    this.accountName = creds.getName();
    this.credentials = creds;
    this.region = region;
    this.objectMapper = objectMapper;
    this.registry = registry;
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry, this, TencentCloudProvider.ID + ":" + OnDemandType.SecurityGroup);
    this.vpcClient = vpcClient;
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

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.SecurityGroup) && cloudProvider.equals(TencentCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    log.info("Enter TencentCloudSecurityGroupCachingAgent handle, params = " + data);

    String accountNameData = (String) data.get("account");
    String regionData = (String) data.get("region");
    String securityGroupName = (String) data.get("securityGroupName");
    Boolean evict = (Boolean) data.get("evict");

    if (StringUtils.isEmpty(securityGroupName)
        || !accountName.equals(accountNameData)
        || !region.equals(regionData)) {
      log.info("TencentCloudSecurityGroupCachingAgent: input params error!");
      return null;
    }

    if (evict != null && evict) {
      return evictSecurityGroup(providerCache, securityGroupName);
    } else {
      return updateSecurityGroup(providerCache, securityGroupName);
    }
  }

  private OnDemandResult evictSecurityGroup(ProviderCache providerCache, String securityGroupName) {
    TencentCloudSecurityGroupDescription evictedSecurityGroup =
        new TencentCloudSecurityGroupDescription();
    evictedSecurityGroup.setSecurityGroupId("*");
    evictedSecurityGroup.setSecurityGroupName(securityGroupName);
    evictedSecurityGroup.setSecurityGroupDesc("unknown");
    evictedSecurityGroup.setLastReadTime(System.currentTimeMillis());
    CacheResult cacheResult =
        metricsSupport.transformData(
            () -> buildCacheResult(providerCache, null, 0, null, evictedSecurityGroup));

    Map<String, Collection<String>> evictions = new HashMap<>();
    String securityGroupKey =
        Keys.getSecurityGroupKey("*", securityGroupName, getAccountName(), getRegion());
    Collection<String> result =
        providerCache.filterIdentifiers(SECURITY_GROUPS.ns, securityGroupKey);
    evictions.put(SECURITY_GROUPS.ns, result);

    return new OnDemandAgent.OnDemandResult(getAgentType(), cacheResult, evictions);
  }

  @Nullable
  private OnDemandResult updateSecurityGroup(
      ProviderCache providerCache, String securityGroupName) {
    TencentCloudSecurityGroupDescription updatedSecurityGroup =
        metricsSupport.readData(() -> loadSecurityGroupById(securityGroupName));
    if (updatedSecurityGroup == null) {
      log.info(
          "TencentCloudSecurityGroupCachingAgent: Can not find securityGroup "
              + securityGroupName
              + " in "
              + getRegion());
      return null;
    }

    CacheResult cacheResult =
        metricsSupport.transformData(
            () -> buildCacheResult(providerCache, null, 0, updatedSecurityGroup, null));

    return new OnDemandResult(getAgentType(), cacheResult, null);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Enter TencentCloudSecurityGroupCachingAgent loadData in " + getAgentType());
    long currentTime = System.currentTimeMillis();
    List<TencentCloudSecurityGroupDescription> securityGroupDescSet = loadSecurityGroupAll();

    log.info(
        "Total SecurityGroup Number = " + securityGroupDescSet.size() + " in " + getAgentType());
    return buildCacheResult(providerCache, securityGroupDescSet, currentTime, null, null);
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return new ArrayList<>();
  }

  private List<TencentCloudSecurityGroupDescription> loadSecurityGroupAll() {
    List<SecurityGroup> securityGroupSet = vpcClient.getSecurityGroupsAll();

    List<TencentCloudSecurityGroupDescription> securityGroupDescriptionList = new ArrayList<>();

    for (SecurityGroup securityGroup : securityGroupSet) {
      TencentCloudSecurityGroupDescription securityGroupDesc =
          new TencentCloudSecurityGroupDescription();
      securityGroupDesc.setSecurityGroupId(securityGroup.getSecurityGroupId());
      securityGroupDesc.setSecurityGroupName(securityGroup.getSecurityGroupName());
      securityGroupDesc.setSecurityGroupDesc(securityGroup.getSecurityGroupDesc());

      SecurityGroupPolicySet securityGroupRules =
          vpcClient.getSecurityGroupPolicies(securityGroupDesc.getSecurityGroupId());

      List<TencentCloudSecurityGroupRule> inRuleList =
          Arrays.stream(securityGroupRules.getIngress())
              .map(
                  ingress ->
                      TencentCloudSecurityGroupRule.builder()
                          .index(ingress.getPolicyIndex())
                          .protocol(ingress.getProtocol())
                          .port(ingress.getPort())
                          .cidrBlock(ingress.getCidrBlock())
                          .action(ingress.getAction())
                          .build())
              .collect(Collectors.toList());
      securityGroupDesc.setInRules(inRuleList);

      List<TencentCloudSecurityGroupRule> outRuleList =
          Arrays.stream(securityGroupRules.getEgress())
              .map(
                  egress ->
                      TencentCloudSecurityGroupRule.builder()
                          .index(egress.getPolicyIndex())
                          .protocol(egress.getProtocol())
                          .port(egress.getPort())
                          .cidrBlock(egress.getCidrBlock())
                          .action(egress.getAction())
                          .build())
              .collect(Collectors.toList());
      securityGroupDesc.setOutRules(outRuleList);

      securityGroupDescriptionList.add(securityGroupDesc);
    }
    return securityGroupDescriptionList;
  }

  private TencentCloudSecurityGroupDescription loadSecurityGroupById(String securityGroupId) {
    SecurityGroup securityGroup = null;
    List<SecurityGroup> securityGroups = vpcClient.getSecurityGroupByName(securityGroupId);
    if (!CollectionUtils.isEmpty(securityGroups)) {
      securityGroup = securityGroups.get(0);
    }
    long currentTime = System.currentTimeMillis();
    TencentCloudSecurityGroupDescription securityGroupDesc = null;
    if (securityGroup != null) {
      securityGroupDesc = new TencentCloudSecurityGroupDescription();
      securityGroupDesc.setSecurityGroupId(securityGroup.getSecurityGroupId());
      securityGroupDesc.setSecurityGroupDesc(securityGroup.getSecurityGroupDesc());
      securityGroupDesc.setSecurityGroupName(securityGroup.getSecurityGroupName());
      securityGroupDesc.setLastReadTime(currentTime);

      SecurityGroupPolicySet securityGroupRules =
          vpcClient.getSecurityGroupPolicies(securityGroupDesc.getSecurityGroupId());

      List<TencentCloudSecurityGroupRule> inRuleList =
          Arrays.stream(securityGroupRules.getIngress())
              .map(
                  ingress ->
                      TencentCloudSecurityGroupRule.builder()
                          .index(ingress.getPolicyIndex())
                          .protocol(ingress.getProtocol())
                          .port(ingress.getPort())
                          .cidrBlock(ingress.getCidrBlock())
                          .action(ingress.getAction())
                          .build())
              .collect(Collectors.toList());
      securityGroupDesc.setInRules(inRuleList);

      List<TencentCloudSecurityGroupRule> outRuleList =
          Arrays.stream(securityGroupRules.getEgress())
              .map(
                  egress ->
                      TencentCloudSecurityGroupRule.builder()
                          .index(egress.getPolicyIndex())
                          .protocol(egress.getProtocol())
                          .port(egress.getPort())
                          .cidrBlock(egress.getCidrBlock())
                          .action(egress.getAction())
                          .build())
              .collect(Collectors.toList());
      securityGroupDesc.setOutRules(outRuleList);
    }

    return securityGroupDesc;
  }

  private CacheResult buildCacheResult(
      ProviderCache providerCache,
      Collection<TencentCloudSecurityGroupDescription> securityGroups,
      long lastReadTime,
      TencentCloudSecurityGroupDescription updatedSecurityGroup,
      TencentCloudSecurityGroupDescription evictedSecurityGroup) {
    if (securityGroups != null) {
      List<CacheData> data = new ArrayList<>();
      Collection<String> identifiers =
          providerCache.filterIdentifiers(
              ON_DEMAND.ns, Keys.getSecurityGroupKey("*", "*", accountName, region));
      Collection<CacheData> onDemandCacheResults =
          providerCache.getAll(ON_DEMAND.ns, identifiers, RelationshipCacheFilter.none());

      // Add any outdated OnDemand cache entries to the evicted list
      List<String> evictions = new ArrayList<>();
      Map<String, CacheData> usableOnDemandCacheDatas = new HashMap<>();
      for (CacheData cacheData : onDemandCacheResults) {
        if ((long) cacheData.getAttributes().get("cachedTime") < lastReadTime) {
          evictions.add(cacheData.getId());
        } else {
          usableOnDemandCacheDatas.put(cacheData.getId(), cacheData);
        }
      }

      for (TencentCloudSecurityGroupDescription item : securityGroups) {
        TencentCloudSecurityGroupDescription securityGroup = item;
        String sgKey =
            Keys.getSecurityGroupKey(
                securityGroup.getSecurityGroupId(),
                securityGroup.getSecurityGroupName(),
                getAccountName(),
                getRegion());
        CacheData onDemandSG = usableOnDemandCacheDatas.get(sgKey);
        if (onDemandSG != null) {
          if ((long) onDemandSG.getAttributes().get("cachedTime")
              > securityGroup.getLastReadTime()) {
            // Found a security group resource that has been updated since last time was read from
            // Azure cloud
            try {
              securityGroup =
                  objectMapper.readValue(
                      (String) onDemandSG.getAttributes().get("securityGroup"),
                      TencentCloudSecurityGroupDescription.class);
            } catch (IOException e) {
              throw new RuntimeException("Security group deserialization failed");
            }
          } else {
            // Found a Security Group that has been deleted since last time was read from Tencent
            // cloud
            securityGroup = null;
          }
          // There's no need to keep this entry in the map
          usableOnDemandCacheDatas.remove(sgKey);
        }
        if (securityGroup != null) {
          data.add(buildCacheData(securityGroup));
        }
      }

      log.info("Caching " + data.size() + " items in " + getAgentType());
      Map<String, Collection<CacheData>> cacheResultsMap = new HashMap<>();
      cacheResultsMap.put(SECURITY_GROUPS.ns, data);
      Map<String, Collection<String>> evictionsMap = new HashMap<>();
      evictionsMap.put(ON_DEMAND.ns, evictions);
      return new DefaultCacheResult(cacheResultsMap, evictionsMap);
    } else {
      if (updatedSecurityGroup != null) {
        // This is an OnDemand update/edit request for a given security group resource
        // Attempt to add entry into the OnDemand respective cache
        if (updateCache(providerCache, updatedSecurityGroup, "OnDemandUpdated")) {
          CacheData data = buildCacheData(updatedSecurityGroup);
          log.info("Caching 1 OnDemand updated item in " + getAgentType());
          Map<String, Collection<CacheData>> cacheResultsMap = new HashMap<>();
          cacheResultsMap.put(SECURITY_GROUPS.ns, Arrays.asList(data));
          return new DefaultCacheResult(cacheResultsMap);
        } else {
          return null;
        }
      }
      if (evictedSecurityGroup != null) {
        // This is an OnDemand delete request for a given Azure network security group resource
        // Attempt to add entry into the OnDemand respective cache
        if (updateCache(providerCache, evictedSecurityGroup, "OnDemandEvicted")) {
          log.info("Caching 1 OnDemand evicted item in " + getAgentType());
          Map<String, Collection<CacheData>> cacheResultsMap = new HashMap<>();
          cacheResultsMap.put(SECURITY_GROUPS.ns, new ArrayList<>());
          return new DefaultCacheResult(cacheResultsMap);
        } else {
          return null;
        }
      }
    }
    Map<String, Collection<CacheData>> cacheResultsMap = new HashMap<>();
    cacheResultsMap.put(SECURITY_GROUPS.ns, new ArrayList<>());
    return new DefaultCacheResult(cacheResultsMap);
  }

  private CacheData buildCacheData(TencentCloudSecurityGroupDescription securityGroup) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(SECURITY_GROUPS.ns, securityGroup);
    String key =
        Keys.getSecurityGroupKey(
            securityGroup.getSecurityGroupId(),
            securityGroup.getSecurityGroupName(),
            accountName,
            region);

    return new DefaultCacheData(key, attributes, new HashMap<>());
  }

  private Boolean updateCache(
      ProviderCache providerCache,
      TencentCloudSecurityGroupDescription securityGroup,
      String onDemandCacheType) {
    boolean foundUpdatedOnDemandSG = false;
    if (securityGroup != null) {
      // Get the current list of all OnDemand requests from the cache
      String key =
          Keys.getSecurityGroupKey(
              securityGroup.getSecurityGroupId(),
              securityGroup.getSecurityGroupName(),
              accountName,
              region);
      Collection<CacheData> cacheResults = providerCache.getAll(ON_DEMAND.ns, Arrays.asList(key));
      if (!CollectionUtils.isEmpty(cacheResults)) {
        for (CacheData cacheData : cacheResults) {
          // cacheResults.each should only return one item which is matching the given security
          // group details
          if ((long) cacheData.getAttributes().get("cachedTime")
              > securityGroup.getLastReadTime()) {
            // Found a newer matching entry in the cache when compared with the current OnDemand
            // request
            foundUpdatedOnDemandSG = true;
          }
        }
      }

      if (!foundUpdatedOnDemandSG) {
        String id =
            Keys.getSecurityGroupKey(
                securityGroup.getSecurityGroupId(),
                securityGroup.getSecurityGroupName(),
                accountName,
                region);
        Map<String, Object> attributes = new HashMap<>();
        try {
          attributes.put("securityGroup", objectMapper.writeValueAsString(securityGroup));
        } catch (JsonProcessingException e) {
          throw new RuntimeException("Security group deserialization failed");
        }
        attributes.put("cachedTime", securityGroup.getLastReadTime());
        attributes.put("onDemandCacheType", onDemandCacheType);
        Map<String, Collection<String>> relationships = new HashMap<>();

        DefaultCacheData cacheData = new DefaultCacheData(id, attributes, relationships);
        providerCache.putCacheData(ON_DEMAND.ns, cacheData);
        return true;
      }
    }
    return false;
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public final String getRegion() {
    return region;
  }

  public final TencentCloudNamedAccountCredentials getCredentials() {
    return credentials;
  }

  public final Registry getRegistry() {
    return registry;
  }

  public final OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
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
}
