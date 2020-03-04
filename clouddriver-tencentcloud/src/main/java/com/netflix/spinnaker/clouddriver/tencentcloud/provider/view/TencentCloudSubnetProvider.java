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

package com.netflix.spinnaker.clouddriver.tencentcloud.provider.view;

import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.SUBNETS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.SubnetProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudSubnet;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudSubnetDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.TencentCloudInfrastructureProvider;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TencentCloudSubnetProvider implements SubnetProvider<TencentCloudSubnet> {

  private String cloudProvider = TencentCloudProvider.ID;
  private Cache cacheView;
  private ObjectMapper objectMapper;
  private TencentCloudInfrastructureProvider tencentProvider;

  @Autowired
  public TencentCloudSubnetProvider(
      TencentCloudInfrastructureProvider provider, Cache cacheView, ObjectMapper objectMapper) {
    this.tencentProvider = provider;
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentCloudSubnet> getAll() {
    return getAllMatchingKeyPattern(Keys.getSubnetKey("*", "*", "*"));
  }

  public Set<TencentCloudSubnet> getAllMatchingKeyPattern(String pattern) {
    return loadResults(cacheView.filterIdentifiers(SUBNETS.ns, pattern));
  }

  public Set<TencentCloudSubnet> loadResults(Collection<String> identifiers) {
    Collection<CacheData> dataCollection =
        cacheView.getAll(SUBNETS.ns, identifiers, RelationshipCacheFilter.none());

    Set<TencentCloudSubnet> result = new HashSet<>();
    for (CacheData cacheData : dataCollection) {
      result.add(fromCacheData(cacheData));
    }

    return result;
  }

  public TencentCloudSubnet fromCacheData(CacheData cacheData) {
    TencentCloudSubnetDescription description =
        objectMapper.convertValue(
            cacheData.getAttributes().get(SUBNETS.ns), TencentCloudSubnetDescription.class);
    Map<String, String> parts = Keys.parse(cacheData.getId());

    String account = parts.get("account") == null ? "unknown" : parts.get("account");
    String region = parts.get("region") == null ? "unknown" : parts.get("region");

    TencentCloudSubnet subnet = new TencentCloudSubnet();

    subnet.setId(description.getSubnetId());
    subnet.setName(description.getSubnetName());
    subnet.setVpcId(description.getVpcId());
    subnet.setCidrBlock(description.getCidrBlock());
    subnet.setIsDefault(description.getIsDefault());
    subnet.setZone(description.getZone());
    subnet.setPurpose("");
    subnet.setAccount(account);
    subnet.setRegion(region);

    return subnet;
  }

  public final String getCloudProvider() {
    return cloudProvider;
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
