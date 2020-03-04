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

import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.KEY_PAIRS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.KeyPairProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudKeyPair;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TencentCloudKeyPairProvider implements KeyPairProvider<TencentCloudKeyPair> {

  @Autowired private Cache cacheView;
  private final ObjectMapper objectMapper;

  @Autowired
  public TencentCloudKeyPairProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentCloudKeyPair> getAll() {
    Collection<CacheData> cacheDataCollection =
        cacheView.getAll(
            KEY_PAIRS.ns,
            cacheView.filterIdentifiers(KEY_PAIRS.ns, Keys.getKeyPairKey("*'", "*", "*")),
            RelationshipCacheFilter.none());

    return cacheDataCollection.stream()
        .map(
            cacheData ->
                objectMapper.convertValue(
                    cacheData.getAttributes().get("keyPair"), TencentCloudKeyPair.class))
        .collect(Collectors.toSet());
  }

  public Cache getCacheView() {
    return cacheView;
  }

  public void setCacheView(Cache cacheView) {
    this.cacheView = cacheView;
  }
}
