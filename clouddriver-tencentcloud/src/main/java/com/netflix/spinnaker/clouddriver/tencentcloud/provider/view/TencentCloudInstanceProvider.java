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

import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.HEALTH_CHECKS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.SERVER_GROUPS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudInstance;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudTargetHealth;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudTargetHealth.LBHealthSummary;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudTargetHealth.TargetHealthStatus;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerTargetHealth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class TencentCloudInstanceProvider
    implements InstanceProvider<TencentCloudInstance, String> {

  @Autowired private ObjectMapper objectMapper;
  @Autowired private TencentCloudProvider tencentCloudProvider;
  @Autowired private Cache cacheView;

  @Override
  public TencentCloudInstance getInstance(final String account, final String region, String id) {
    String key = Keys.getInstanceKey(id, account, region);

    Collection<CacheData> cacheDataCollection =
        cacheView.getAll(
            INSTANCES.ns,
            new ArrayList<>(Arrays.asList(key)),
            RelationshipCacheFilter.include(LOAD_BALANCERS.ns, SERVER_GROUPS.ns, CLUSTERS.ns));

    return cacheDataCollection.stream()
        .findFirst()
        .map(cacheData -> instanceFromCacheData(account, region, cacheData))
        .orElse(null);
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }

  @Override
  public String getCloudProvider() {
    return TencentCloudProvider.ID;
  }

  public TencentCloudInstance instanceFromCacheData(
      String account, String region, CacheData cacheData) {
    TencentCloudInstance instance =
        objectMapper.convertValue(
            cacheData.getAttributes().get("instance"), TencentCloudInstance.class);

    String serverGroupName = instance.getServerGroupName();
    CacheData serverGroupCache =
        cacheView.get(SERVER_GROUPS.ns, Keys.getServerGroupKey(serverGroupName, account, region));

    Map<String, Object> attributes =
        serverGroupCache == null ? null : serverGroupCache.getAttributes();

    Assert.notNull(attributes, "Attributes from cache must not be null");

    Map asgInfo = (Map) attributes.get("asg");
    List lbInfo = (List) asgInfo.get("forwardLoadBalancerSet");
    if (lbInfo != null) {
      String lbId = (String) ((Map) lbInfo.get(0)).get("loadBalancerId");
      String listenerId = (String) ((Map) lbInfo.get(0)).get("listenerId");
      String lbHealthKey =
          Keys.getTargetHealthKey(lbId, listenerId, instance.getName(), account, region);
      CacheData lbHealthCache = cacheView.get(HEALTH_CHECKS.ns, lbHealthKey);

      Map<String, Object> attributes1 =
          (lbHealthCache == null ? null : lbHealthCache.getAttributes());
      TencentCloudLoadBalancerTargetHealth loadBalancerTargetHealth =
          attributes1 == null
              ? null
              : objectMapper.convertValue(
                  attributes1.get("targetHealth"), TencentCloudLoadBalancerTargetHealth.class);

      if (loadBalancerTargetHealth != null) {
        instance.setTargetHealth(
            new TencentCloudTargetHealth(loadBalancerTargetHealth.getHealthStatus()));
        TargetHealthStatus healthStatus = instance.getTargetHealth().getTargetHealthStatus();
        LBHealthSummary summary = new LBHealthSummary();
        summary.setLoadBalancerName(lbId);
        summary.setState(healthStatus.toServiceStatus());
        instance.getTargetHealth().getLoadBalancers().add(summary);
      } else {
        // if server group has lb, but can't get lb health check result for instance in server group
        // assume the target health check result is UNKNOWN
        instance.setTargetHealth(new TencentCloudTargetHealth());
      }
    }

    return instance;
  }
}
