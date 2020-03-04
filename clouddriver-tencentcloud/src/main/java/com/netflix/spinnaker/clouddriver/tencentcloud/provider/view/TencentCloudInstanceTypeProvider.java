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

import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.INSTANCE_TYPES;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.InstanceTypeProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudInstanceType;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TencentCloudInstanceTypeProvider
    implements InstanceTypeProvider<TencentCloudInstanceType> {

  @Autowired private Cache cacheView;
  private final ObjectMapper objectMapper;

  @Autowired
  public TencentCloudInstanceTypeProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentCloudInstanceType> getAll() {

    Collection<CacheData> cacheDataCollection =
        cacheView.getAll(
            INSTANCE_TYPES.ns,
            cacheView.filterIdentifiers(INSTANCE_TYPES.ns, Keys.getInstanceTypeKey("*", "*", "*")),
            RelationshipCacheFilter.none());

    return cacheDataCollection.stream()
        .map(
            cacheData ->
                objectMapper.convertValue(
                    cacheData.getAttributes().get("instanceType"), TencentCloudInstanceType.class))
        .collect(Collectors.toSet());
  }
}
