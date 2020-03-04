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
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.NETWORKS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.VirtualPrivateCloudClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudNetworkDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.TencentCloudInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.tencentcloudapi.vpc.v20170312.models.Vpc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TencentCloudNetworkCachingAgent implements CachingAgent, AccountAware {

  private final ObjectMapper objectMapper;
  private final String region;
  private final String accountName;
  private final TencentCloudNamedAccountCredentials credentials;
  private final String providerName = TencentCloudInfrastructureProvider.class.getName();
  private final Set<AgentDataType> providedDataTypes =
      new HashSet<>(Arrays.asList(AUTHORITATIVE.forType(NETWORKS.ns)));
  private VirtualPrivateCloudClient vpcClient;

  public TencentCloudNetworkCachingAgent(
      VirtualPrivateCloudClient vpcClient,
      TencentCloudNamedAccountCredentials creds,
      ObjectMapper objectMapper,
      String region) {
    this.accountName = creds.getName();
    this.credentials = creds;
    this.objectMapper = objectMapper;
    this.region = region;
    this.vpcClient = vpcClient;
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getRegion() + "/" + this.getClass().getSimpleName();
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in " + getAgentType());

    List<TencentCloudNetworkDescription> networks = loadNetworksAll();

    List<CacheData> data = new ArrayList<>();
    for (TencentCloudNetworkDescription description : networks) {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put(NETWORKS.ns, description);
      data.add(
          new DefaultCacheData(
              Keys.getNetworkKey(description.getVpcId(), accountName, region),
              attributes,
              new HashMap<>()));
    }

    log.info("Caching " + data.size() + " items in " + getAgentType());
    Map<String, Collection<CacheData>> map = new HashMap<>();
    map.put(NETWORKS.ns, data);
    return new DefaultCacheResult(map);
  }

  private List<TencentCloudNetworkDescription> loadNetworksAll() {
    List<Vpc> networkSet = vpcClient.getNetworksAll();

    List<TencentCloudNetworkDescription> networkDescriptionSet = new ArrayList<>();
    for (Vpc vpc : networkSet) {
      TencentCloudNetworkDescription networkDesc = new TencentCloudNetworkDescription();
      networkDesc.setVpcId(vpc.getVpcId());
      networkDesc.setVpcName(vpc.getVpcName());
      networkDesc.setCidrBlock(vpc.getCidrBlock());
      networkDesc.setIsDefault(vpc.getIsDefault());
      networkDescriptionSet.add(networkDesc);
    }

    return networkDescriptionSet;
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public final String getRegion() {
    return region;
  }

  public final String getAccountName() {
    return accountName;
  }

  public final TencentCloudNamedAccountCredentials getCredentials() {
    return credentials;
  }

  public final String getProviderName() {
    return providerName;
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }
}
