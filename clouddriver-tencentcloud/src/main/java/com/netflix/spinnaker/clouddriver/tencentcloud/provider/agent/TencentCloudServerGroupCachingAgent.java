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
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.ON_DEMAND;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.SERVER_GROUPS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudBasicResource;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudInstance;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudServerGroup;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.MutableCacheData;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import com.tencentcloudapi.as.v20180419.models.AutoScalingGroup;
import com.tencentcloudapi.as.v20180419.models.Instance;
import com.tencentcloudapi.as.v20180419.models.LaunchConfiguration;
import com.tencentcloudapi.as.v20180419.models.ScalingPolicy;
import com.tencentcloudapi.as.v20180419.models.ScheduledAction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
public class TencentCloudServerGroupCachingAgent extends AbstractTencentCloudCachingAgent
    implements OnDemandAgent {

  private String onDemandAgentType = getAgentType() + "-OnDemand";
  private AutoScalingClient autoScalingClient;
  private OnDemandMetricsSupport metricsSupport;
  private Namer<TencentCloudBasicResource> namer;
  private final Set<AgentDataType> providedDataTypes =
      new HashSet<>(
          Arrays.asList(
              AUTHORITATIVE.forType(APPLICATIONS.ns),
              AUTHORITATIVE.forType(SERVER_GROUPS.ns),
              INFORMATIVE.forType(CLUSTERS.ns),
              INFORMATIVE.forType(INSTANCES.ns),
              INFORMATIVE.forType(LOAD_BALANCERS.ns)));

  public TencentCloudServerGroupCachingAgent(
      AutoScalingClient autoScalingClient,
      TencentCloudNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      String region) {
    super(credentials, objectMapper, region);
    this.autoScalingClient = autoScalingClient;
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry, this, TencentCloudProvider.ID + ":" + OnDemandType.ServerGroup);
    this.namer =
        NamerRegistry.lookup()
            .withProvider(TencentCloudProvider.ID)
            .withAccount(credentials.getName())
            .withResource(TencentCloudBasicResource.class);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    long start = System.currentTimeMillis();
    log.info("start load data");

    List<TencentCloudServerGroup> serverGroups = loadAsgAsServerGroup(null);
    List<CacheData> toEvictOnDemandCacheData = new ArrayList<>();
    List<CacheData> toKeepOnDemandCacheData = new ArrayList<>();

    Set<String> serverGroupKeys =
        serverGroups.stream()
            .map(
                it -> Keys.getServerGroupKey(it.getName(), getCredentials().getName(), getRegion()))
            .collect(Collectors.toSet());

    List<String> pendingOnDemandRequestKeys =
        providerCache
            .filterIdentifiers(
                ON_DEMAND.ns,
                Keys.getServerGroupKey("*", "*", getCredentials().getName(), getRegion()))
            .stream()
            .filter(serverGroupKeys::contains)
            .collect(Collectors.toList());

    Collection<CacheData> pendingOnDemandRequestsForServerGroups =
        providerCache.getAll(ON_DEMAND.ns, pendingOnDemandRequestKeys);

    for (CacheData cacheData : pendingOnDemandRequestsForServerGroups) {
      long cacheTime = (long) cacheData.getAttributes().get("cacheTime");
      int processedCount =
          cacheData.getAttributes().get("processedCount") == null
              ? 0
              : (int) cacheData.getAttributes().get("processedCount");
      if (cacheTime < start && processedCount > 0) {
        toEvictOnDemandCacheData.add(cacheData);
      } else {
        toKeepOnDemandCacheData.add(cacheData);
      }
    }

    CacheResult result =
        buildCacheResult(serverGroups, toKeepOnDemandCacheData, toEvictOnDemandCacheData);
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
      Collection<TencentCloudServerGroup> serverGroups,
      Collection<CacheData> toKeepOnDemandCacheData,
      Collection<CacheData> toEvictOnDemandCacheData) {
    log.info("Start build cache for " + getAgentType());

    Map<String, Collection<String>> evictions = new HashMap<>();

    if (!CollectionUtils.isEmpty(toEvictOnDemandCacheData)) {
      List<String> idList =
          toEvictOnDemandCacheData.stream().map(CacheData::getId).collect(Collectors.toList());
      evictions.put(ON_DEMAND.ns, idList);
    }

    Map<String, Map<String, CacheData>> namespaceCache = new HashMap<>();

    for (TencentCloudServerGroup serverGroup : serverGroups) {
      Moniker moniker = getNamer().deriveMoniker(serverGroup);
      String applicationName = moniker.getApp();
      String clusterName = moniker.getCluster();

      if (applicationName == null || clusterName == null) {
        return null;
      }
      String serverGroupKey =
          Keys.getServerGroupKey(serverGroup.getName(), getAccountName(), getRegion());
      String clusterKey = Keys.getClusterKey(clusterName, applicationName, getAccountName());

      Set<TencentCloudInstance> instances = serverGroup.getInstances();
      List<String> instanceKeys =
          instances.stream()
              .map(
                  instance ->
                      Keys.getInstanceKey(instance.getName(), getAccountName(), getRegion()))
              .collect(Collectors.toList());

      Set<String> loadBalancerIds = serverGroup.getLoadBalancers();
      List<String> loadBalancerKeys =
          loadBalancerIds.stream()
              .map(id -> Keys.getLoadBalancerKey(id, getAccountName(), getRegion()))
              .collect(Collectors.toList());

      // application
      String appKey = Keys.getApplicationKey(applicationName);

      if (!namespaceCache.containsKey(APPLICATIONS.ns)) {
        namespaceCache.put(APPLICATIONS.ns, new HashMap<>());
        CacheData appData = new MutableCacheData(appKey);
        appData.getAttributes().put("name", applicationName);
        appData
            .getRelationships()
            .put(CLUSTERS.ns, new ArrayList<>(Collections.singletonList(clusterKey)));
        appData
            .getRelationships()
            .put(SERVER_GROUPS.ns, new ArrayList<>(Collections.singletonList(serverGroupKey)));
        namespaceCache.get(APPLICATIONS.ns).put(appKey, appData);
      } else {
        if (namespaceCache.get(APPLICATIONS.ns).containsKey(appKey)) {
          namespaceCache
              .get(APPLICATIONS.ns)
              .get(appKey)
              .getRelationships()
              .get(CLUSTERS.ns)
              .add(clusterKey);
          namespaceCache
              .get(APPLICATIONS.ns)
              .get(appKey)
              .getRelationships()
              .get(SERVER_GROUPS.ns)
              .add(serverGroupKey);
        } else {
          CacheData appData = new MutableCacheData(appKey);
          appData.getAttributes().put("name", applicationName);
          appData
              .getRelationships()
              .put(CLUSTERS.ns, new ArrayList<>(Collections.singletonList(clusterKey)));
          appData
              .getRelationships()
              .put(SERVER_GROUPS.ns, new ArrayList<>(Collections.singletonList(serverGroupKey)));
          namespaceCache.get(APPLICATIONS.ns).put(appKey, appData);
        }
      }

      // cluster
      if (namespaceCache.containsKey(CLUSTERS.ns)) {
        if (namespaceCache.get(CLUSTERS.ns).containsKey(clusterKey)) {
          namespaceCache
              .get(CLUSTERS.ns)
              .get(clusterKey)
              .getRelationships()
              .get(APPLICATIONS.ns)
              .add(appKey);
          namespaceCache
              .get(CLUSTERS.ns)
              .get(clusterKey)
              .getRelationships()
              .get(SERVER_GROUPS.ns)
              .add(serverGroupKey);
          namespaceCache
              .get(CLUSTERS.ns)
              .get(clusterKey)
              .getRelationships()
              .get(INSTANCES.ns)
              .addAll(instanceKeys);
          namespaceCache
              .get(CLUSTERS.ns)
              .get(clusterKey)
              .getRelationships()
              .get(LOAD_BALANCERS.ns)
              .addAll(loadBalancerKeys);
        } else {
          CacheData clustersData = new MutableCacheData(clusterKey);
          clustersData.getAttributes().put("name", clusterName);
          clustersData.getAttributes().put("accountName", getAccountName());
          clustersData
              .getRelationships()
              .put(APPLICATIONS.ns, new ArrayList<>(Collections.singletonList(appKey)));
          clustersData
              .getRelationships()
              .put(SERVER_GROUPS.ns, new ArrayList<>(Collections.singletonList(serverGroupKey)));
          clustersData.getRelationships().put(INSTANCES.ns, new ArrayList<>(instanceKeys));
          clustersData.getRelationships().put(LOAD_BALANCERS.ns, new ArrayList<>(loadBalancerKeys));
          namespaceCache.get(CLUSTERS.ns).put(clusterKey, clustersData);
        }
      } else {
        namespaceCache.put(CLUSTERS.ns, new HashMap<>());
        CacheData clustersData = new MutableCacheData(clusterKey);
        clustersData.getAttributes().put("name", clusterName);
        clustersData.getAttributes().put("accountName", getAccountName());
        clustersData
            .getRelationships()
            .put(APPLICATIONS.ns, new ArrayList<>(Collections.singletonList(appKey)));
        clustersData
            .getRelationships()
            .put(SERVER_GROUPS.ns, new ArrayList<>(Collections.singletonList(serverGroupKey)));
        clustersData.getRelationships().put(INSTANCES.ns, new ArrayList<>(instanceKeys));
        clustersData.getRelationships().put(LOAD_BALANCERS.ns, new ArrayList<>(loadBalancerKeys));
        namespaceCache.get(CLUSTERS.ns).put(clusterKey, clustersData);
      }

      // LoadBalancer
      if (!namespaceCache.containsKey(LOAD_BALANCERS.ns)) {
        namespaceCache.put(LOAD_BALANCERS.ns, new HashMap<>());
      }
      for (String key : loadBalancerKeys) {
        if (namespaceCache.get(LOAD_BALANCERS.ns).containsKey(key)) {
          Map<String, Collection<String>> relationships =
              namespaceCache.get(LOAD_BALANCERS.ns).get(key).getRelationships();
          if (relationships.containsKey(SERVER_GROUPS.ns)) {
            relationships.get(SERVER_GROUPS.ns).add(serverGroupKey);
          } else {
            relationships.put(SERVER_GROUPS.ns, new ArrayList<>(Arrays.asList(serverGroupKey)));
          }
        } else {
          CacheData lbData = new MutableCacheData(key);
          lbData
              .getRelationships()
              .put(SERVER_GROUPS.ns, new ArrayList<>(Arrays.asList(serverGroupKey)));
          namespaceCache.get(LOAD_BALANCERS.ns).put(key, lbData);
        }
      }

      // Server group
      CacheData onDemandServerGroupCache = null;
      if (toKeepOnDemandCacheData != null) {
        onDemandServerGroupCache =
            toKeepOnDemandCacheData.stream()
                .filter(it -> it.getAttributes().get("name").equals(serverGroupKey))
                .findFirst()
                .orElse(null);
      }
      if (onDemandServerGroupCache != null) {
        mergeOnDemandCache(onDemandServerGroupCache, namespaceCache);
      } else {
        if (namespaceCache.containsKey(SERVER_GROUPS.ns)) {
          if (namespaceCache.get(SERVER_GROUPS.ns).containsKey(serverGroupKey)) {
            namespaceCache
                .get(SERVER_GROUPS.ns)
                .get(serverGroupKey)
                .getRelationships()
                .get(APPLICATIONS.ns)
                .add(appKey);
            namespaceCache
                .get(SERVER_GROUPS.ns)
                .get(serverGroupKey)
                .getRelationships()
                .get(CLUSTERS.ns)
                .add(clusterKey);
            namespaceCache
                .get(SERVER_GROUPS.ns)
                .get(serverGroupKey)
                .getRelationships()
                .get(INSTANCES.ns)
                .addAll(instanceKeys);
            namespaceCache
                .get(SERVER_GROUPS.ns)
                .get(serverGroupKey)
                .getRelationships()
                .get(LOAD_BALANCERS.ns)
                .addAll(loadBalancerKeys);
          } else {
            MutableCacheData cacheData = new MutableCacheData(serverGroupKey);
            cacheData.getAttributes().put("asg", serverGroup.getAsg());
            cacheData.getAttributes().put("accountName", serverGroup.getAccountName());
            cacheData.getAttributes().put("name", serverGroup.getName());
            cacheData.getAttributes().put("region", serverGroup.getRegion());
            cacheData.getAttributes().put("launchConfig", serverGroup.getLaunchConfig());
            cacheData.getAttributes().put("disabled", serverGroup.getDisabled());
            cacheData.getAttributes().put("scalingPolicies", serverGroup.getScalingPolicies());
            cacheData.getAttributes().put("scheduledActions", serverGroup.getScheduledActions());
            cacheData
                .getRelationships()
                .put(APPLICATIONS.ns, new ArrayList<>(Collections.singletonList(appKey)));
            cacheData
                .getRelationships()
                .put(CLUSTERS.ns, new ArrayList<>(Collections.singletonList(clusterKey)));
            cacheData.getRelationships().put(INSTANCES.ns, new ArrayList<>(instanceKeys));
            cacheData.getRelationships().put(LOAD_BALANCERS.ns, new ArrayList<>(loadBalancerKeys));
            namespaceCache.get(SERVER_GROUPS.ns).put(serverGroupKey, cacheData);
          }
        } else {
          namespaceCache.put(SERVER_GROUPS.ns, new HashMap<>());
          MutableCacheData cacheData = new MutableCacheData(serverGroupKey);
          cacheData.getAttributes().put("asg", serverGroup.getAsg());
          cacheData.getAttributes().put("accountName", serverGroup.getAccountName());
          cacheData.getAttributes().put("name", serverGroup.getName());
          cacheData.getAttributes().put("region", serverGroup.getRegion());
          cacheData.getAttributes().put("launchConfig", serverGroup.getLaunchConfig());
          cacheData.getAttributes().put("disabled", serverGroup.getDisabled());
          cacheData.getAttributes().put("scalingPolicies", serverGroup.getScalingPolicies());
          cacheData.getAttributes().put("scheduledActions", serverGroup.getScheduledActions());
          cacheData
              .getRelationships()
              .put(APPLICATIONS.ns, new ArrayList<>(Collections.singletonList(appKey)));
          cacheData
              .getRelationships()
              .put(CLUSTERS.ns, new ArrayList<>(Collections.singletonList(clusterKey)));
          cacheData.getRelationships().put(INSTANCES.ns, new ArrayList<>(instanceKeys));
          cacheData.getRelationships().put(LOAD_BALANCERS.ns, new ArrayList<>(loadBalancerKeys));
          namespaceCache.get(SERVER_GROUPS.ns).put(serverGroupKey, cacheData);
        }
      }
    }

    Map<String, Collection<CacheData>> cacheResults = new HashMap<>();

    for (Map.Entry<String, Map<String, CacheData>> entry : namespaceCache.entrySet()) {
      String namespace = entry.getKey();
      Map<String, CacheData> cacheDataMap = entry.getValue();
      cacheResults.put(namespace, cacheDataMap.values());
    }
    if (!CollectionUtils.isEmpty(toKeepOnDemandCacheData)) {
      cacheResults.put(ON_DEMAND.ns, toKeepOnDemandCacheData);
    }

    return new DefaultCacheResult(cacheResults, evictions);
  }

  public void mergeOnDemandCache(
      CacheData onDemandServerGroupCache, Map<String, Map<String, CacheData>> namespaceCache) {
    Map<String, List<MutableCacheData>> onDemandCache = null;
    try {
      onDemandCache =
          getObjectMapper()
              .readValue(
                  (String) onDemandServerGroupCache.getAttributes().get("cacheResults"),
                  new TypeReference<Map<String, List<MutableCacheData>>>() {});

      for (Map.Entry<String, List<MutableCacheData>> entry : onDemandCache.entrySet()) {
        String namespace = entry.getKey();
        List<MutableCacheData> cacheDataList = entry.getValue();

        if (!namespace.equals("onDemand")) {
          for (CacheData cacheData : cacheDataList) {
            CacheData existingCacheData = null;
            if (namespaceCache.containsKey(namespace)) {
              existingCacheData = namespaceCache.get(namespace).get(cacheData.getId());
            } else {
              namespaceCache.put(namespace, new HashMap<>());
            }
            if (existingCacheData == null) {
              namespaceCache.get(namespace).put(cacheData.getId(), cacheData);
            } else {
              existingCacheData.getAttributes().putAll(cacheData.getAttributes());
              for (Map.Entry<String, Collection<String>> relation :
                  cacheData.getRelationships().entrySet()) {
                String relationshipName = relation.getKey();
                Collection<String> relationships = relation.getValue();
                existingCacheData.getRelationships().get(relationshipName).addAll(relationships);
              }
            }
          }
        }
      }
    } catch (IOException e) {
      log.error("Failed to deserialize", e);
      e.printStackTrace();
    }
  }

  public List<TencentCloudServerGroup> loadAsgAsServerGroup(String serverGroupName) {
    List<AutoScalingGroup> asgs;
    if (StringUtils.isEmpty(serverGroupName)) {
      asgs = autoScalingClient.getAllAutoScalingGroups();
    } else {
      asgs = autoScalingClient.getAutoScalingGroupsByName(serverGroupName);
    }

    List<String> launchConfigurationIds =
        asgs.stream().map(AutoScalingGroup::getLaunchConfigurationId).collect(Collectors.toList());

    List<LaunchConfiguration> launchConfigurations =
        autoScalingClient.getLaunchConfigurations(launchConfigurationIds);

    List<ScalingPolicy> scalingPolicies = loadScalingPolicies(null);

    List<ScheduledAction> scheduledActions = loadScheduledActions(null);

    List<Instance> autoScalingInstances = loadAutoScalingInstances(null);

    List<TencentCloudServerGroup> result = new ArrayList<>();
    for (AutoScalingGroup asg : asgs) {
      String autoScalingGroupId = asg.getAutoScalingGroupId();
      String autoScalingGroupName = asg.getAutoScalingGroupName();
      boolean disabled = asg.getEnabledStatus().equals("DISABLED");

      TencentCloudServerGroup serverGroup = new TencentCloudServerGroup();
      serverGroup.setAccountName(this.getAccountName());
      serverGroup.setRegion(this.getRegion());
      serverGroup.setName(autoScalingGroupName);
      serverGroup.setDisabled(disabled);
      serverGroup.setAsg(asg);

      String launchConfigurationId = asg.getLaunchConfigurationId();
      LaunchConfiguration launchConfiguration =
          launchConfigurations.stream()
              .filter(it -> it.getLaunchConfigurationId().equals(launchConfigurationId))
              .findFirst()
              .orElse(null);
      serverGroup.setLaunchConfig(getObjectMapper().convertValue(launchConfiguration, Map.class));

      List<ScalingPolicy> scalingPolicyList =
          scalingPolicies.stream()
              .filter(it -> it.getAutoScalingGroupId().equals(autoScalingGroupId))
              .collect(Collectors.toList());
      serverGroup.setScalingPolicies(scalingPolicyList);

      List<ScheduledAction> scheduledActionList =
          scheduledActions.stream()
              .filter(it -> it.getAutoScalingGroupId().equals(autoScalingGroupId))
              .collect(Collectors.toList());
      serverGroup.setScheduledActions(scheduledActionList);

      List<Instance> instanceList =
          autoScalingInstances.stream()
              .filter(it -> it.getAutoScalingGroupId().equals(autoScalingGroupId))
              .collect(Collectors.toList());

      for (Instance instance : instanceList) {
        TencentCloudInstance newInstance = new TencentCloudInstance();
        newInstance.setName(instance.getInstanceId());
        newInstance.setLaunchTime(
            AutoScalingClient.convertToIsoDateTime(instance.getAddTime()).getTime());
        newInstance.setZone(instance.getZone());
        newInstance.setAccount(getAccountName());
        newInstance.setServerGroupName(serverGroupName);
        serverGroup.getInstances().add(newInstance);
      }
      result.add(serverGroup);
    }
    return result;
  }

  private List<ScalingPolicy> loadScalingPolicies(String autoScalingGroupId) {
    return autoScalingClient.getScalingPolicies(autoScalingGroupId);
  }

  private List<ScheduledAction> loadScheduledActions(String autoScalingGroupId) {
    return autoScalingClient.getScheduledAction(autoScalingGroupId);
  }

  private List<Instance> loadAutoScalingInstances(String autoScalingGroupId) {
    return autoScalingClient.getAutoScalingInstances(autoScalingGroupId);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    String accountName = (String) data.get("account");
    String region = (String) data.get("region");
    String serverGroupName = (String) data.get("serverGroupName");
    if (!data.containsKey("serverGroupName")
        || !getAccountName().equals(accountName)
        || !getRegion().equals(region)) {
      return null;
    }

    log.info("Enter tencentcloud server group agent handle " + serverGroupName);

    TencentCloudServerGroup serverGroup =
        metricsSupport.readData(() -> loadAsgAsServerGroup(serverGroupName).get(0));

    if (serverGroup == null) {
      return null;
    }

    CacheResult cacheResult =
        metricsSupport.transformData(
            () -> buildCacheResult(Arrays.asList(serverGroup), null, null));

    String cacheResultAsJson = null;
    try {
      cacheResultAsJson = getObjectMapper().writeValueAsString(cacheResult.getCacheResults());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("CacheResult deserialization failed");
    }

    String serverGroupKey =
        Keys.getServerGroupKey(
            serverGroup.getMoniker().getCluster(), serverGroupName, getAccountName(), getRegion());

    List<CacheData> cacheDataList = new ArrayList<>();
    for (Collection<CacheData> dataCollection : cacheResult.getCacheResults().values()) {
      if (dataCollection != null) {
        cacheDataList.addAll(dataCollection);
      }
    }

    if (cacheDataList.isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously
      // existed).
      providerCache.evictDeletedItems(
          ON_DEMAND.ns, new ArrayList<>(Collections.singletonList(serverGroupKey)));
    } else {
      final String finalCacheResultAsJson = cacheResultAsJson;
      metricsSupport.onDemandStore(
          () -> {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("name", serverGroupKey);
            attributes.put("cacheTime", System.currentTimeMillis());
            attributes.put("cacheResults", finalCacheResultAsJson);

            CacheData cacheData =
                new DefaultCacheData(serverGroupKey, 10 * 60, attributes, new HashMap<>());
            providerCache.putCacheData(ON_DEMAND.ns, cacheData);
            return null;
          });
    }

    Map<String, Collection<String>> evictions = new HashMap<>();
    if (serverGroup.getAsg() == null) {
      evictions.put(SERVER_GROUPS.ns, Collections.singletonList(serverGroupKey));
    }

    return new OnDemandResult(getOnDemandAgentType(), cacheResult, evictions);
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.ServerGroup) && cloudProvider.equals(TencentCloudProvider.ID);
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<String> keys =
        providerCache.filterIdentifiers(
            ON_DEMAND.ns,
            Keys.getServerGroupKey("*", "*", getCredentials().getName(), getRegion()));
    return fetchPendingOnDemandRequests(providerCache, keys);
  }

  private Collection<Map> fetchPendingOnDemandRequests(
      ProviderCache providerCache, Collection<String> keys) {

    return providerCache.getAll(ON_DEMAND.ns, keys, RelationshipCacheFilter.none()).stream()
        .map(
            it -> {
              Map<String, Object> result = new HashMap<>();
              result.put("id", it.getId());
              result.put("details", Keys.parse(it.getId()));
              result.put("moniker", convertOnDemandDetails(Keys.parse(it.getId())));
              result.put("cacheTime", it.getAttributes().get("cacheTime"));
              result.put("cacheExpiry", it.getAttributes().get("cacheExpiry"));
              result.put("proccessedCount", it.getAttributes().get("processedCount"));
              result.put("processedTime", it.getAttributes().get("processedTime"));
              return result;
            })
        .collect(Collectors.toList());
  }

  public String getOnDemandAgentType() {
    return onDemandAgentType;
  }

  public void setOnDemandAgentType(String onDemandAgentType) {
    this.onDemandAgentType = onDemandAgentType;
  }

  public final OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  public final Namer<TencentCloudBasicResource> getNamer() {
    return namer;
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }
}
