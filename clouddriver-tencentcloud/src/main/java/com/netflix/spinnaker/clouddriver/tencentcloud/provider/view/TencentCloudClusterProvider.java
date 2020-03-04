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

import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.LAUNCH_CONFIGS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.SERVER_GROUPS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CacheFilter;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudCluster;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudInstance;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudServerGroup;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class TencentCloudClusterProvider implements ClusterProvider<TencentCloudCluster> {

  @Autowired private TencentCloudProvider tencentCloudProvider;
  @Autowired private TencentCloudInstanceProvider tencentCloudInstanceProvider;
  @Autowired private Cache cacheView;

  @Override
  public Map<String, Set<TencentCloudCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns);
    Collection<TencentCloudCluster> clusters = translateClusters(clusterData, false);

    Map<String, Set<TencentCloudCluster>> result = new HashMap<>();

    for (TencentCloudCluster cluster : clusters) {
      if (!result.containsKey(cluster.getAccountName())) {
        Set<TencentCloudCluster> tencentCloudClusters = new HashSet<>();
        tencentCloudClusters.add(cluster);
        result.put(cluster.getAccountName(), tencentCloudClusters);
      } else {
        result.get(cluster.getAccountName()).add(cluster);
      }
    }
    return result;
  }

  @Override
  public Map<String, Set<TencentCloudCluster>> getClusterSummaries(String applicationName) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName));

    Map<String, Set<TencentCloudCluster>> result = new HashMap<>();

    if (application == null) {
      return null;
    }
    Collection<TencentCloudCluster> clusters =
        translateClusters(resolveRelationshipData(application, CLUSTERS.ns), false);
    for (TencentCloudCluster cluster : clusters) {
      if (!result.containsKey(cluster.getAccountName())) {
        Set<TencentCloudCluster> tencentCloudClusters = new HashSet<>();
        tencentCloudClusters.add(cluster);
        result.put(cluster.getAccountName(), tencentCloudClusters);
      } else {
        result.get(cluster.getAccountName()).add(cluster);
      }
    }

    return result;
  }

  @Override
  public Map<String, Set<TencentCloudCluster>> getClusterDetails(String applicationName) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName));

    if (application == null) {
      log.info("application is not found.");
      return null;
    }

    log.info("application is " + application.getId() + ".");
    Collection<TencentCloudCluster> clusters =
        translateClusters(resolveRelationshipData(application, CLUSTERS.ns), true);

    Map<String, Set<TencentCloudCluster>> result = new HashMap<>();

    for (TencentCloudCluster cluster : clusters) {
      if (!result.containsKey(cluster.getAccountName())) {
        Set<TencentCloudCluster> tencentCloudClusters = new HashSet<>();
        tencentCloudClusters.add(cluster);
        result.put(cluster.getAccountName(), tencentCloudClusters);
      } else {
        result.get(cluster.getAccountName()).add(cluster);
      }
    }

    return result;
  }

  @Override
  public Set<TencentCloudCluster> getClusters(String applicationName, final String account) {
    CacheData application =
        cacheView.get(
            APPLICATIONS.ns,
            Keys.getApplicationKey(applicationName),
            RelationshipCacheFilter.include(CLUSTERS.ns));

    if (application == null) {
      return null;
    }
    Collection<String> clusterKeys =
        application.getRelationships().get(CLUSTERS.ns).stream()
            .filter(itr -> Keys.parse(itr).get("account").equals(account))
            .collect(Collectors.toList());

    Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys);

    return (Set<TencentCloudCluster>) translateClusters(clusters, true);
  }

  @Override
  public TencentCloudCluster getCluster(
      String application, String account, String name, boolean includeDetails) {
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, application, account));

    if (cluster == null) {
      return null;
    }
    List<CacheData> cacheDataList = new ArrayList<>();
    cacheDataList.add(cluster);

    return translateClusters(cacheDataList, includeDetails).iterator().next();
  }

  @Override
  public TencentCloudCluster getCluster(
      String applicationName, String accountName, String clusterName) {
    return getCluster(applicationName, accountName, clusterName, true);
  }

  @Override
  public TencentCloudServerGroup getServerGroup(
      String account, String region, String name, boolean includeDetails) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region);
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey);

    if (serverGroupData == null) {
      return null;
    }
    // TODO shengyu 能否优化成class
    Map<String, String> launchConfig =
        (Map<String, String>) serverGroupData.getAttributes().get("launchConfig");

    String imageId = launchConfig.get("imageId");

    CacheData imageConfig = cacheView.get(IMAGES.ns, Keys.getImageKey(imageId, account, region));
    ObjectMapper objectMapper = new ObjectMapper();
    TencentCloudServerGroup serverGroup =
        objectMapper.convertValue(serverGroupData.getAttributes(), TencentCloudServerGroup.class);

    serverGroup.setAccountName(account);

    Map<String, Object> imageMap = null;
    if (imageConfig != null) {
      imageMap = (Map<String, Object>) imageConfig.getAttributes().get("image");
    }
    serverGroup.setImage(imageMap);

    if (includeDetails) {
      // show instances info
      serverGroup.setInstances(getServerGroupInstances(account, region, serverGroupData));
    }
    return serverGroup;
  }

  @Override
  public TencentCloudServerGroup getServerGroup(String account, String region, String name) {
    return getServerGroup(account, region, name, true);
  }

  @Override
  public String getCloudProviderId() {
    return tencentCloudProvider.getId();
  }

  @Override
  public boolean supportsMinimalClusters() {
    return true;
  }

  public String getServerGroupAsgId(String serverGroupName, String account, String region) {
    TencentCloudServerGroup serverGroup = getServerGroup(account, region, serverGroupName, false);
    String asgId = null;
    if (serverGroup != null) {
      asgId = serverGroup.getAsg().getAutoScalingGroupId();
    }
    return asgId;
  }

  private Collection<TencentCloudCluster> translateClusters(
      Collection<CacheData> clusterData, boolean includeDetails) {

    Map<String, TencentCloudLoadBalancer> loadBalancers = new HashMap<>();
    Map<String, TencentCloudServerGroup> serverGroups;

    if (includeDetails) {
      Collection<CacheData> allLoadBalancers =
          resolveRelationshipDataForCollection(clusterData, LOAD_BALANCERS.ns);
      Collection<CacheData> allServerGroups =
          resolveRelationshipDataForCollection(
              clusterData,
              SERVER_GROUPS.ns,
              RelationshipCacheFilter.include(INSTANCES.ns, LAUNCH_CONFIGS.ns));

      loadBalancers = translateLoadBalancers(allLoadBalancers);
      serverGroups = translateServerGroups(allServerGroups);
    } else {
      Collection<CacheData> allServerGroups =
          resolveRelationshipDataForCollection(
              clusterData, SERVER_GROUPS.ns, RelationshipCacheFilter.include(INSTANCES.ns));
      serverGroups = translateServerGroups(allServerGroups);
    }

    List<TencentCloudCluster> clusters = new ArrayList<>();
    for (CacheData clusterDataEntry : clusterData) {
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.getId());

      TencentCloudCluster cluster = new TencentCloudCluster();
      cluster.setAccountName(clusterKey.get("account"));
      cluster.setName(clusterKey.get("cluster"));

      if (clusterDataEntry.getRelationships().containsKey(SERVER_GROUPS.ns)) {
        Set<TencentCloudServerGroup> tencentServerGroupSet =
            clusterDataEntry.getRelationships().get(SERVER_GROUPS.ns).stream()
                .filter(serverGroups::containsKey)
                .map(serverGroups::get)
                .collect(Collectors.toSet());
        cluster.setServerGroups(tencentServerGroupSet);
      }

      if (clusterDataEntry.getRelationships().containsKey(LOAD_BALANCERS.ns)) {
        Set<TencentCloudLoadBalancer> tencentLoadBalancerSet = new HashSet<>();

        if (includeDetails) {
          for (String it : clusterDataEntry.getRelationships().get(LOAD_BALANCERS.ns)) {
            tencentLoadBalancerSet.add(loadBalancers.get(it));
          }
        } else {
          for (String it : clusterDataEntry.getRelationships().get(LOAD_BALANCERS.ns)) {
            Map parts = Keys.parse(it);
            TencentCloudLoadBalancer tlb = new TencentCloudLoadBalancer();
            tlb.setId((String) parts.get("id"));
            tlb.setAccountName((String) parts.get("account"));
            tlb.setRegion((String) parts.get("region"));
            tencentLoadBalancerSet.add(tlb);
          }
        }
        cluster.setLoadBalancers(tencentLoadBalancerSet);
      }
      clusters.add(cluster);
    }
    return clusters;
  }

  private static Map<String, TencentCloudLoadBalancer> translateLoadBalancers(
      Collection<CacheData> loadBalancerData) {
    Map<String, TencentCloudLoadBalancer> result = new HashMap<>();
    for (CacheData loadBalancerEntry : loadBalancerData) {
      Map<String, String> lbKey = Keys.parse(loadBalancerEntry.getId());
      TencentCloudLoadBalancer tlb = new TencentCloudLoadBalancer();
      tlb.setId(lbKey.get("id"));
      tlb.setAccountName(lbKey.get("account"));
      tlb.setRegion(lbKey.get("region"));
      result.put(loadBalancerEntry.getId(), tlb);
    }
    return result;
  }

  private Map<String, TencentCloudServerGroup> translateServerGroups(
      Collection<CacheData> serverGroupData) {
    ObjectMapper objectMapper = new ObjectMapper();
    Map<String, TencentCloudServerGroup> serverGroups = new HashMap<>();
    for (CacheData serverGroupEntry : serverGroupData) {
      TencentCloudServerGroup serverGroup =
          objectMapper.convertValue(
              serverGroupEntry.getAttributes(), TencentCloudServerGroup.class);

      String account = serverGroup.getAccountName();
      String region = serverGroup.getRegion();

      serverGroup.setInstances(getServerGroupInstances(account, region, serverGroupEntry));

      String imageId =
          ((Map<String, String>) serverGroupEntry.getAttributes().get("launchConfig"))
              .get("imageId");
      CacheData imageConfig = null;
      if (!StringUtils.isEmpty(imageId)) {
        imageConfig = cacheView.get(IMAGES.ns, Keys.getImageKey(imageId, account, region));
      }
      if (imageConfig != null) {
        serverGroup.setImage((Map<String, Object>) imageConfig.getAttributes().get("image"));
      }
      serverGroups.put(serverGroupEntry.getId(), serverGroup);
    }
    return serverGroups;
  }

  private Set<TencentCloudInstance> getServerGroupInstances(
      String account, String region, CacheData serverGroupData) {
    Collection<String> instanceKeys = serverGroupData.getRelationships().get(INSTANCES.ns);
    Collection<CacheData> instances = cacheView.getAll(INSTANCES.ns, instanceKeys);

    return instances.stream()
        .map(it -> tencentCloudInstanceProvider.instanceFromCacheData(account, region, it))
        .collect(Collectors.toSet());
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String type) {
    Collection<String> relationships = source.getRelationships().get(type);
    if (relationships == null) {
      return new ArrayList<>();
    }
    return cacheView.getAll(type, relationships, null);
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(
      Collection<CacheData> sources, String relationship, CacheFilter cacheFilter) {

    Collection<String> relationships = new ArrayList<>();
    for (CacheData cacheData : sources) {
      if (cacheData.getRelationships().containsKey(relationship)) {
        relationships.addAll(cacheData.getRelationships().get(relationship));
      }
    }

    return !CollectionUtils.isEmpty(relationships)
        ? cacheView.getAll(relationship, relationships, cacheFilter)
        : new ArrayList<>();
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(
      Collection<CacheData> sources, String relationship) {
    return resolveRelationshipDataForCollection(sources, relationship, null);
  }

  public TencentCloudProvider getTencentCloudProvider() {
    return tencentCloudProvider;
  }

  public void setTencentCloudProvider(TencentCloudProvider tencentCloudProvider) {
    this.tencentCloudProvider = tencentCloudProvider;
  }

  public TencentCloudInstanceProvider getTencentCloudInstanceProvider() {
    return tencentCloudInstanceProvider;
  }

  public void setTencentCloudInstanceProvider(
      TencentCloudInstanceProvider tencentCloudInstanceProvider) {
    this.tencentCloudInstanceProvider = tencentCloudInstanceProvider;
  }

  public Cache getCacheView() {
    return cacheView;
  }

  public void setCacheView(Cache cacheView) {
    this.cacheView = cacheView;
  }
}
