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
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.INSTANCE_TYPES;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudInstanceType;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.MutableCacheData;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.tencentcloudapi.cvm.v20170312.models.InstanceTypeConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TencentCloudInstanceTypeCachingAgent extends AbstractTencentCloudCachingAgent {

  private final List<AgentDataType> providedDataTypes =
      new ArrayList<>(Arrays.asList(AUTHORITATIVE.forType(INSTANCE_TYPES.ns)));

  private CloudVirtualMachineClient cvmClient;

  public TencentCloudInstanceTypeCachingAgent(
      CloudVirtualMachineClient cvmClient,
      TencentCloudNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      String region) {
    super(credentials, objectMapper, region);
    this.cvmClient = cvmClient;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("start load instance types data");

    Map<String, Collection<CacheData>> cacheResultsMap = new HashMap<>();
    Map<String, Map<String, CacheData>> namespaceCache = new HashMap<>();

    InstanceTypeConfig[] result = cvmClient.getInstanceTypes();
    namespaceCache.put(INSTANCE_TYPES.ns, new HashMap<>());
    for (InstanceTypeConfig it : result) {
      TencentCloudInstanceType tencentCloudInstanceType = new TencentCloudInstanceType();
      tencentCloudInstanceType.setName(it.getInstanceType());
      tencentCloudInstanceType.setAccount(getAccountName());
      tencentCloudInstanceType.setRegion(getRegion());
      tencentCloudInstanceType.setZone(it.getZone());
      tencentCloudInstanceType.setInstanceFamily(it.getInstanceFamily());
      tencentCloudInstanceType.setCpu(it.getCPU());
      tencentCloudInstanceType.setMem(it.getMemory());

      Map<String, CacheData> instanceTypes = namespaceCache.get(INSTANCE_TYPES.ns);
      String instanceTypeKey =
          Keys.getInstanceTypeKey(
              getAccountName(), getRegion(), tencentCloudInstanceType.getName());
      if (instanceTypes.containsKey(instanceTypeKey)) {
        instanceTypes
            .get(instanceTypeKey)
            .getAttributes()
            .put("instanceType", tencentCloudInstanceType);
      } else {
        CacheData cacheData = new MutableCacheData(instanceTypeKey);
        cacheData.getAttributes().put("instanceType", tencentCloudInstanceType);
        instanceTypes.put(instanceTypeKey, cacheData);
      }
    }

    namespaceCache.forEach(
        (namespace, cacheDataMap) -> cacheResultsMap.put(namespace, cacheDataMap.values()));

    CacheResult defaultCacheResult = new DefaultCacheResult(cacheResultsMap);
    log.info("finish loads instance type data.");
    log.info(
        "Caching " + namespaceCache.get(INSTANCE_TYPES.ns).size() + " items in " + getAgentType());
    return defaultCacheResult;
  }

  public final List<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }
}
