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
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.NAMED_IMAGES;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudImage;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.MutableCacheData;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.tencentcloudapi.cvm.v20170312.models.Image;
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

@Slf4j
public class TencentCloudImageCachingAgent extends AbstractTencentCloudCachingAgent {
  private final Set<AgentDataType> providedDataTypes =
      new HashSet<>(Collections.singletonList(AUTHORITATIVE.forType(IMAGES.ns)));

  private CloudVirtualMachineClient cvmClient;

  public TencentCloudImageCachingAgent(
      CloudVirtualMachineClient cvmClient,
      TencentCloudNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      String region) {
    super(credentials, objectMapper, region);
    this.cvmClient = cvmClient;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("start load image data");

    Map<String, Collection<CacheData>> cacheResultMap = new HashMap<>();
    Map<String, Map<String, CacheData>> namespaceCache = new HashMap<>();
    namespaceCache.put(IMAGES.ns, new HashMap<>());
    namespaceCache.put(NAMED_IMAGES.ns, new HashMap<>());

    Map<String, Collection<String>> evictions = new HashMap<>();
    evictions.put(NAMED_IMAGES.ns, new ArrayList<>());

    List<Image> imageList = cvmClient.getImages();

    for (Image image : imageList) {
      List<Map<String, Object>> snapshotList = new ArrayList<>();

      if (image.getSnapshotSet() != null) {
        snapshotList =
            Arrays.stream(image.getSnapshotSet())
                .<Map<String, Object>>map(
                    snapshot -> getObjectMapper().convertValue(snapshot, ATTRIBUTES))
                .collect(Collectors.toList());
      }

      TencentCloudImage tencentCloudImage =
          TencentCloudImage.builder()
              .region(getRegion())
              .name(image.getImageName())
              .imageId(image.getImageId())
              .type(image.getImageType())
              .osPlatform(image.getPlatform())
              .createdTime(image.getCreatedTime())
              .snapshotSet(snapshotList)
              .build();

      String imageKey = Keys.getImageKey(tencentCloudImage.getId(), getAccountName(), getRegion());
      String namedImageKey = Keys.getNamedImageKey(tencentCloudImage.getName(), getAccountName());
      Map<String, CacheData> images = namespaceCache.get(IMAGES.ns);

      CacheData imageCacheData = new MutableCacheData(imageKey);
      imageCacheData.getAttributes().put("image", tencentCloudImage);
      imageCacheData.getAttributes().put("snapshotSet", tencentCloudImage.getSnapshotSet());
      imageCacheData
          .getRelationships()
          .put(NAMED_IMAGES.ns, Collections.singletonList(namedImageKey));
      images.put(imageKey, imageCacheData);

      CacheData originImageCache = providerCache.get(IMAGES.ns, imageKey);

      if (originImageCache != null) {
        Collection<String> imageNames = originImageCache.getRelationships().get(NAMED_IMAGES.ns);
        if (imageNames != null && !imageNames.iterator().next().equals(namedImageKey)) {
          evictions
              .get(NAMED_IMAGES.ns)
              .addAll(originImageCache.getRelationships().get(NAMED_IMAGES.ns));
        }
      }

      Map<String, CacheData> namedImages = namespaceCache.get(NAMED_IMAGES.ns);
      CacheData namedImageCacheData = new MutableCacheData(namedImageKey);
      namedImageCacheData.getAttributes().put("imageName", tencentCloudImage.getName());
      namedImageCacheData.getAttributes().put("type", tencentCloudImage.getType());
      namedImageCacheData.getAttributes().put("osPlatform", tencentCloudImage.getOsPlatform());
      namedImageCacheData.getAttributes().put("snapshotSet", tencentCloudImage.getSnapshotSet());
      namedImageCacheData.getAttributes().put("createdTime", tencentCloudImage.getCreatedTime());
      namedImageCacheData.getRelationships().put(IMAGES.ns, Collections.singletonList(imageKey));
      namedImages.put(namedImageKey, namedImageCacheData);
    }

    namespaceCache.forEach(
        (namespace, cacheDataMap) -> cacheResultMap.put(namespace, cacheDataMap.values()));

    CacheResult defaultCacheResult = new DefaultCacheResult(cacheResultMap, evictions);
    log.info("finish loads image data.");
    log.info("Caching " + namespaceCache.get(IMAGES.ns).size() + " items in " + getAgentType());
    return defaultCacheResult;
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }
}
