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
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.KEY_PAIRS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudKeyPair;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.MutableCacheData;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.tencentcloudapi.cvm.v20170312.models.KeyPair;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TencentCloudKeyPairCachingAgent extends AbstractTencentCloudCachingAgent {

  private final Set<AgentDataType> providedDataTypes =
      new HashSet<>(Collections.singletonList(AUTHORITATIVE.forType(KEY_PAIRS.ns)));

  private CloudVirtualMachineClient cvmClient;

  public TencentCloudKeyPairCachingAgent(
      CloudVirtualMachineClient cvmClient,
      TencentCloudNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      String region) {
    super(credentials, objectMapper, region);
    this.cvmClient = cvmClient;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("start load key pair data");

    Map<String, Collection<CacheData>> cacheResultMap = new HashMap<>();
    Map<String, Map<String, CacheData>> namespaceCache = new HashMap<>();
    namespaceCache.put(KEY_PAIRS.ns, new HashMap<>());

    List<KeyPair> keyPairList = cvmClient.getKeyPairs();

    for (KeyPair keyPair : keyPairList) {
      TencentCloudKeyPair tencentCloudKeyPair =
          TencentCloudKeyPair.builder()
              .keyId(keyPair.getKeyId())
              .keyName(keyPair.getKeyName())
              .keyFingerprint("")
              .region(getRegion())
              .account(getAccountName())
              .build();

      String keyPairKey =
          Keys.getKeyPairKey(tencentCloudKeyPair.getKeyName(), getAccountName(), getRegion());

      CacheData cacheData = new MutableCacheData(keyPairKey);
      cacheData.getAttributes().put("keyPair", tencentCloudKeyPair);
      namespaceCache.get(KEY_PAIRS.ns).put(keyPairKey, cacheData);
    }

    namespaceCache.forEach(
        (namespace, cacheDataMap) -> cacheResultMap.put(namespace, cacheDataMap.values()));

    CacheResult defaultCacheResult = new DefaultCacheResult(cacheResultMap);
    log.info("finish loads key pair data.");
    log.info("Caching " + namespaceCache.get(KEY_PAIRS.ns).size() + " items in " + getAgentType());

    return defaultCacheResult;
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }
}
