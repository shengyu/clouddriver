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
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.SERVER_GROUPS;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerListener;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerRule;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer.TencentCloudLoadBalancerTarget;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.TencentCloudInfrastructureProvider;
import com.tencentcloudapi.as.v20180419.models.AutoScalingGroup;
import com.tencentcloudapi.as.v20180419.models.ForwardLoadBalancer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class TencentCloudLoadBalancerProvider
    implements LoadBalancerProvider<TencentCloudLoadBalancer> {

  private final String cloudProvider = TencentCloudProvider.ID;
  private final Cache cacheView;
  private final ObjectMapper objectMapper;
  private final TencentCloudInfrastructureProvider tencentProvider;

  @Autowired
  public TencentCloudLoadBalancerProvider(
      Cache cacheView, TencentCloudInfrastructureProvider tProvider, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.tencentProvider = tProvider;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentCloudLoadBalancer> getApplicationLoadBalancers(String applicationName) {
    log.info("Enter tencentcloud getApplicationLoadBalancers " + applicationName);

    CacheData application =
        cacheView.get(
            APPLICATIONS.ns,
            Keys.getApplicationKey(applicationName),
            RelationshipCacheFilter.include(LOAD_BALANCERS.ns));
    if (application == null) {
      return null;
    }
    Collection<String> loadBalancerKeys = application.getRelationships().get(LOAD_BALANCERS.ns);
    if (CollectionUtils.isEmpty(loadBalancerKeys)) {
      return null;
    }
    Collection<CacheData> loadBalancers = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys);
    if (CollectionUtils.isEmpty(loadBalancers)) {
      return null;
    }
    return translateLoadBalancersFromCacheData(loadBalancers);
  }

  public Set<TencentCloudLoadBalancer> getAll() {
    return getAllMatchingKeyPattern(Keys.getLoadBalancerKey("*", "*", "*"));
  }

  public Set<TencentCloudLoadBalancer> getAllMatchingKeyPattern(String pattern) {
    log.info("Enter getAllMatchingKeyPattern patten = " + pattern);
    return loadResults(cacheView.filterIdentifiers(LOAD_BALANCERS.ns, pattern));
  }

  public Set<TencentCloudLoadBalancer> loadResults(Collection<String> identifiers) {
    log.info("Enter loadResults id = " + identifiers);
    Collection<CacheData> cacheDataCollection =
        cacheView.getAll(LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.none());

    return cacheDataCollection.stream().map(this::fromCacheData).collect(Collectors.toSet());
  }

  private Set<LoadBalancerInstance> getLoadBalancerInstanceByListenerId(
      TencentCloudLoadBalancer loadBalancer, String listenerId) {

    TencentCloudLoadBalancerListener listener =
        loadBalancer.getListeners().stream()
            .filter(itr -> itr.getListenerId().equals(listenerId))
            .findFirst()
            .orElse(null);

    Set<LoadBalancerInstance> instances = new HashSet<>();

    if (listener != null) {
      for (TencentCloudLoadBalancerTarget target : listener.getTargets()) {
        instances.add(LoadBalancerInstance.builder().id(target.getInstanceId()).build());
      }

      for (TencentCloudLoadBalancerRule rule : listener.getRules()) {
        for (TencentCloudLoadBalancerTarget target : rule.getTargets()) {
          instances.add(LoadBalancerInstance.builder().id(target.getInstanceId()).build());
        }
      }
    }

    return instances;
  }

  private LoadBalancerServerGroup getLoadBalancerServerGroup(
      CacheData loadBalancerCache, TencentCloudLoadBalancer loadBalancerDesc) {
    Collection<String> serverGroupKeys = loadBalancerCache.getRelationships().get(SERVER_GROUPS.ns);
    if (CollectionUtils.isEmpty(serverGroupKeys)) {
      return null;
    }
    String serverGroupKey = serverGroupKeys.iterator().next();
    if (StringUtils.isEmpty(serverGroupKey)) {
      return null;
    }
    log.info(
        "loadBalancer "
            + loadBalancerDesc.getLoadBalancerId()
            + " bind serverGroup "
            + serverGroupKey);
    Map<String, String> parts = Keys.parse(serverGroupKey);
    LoadBalancerServerGroup lbServerGroup =
        LoadBalancerServerGroup.builder()
            .name(parts.get("name"))
            .account(parts.get("account"))
            .region(parts.get("region"))
            .build();

    CacheData serverGroup = cacheView.get(SERVER_GROUPS.ns, serverGroupKey);
    if (serverGroup == null) {
      return null;
    }

    AutoScalingGroup asgInfo = (AutoScalingGroup) serverGroup.getAttributes().get("asg");
    List<ForwardLoadBalancer> lbInfo = Arrays.asList(asgInfo.getForwardLoadBalancerSet());
    if (!CollectionUtils.isEmpty(lbInfo)) {
      String listenerId = lbInfo.get(0).getListenerId();
      log.info(
          String.format(
              "loadBalancer %s listener %s bind serverGroup %s",
              loadBalancerDesc.getLoadBalancerId(), listenerId, serverGroupKey));
      if (!StringUtils.isEmpty(listenerId)) {
        lbServerGroup.setInstances(
            getLoadBalancerInstanceByListenerId(loadBalancerDesc, listenerId));
      }
    }
    return lbServerGroup;
  }

  public TencentCloudLoadBalancer fromCacheData(CacheData cacheData) {
    TencentCloudLoadBalancer loadBalancer =
        objectMapper.convertValue(cacheData.getAttributes(), TencentCloudLoadBalancer.class);

    LoadBalancerServerGroup serverGroup = getLoadBalancerServerGroup(cacheData, loadBalancer);
    if (serverGroup != null) {
      loadBalancer.getServerGroups().add(serverGroup);
    }

    return loadBalancer;
  }

  @Override
  public List<TencentCloudLoadBalancerDetail> byAccountAndRegionAndName(
      final String account, final String region, final String id) {
    log.info(
        "Get loadBalancer byAccountAndRegionAndName: account="
            + account
            + ",region="
            + region
            + ",id="
            + id);
    String lbKey = Keys.getLoadBalancerKey(id, account, region);
    Collection<CacheData> lbCache = cacheView.getAll(LOAD_BALANCERS.ns, lbKey);

    List<TencentCloudLoadBalancerDetail> lbDetails = new ArrayList<>();

    for (CacheData cacheData : lbCache) {
      TencentCloudLoadBalancerDetail lbDetail = new TencentCloudLoadBalancerDetail();
      lbDetail.setId((String) cacheData.getAttributes().get("id"));
      lbDetail.setName((String) cacheData.getAttributes().get("name"));
      lbDetail.setAccount(account);
      lbDetail.setRegion(region);
      lbDetail.setVpcId((String) cacheData.getAttributes().get("vpcId"));
      lbDetail.setSubnetId((String) cacheData.getAttributes().get("subnetId"));
      lbDetail.setLoadBalancerType((String) cacheData.getAttributes().get("loadBalancerType"));
      lbDetail.setLoadBalancerVips(
          (List<String>) cacheData.getAttributes().get("loadBalancerVips"));
      lbDetail.setCreateTime((String) cacheData.getAttributes().get("createTime"));
      lbDetail.setSecurityGroups((List<String>) cacheData.getAttributes().get("securityGroups"));
      lbDetail.setListeners(
          (List<TencentCloudLoadBalancerListener>) cacheData.getAttributes().get("listeners"));

      lbDetails.add(lbDetail);
    }

    return lbDetails;
  }

  @Override
  public List<Item> list() {
    log.info("Enter list loadBalancer");
    String searchKey = Keys.getLoadBalancerKey("*", "*", "*");
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey);
    return new ArrayList<>(getSummaryForLoadBalancers(identifiers).values());
  }

  @Override
  public Item get(final String id) {
    log.info("Enter Get loadBalancer id " + id);
    String searchKey = Keys.getLoadBalancerKey(id, "*", "*");

    List<String> identifiers =
        cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey).stream()
            .filter(
                itr -> {
                  Map<String, String> keyMap = Keys.parse(itr);
                  if (keyMap == null || keyMap.get("id") == null) {
                    return false;
                  }
                  return keyMap.get("id").equals(id);
                })
            .collect(Collectors.toList());

    return getSummaryForLoadBalancers(identifiers).get(id);
  }

  private Map<String, TencentCloudLoadBalancerSummary> getSummaryForLoadBalancers(
      Collection<String> loadBalancerKeys) {

    Collection<CacheData> loadBalancerData = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys);

    Map<String, CacheData> loadBalancers = new HashMap<>();
    for (CacheData cacheData : loadBalancerData) {
      loadBalancers.put(cacheData.getId(), cacheData);
    }

    Map<String, TencentCloudLoadBalancerSummary> map = new HashMap<>();
    for (String lb : loadBalancerKeys) {
      CacheData loadBalancerFromCache = loadBalancers.get(lb);
      if (loadBalancerFromCache != null) {
        Map<String, String> parts = Keys.parse(lb);
        // loadBalancerId
        String name = parts.get("id");
        String region = parts.get("region");
        String account = parts.get("account");
        TencentCloudLoadBalancerSummary summary = map.get(name);
        if (summary == null) {
          summary = new TencentCloudLoadBalancerSummary();
          summary.setName(name);
          map.put(name, summary);
        }

        TencentCloudLoadBalancerDetail loadBalancer = new TencentCloudLoadBalancerDetail();
        loadBalancer.setAccount(parts.get("account"));
        loadBalancer.setRegion(parts.get("region"));
        loadBalancer.setId(parts.get("id"));
        loadBalancer.setVpcId((String) loadBalancerFromCache.getAttributes().get("vpcId"));
        loadBalancer.setName((String) loadBalancerFromCache.getAttributes().get("name"));

        summary
            .getOrCreateAccount(account)
            .getOrCreateRegion(region)
            .getLoadBalancers()
            .add(loadBalancer);
      }
    }

    return map;
  }

  private Set<TencentCloudLoadBalancer> translateLoadBalancersFromCacheData(
      Collection<CacheData> loadBalancerData) {
    return loadBalancerData.stream().map(this::fromCacheData).collect(Collectors.toSet());
  }

  public final String getCloudProvider() {
    return cloudProvider;
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public static class TencentCloudLoadBalancerSummary implements Item {

    private Map<String, TencentCloudLoadBalancerAccount> mappedAccounts = new HashMap<>();
    private String name;

    public TencentCloudLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        TencentCloudLoadBalancerAccount account = new TencentCloudLoadBalancerAccount();
        account.setName(name);
        mappedAccounts.put(name, account);
      }

      return mappedAccounts.get(name);
    }

    @JsonProperty("accounts")
    public List<TencentCloudLoadBalancerAccount> getByAccounts() {
      return new ArrayList<>(mappedAccounts.values());
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  public static class TencentCloudLoadBalancerAccount implements ByAccount {

    private Map<String, TencentCloudLoadBalancerAccountRegion> mappedRegions = new HashMap<>();
    private String name;

    public TencentCloudLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(
            name,
            TencentCloudLoadBalancerAccountRegion.builder()
                .name(name)
                .loadBalancers(new ArrayList<>())
                .build());
      }

      return mappedRegions.get(name);
    }

    @JsonProperty("regions")
    public List<TencentCloudLoadBalancerAccountRegion> getByRegions() {
      return new ArrayList<>(mappedRegions.values());
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @Data
  @Builder
  public static class TencentCloudLoadBalancerAccountRegion implements ByRegion {

    private String name;
    private List<TencentCloudLoadBalancerDetail> loadBalancers;
  }

  @Data
  public static class TencentCloudLoadBalancerDetail implements Details {

    private final String type = TencentCloudProvider.ID;
    private String account;
    private String region;
    private String name;
    private String id;
    private String loadBalancerType;
    private Integer forwardType;
    private String vpcId;
    private String subnetId;
    private Integer projectId;
    private String createTime;
    private List<String> loadBalancerVips;
    private List<String> securityGroups;
    private List<TencentCloudLoadBalancerListener> listeners;
  }
}
