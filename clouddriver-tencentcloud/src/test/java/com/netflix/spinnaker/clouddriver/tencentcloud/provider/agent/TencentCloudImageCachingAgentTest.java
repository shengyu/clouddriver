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

import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.IMAGES;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.netflix.spinnaker.cats.agent.CacheResult;
import org.junit.jupiter.api.Test;

class TencentCloudImageCachingAgentTest extends BaseAgentTest {

  @Test
  void loadData() {
    TencentCloudImageCachingAgent agent =
        new TencentCloudImageCachingAgent(cvmClient, credentials, objectMapper, REGION);

    CacheResult cacheResult = agent.loadData(providerCache);
    assertEquals(cacheResult.getCacheResults().get(IMAGES.ns).size(), 0);
  }

  @Test
  void getProvidedDataTypes() {}
}
