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

import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.NETWORKS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.NetworkProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudNetwork;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudNetworkDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.TencentCloudInfrastructureProvider;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Component
public class TencentCloudNetworkProvider implements NetworkProvider<TencentCloudNetwork> {

  private final String cloudProvider = TencentCloudProvider.ID;
  private final Cache cacheView;
  private final ObjectMapper objectMapper;
  private final TencentCloudInfrastructureProvider tencentProvider;

  @Autowired
  public TencentCloudNetworkProvider(
      TencentCloudInfrastructureProvider provider, Cache cacheView, ObjectMapper objectMapper) {
    this.tencentProvider = provider;
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentCloudNetwork> getAll() {
    return getAllMatchingKeyPattern(Keys.getNetworkKey("*", "*", "*"));
  }

  public Set<TencentCloudNetwork> getAllMatchingKeyPattern(String pattern) {
    return loadResults(cacheView.filterIdentifiers(NETWORKS.ns, pattern));
  }

  public Set<TencentCloudNetwork> loadResults(Collection<String> identifiers) {
    Collection<CacheData> cacheDataCollection =
        cacheView.getAll(NETWORKS.ns, identifiers, RelationshipCacheFilter.none());

    return cacheDataCollection.stream().map(this::fromCacheData).collect(Collectors.toSet());
  }

  public TencentCloudNetwork fromCacheData(CacheData cacheData) {
    TencentCloudNetworkDescription description =
        objectMapper.convertValue(
            cacheData.getAttributes().get(NETWORKS.ns), TencentCloudNetworkDescription.class);
    Map<String, String> parts = Keys.parse(cacheData.getId());

    String account =
        (parts == null || parts.get("account") == null) ? "none" : parts.get("account");
    String region = (parts == null || parts.get("region") == null) ? "none" : parts.get("region");

    return new TencentCloudNetwork(
        description.getVpcId(),
        description.getVpcName(),
        account,
        region,
        description.getCidrBlock(),
        description.getIsDefault());
  }

  public final String getCloudProvider() {
    return cloudProvider;
  }

  public final Cache getCacheView() {
    return cacheView;
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
