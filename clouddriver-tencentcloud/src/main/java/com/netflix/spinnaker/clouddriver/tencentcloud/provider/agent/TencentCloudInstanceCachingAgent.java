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
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.SERVER_GROUPS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudInstance;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudInstanceHealth;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.MutableCacheData;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.netflix.spinnaker.moniker.Moniker;
import com.tencentcloudapi.as.v20180419.models.Instance;
import com.tencentcloudapi.cvm.v20170312.models.Tag;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TencentCloudInstanceCachingAgent extends AbstractTencentCloudCachingAgent {

  private final Set<AgentDataType> providedDataTypes =
      new HashSet<>(
          Arrays.asList(
              AUTHORITATIVE.forType(INSTANCES.ns),
              INFORMATIVE.forType(SERVER_GROUPS.ns),
              INFORMATIVE.forType(CLUSTERS.ns)));

  private AutoScalingClient asClient;
  private CloudVirtualMachineClient cvmClient;

  public TencentCloudInstanceCachingAgent(
      AutoScalingClient asClient,
      CloudVirtualMachineClient cvmClient,
      TencentCloudNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      String region) {
    super(credentials, objectMapper, region);
    this.asClient = asClient;
    this.cvmClient = cvmClient;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    // first, find all auto scaling instances
    // second, get detail info of below instances
    log.info("start load auto scaling instance data");

    Map<String, Collection<CacheData>> cacheResultsMap = new HashMap<>();
    Map<String, Map<String, CacheData>> namespaceCache = new HashMap<>();
    namespaceCache.put(INSTANCES.ns, new HashMap<>());

    List<Instance> asgInstances = asClient.getAutoScalingInstances();
    List<String> asgInstanceIds =
        asgInstances.stream().map(Instance::getInstanceId).collect(Collectors.toList());

    log.info("loads " + asgInstanceIds.size() + " auto scaling instances. ");

    log.info("start load instances detail info.");
    List<com.tencentcloudapi.cvm.v20170312.models.Instance> cvmInstances =
        cvmClient.getInstances(asgInstanceIds);

    for (com.tencentcloudapi.cvm.v20170312.models.Instance instance : cvmInstances) {
      Date launchTime = CloudVirtualMachineClient.convertToIsoDateTime(instance.getCreatedTime());
      Instance asgInstance =
          asgInstances.stream()
              .filter(asgIns -> asgIns.getInstanceId().equals(instance.getInstanceId()))
              .findFirst()
              .orElse(null);
      String launchConfigurationName = null;
      if (asgInstance != null) {
        launchConfigurationName = asgInstance.getLaunchConfigurationName();
      }

      Tag tag =
          Arrays.stream(instance.getTags())
              .filter(it -> it.getKey().equals(AutoScalingClient.getDefaultServerGroupTagKey()))
              .findFirst()
              .orElse(null);
      // filter non-spinnaker created instances
      if (tag == null) {
        continue;
      }
      String serverGroupName = tag.getValue();

      TencentCloudInstance tencentInstance = new TencentCloudInstance();
      tencentInstance.setAccount(getAccountName());
      tencentInstance.setName(instance.getInstanceId());
      tencentInstance.setInstanceName(instance.getInstanceName());
      tencentInstance.setLaunchTime(launchTime != null ? launchTime.getTime() : 0);
      tencentInstance.setZone(instance.getPlacement().getZone());
      tencentInstance.setVpcId(instance.getVirtualPrivateCloud().getVpcId());
      tencentInstance.setSubnetId(instance.getVirtualPrivateCloud().getSubnetId());
      tencentInstance.setPrivateIpAddresses(Arrays.asList(instance.getPrivateIpAddresses()));

      List<String> publicIps = new ArrayList<>();
      if (instance.getPublicIpAddresses() != null) {
        publicIps = Arrays.asList(instance.getPublicIpAddresses());
      }
      tencentInstance.setPublicIpAddresses(publicIps);

      tencentInstance.setImageId(instance.getImageId());
      tencentInstance.setInstanceType(instance.getInstanceType());
      tencentInstance.setSecurityGroupIds(Arrays.asList(instance.getSecurityGroupIds()));
      TencentCloudInstanceHealth instanceHealth =
          new TencentCloudInstanceHealth(instance.getInstanceState());
      tencentInstance.setInstanceHealth(instanceHealth);
      tencentInstance.setServerGroupName(
          serverGroupName != null ? serverGroupName : launchConfigurationName);

      if (instance.getTags() != null) {
        for (Tag it : instance.getTags()) {
          Map<String, String> tagMap = new HashMap<>();
          tagMap.put("key", it.getKey());
          tagMap.put("value", it.getValue());
          tencentInstance.getTags().add(tagMap);
        }
      }

      Map<String, CacheData> instances = namespaceCache.get(INSTANCES.ns);
      String instanceKey =
          Keys.getInstanceKey(instance.getInstanceId(), getAccountName(), getRegion());

      if (instances.containsKey(instanceKey)) {
        instances.get(instanceKey).getAttributes().put("instance", tencentInstance);
      } else {
        CacheData cacheData = new MutableCacheData(instanceKey);
        cacheData.getAttributes().put("instance", tencentInstance);
        instances.put(instanceKey, cacheData);
      }

      Moniker moniker = tencentInstance.getMoniker();
      if (moniker != null) {
        String clusterKey =
            Keys.getClusterKey(moniker.getCluster(), moniker.getApp(), getAccountName());
        String serverGroupKey =
            Keys.getServerGroupKey(
                tencentInstance.getServerGroupName(), getAccountName(), getRegion());
        Map<String, Collection<String>> relationships =
            instances.get(instanceKey).getRelationships();
        if (relationships.containsKey(CLUSTERS.ns)) {
          relationships.get(CLUSTERS.ns).add(clusterKey);
        } else {
          relationships.put(CLUSTERS.ns, Arrays.asList(clusterKey));
        }
        if (relationships.containsKey(SERVER_GROUPS.ns)) {
          relationships.get(SERVER_GROUPS.ns);
        } else {
          relationships.put(SERVER_GROUPS.ns, Arrays.asList(serverGroupKey));
        }
      }
    }

    namespaceCache.forEach(
        (namespace, cacheDataMap) -> cacheResultsMap.put(namespace, cacheDataMap.values()));

    CacheResult defaultCacheResult = new DefaultCacheResult(cacheResultsMap);
    log.info("finish loads instance data.");
    log.info("Caching " + namespaceCache.get(INSTANCES.ns).size() + " items in " + getAgentType());
    return defaultCacheResult;
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }
}
