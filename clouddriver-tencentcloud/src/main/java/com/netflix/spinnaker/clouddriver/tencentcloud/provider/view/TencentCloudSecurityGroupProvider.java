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

import static com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace.SECURITY_GROUPS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudSecurityGroup;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.TencentCloudInfrastructureProvider;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
public class TencentCloudSecurityGroupProvider
    implements SecurityGroupProvider<TencentCloudSecurityGroup> {

  private final String cloudProvider = TencentCloudProvider.ID;
  private final Cache cacheView;
  private final ObjectMapper objectMapper;
  private final TencentCloudInfrastructureProvider tencentProvider;

  @Autowired
  public TencentCloudSecurityGroupProvider(
      TencentCloudInfrastructureProvider provider, Cache cacheView, ObjectMapper objectMapper) {
    this.tencentProvider = provider;
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentCloudSecurityGroup> getAll(boolean includeRules) {
    log.info("Enter TencentCloudSecurityGroupProvider getAll,includeRules=" + includeRules);
    return getAllMatchingKeyPattern(Keys.getSecurityGroupKey("*", "*", "*", "*"), includeRules);
  }

  @Override
  public Set<TencentCloudSecurityGroup> getAllByRegion(boolean includeRules, String region) {
    log.info(
        "Enter TencentCloudSecurityGroupProvider getAllByRegion,includeRules="
            + includeRules
            + ",region="
            + region);
    return getAllMatchingKeyPattern(Keys.getSecurityGroupKey("*", "*", "*", region), includeRules);
  }

  @Override
  public Set<TencentCloudSecurityGroup> getAllByAccount(boolean includeRules, String account) {
    log.info(
        "Enter TencentCloudSecurityGroupProvider getAllByAccount,includeRules="
            + includeRules
            + ",account="
            + account);
    return getAllMatchingKeyPattern(Keys.getSecurityGroupKey("*", "*", account, "*"), includeRules);
  }

  @Override
  public Set<TencentCloudSecurityGroup> getAllByAccountAndName(
      boolean includeRules, String account, String securityGroupName) {
    log.info(
        "Enter TencentCloudSecurityGroupProvider getAllByAccountAndName,includeRules="
            + includeRules
            + "account="
            + account
            + ",securityGroupName="
            + securityGroupName);
    return getAllMatchingKeyPattern(
        Keys.getSecurityGroupKey("*", securityGroupName, account, "*"), includeRules);
  }

  @Override
  public Set<TencentCloudSecurityGroup> getAllByAccountAndRegion(
      boolean includeRules, String account, String region) {
    log.info(
        "Enter TencentCloudSecurityGroupProvider getAllByAccountAndRegion,includeRules="
            + includeRules
            + ",account="
            + account
            + ",region="
            + region);
    return getAllMatchingKeyPattern(
        Keys.getSecurityGroupKey("*", "*", account, region), includeRules);
  }

  @Override
  public TencentCloudSecurityGroup get(
      String account, String region, String securityGroupName, String other) {
    log.info(
        "Enter TencentCloudSecurityGroupProvider get,account="
            + account
            + ",region="
            + region
            + ",securityGroupName="
            + securityGroupName);
    String key = Keys.getSecurityGroupKey("*", securityGroupName, account, region);

    Set<TencentCloudSecurityGroup> securityGroups = getAllMatchingKeyPattern(key, true);
    if (CollectionUtils.isEmpty(securityGroups)) {
      return null;
    }
    // Get the first one
    return securityGroups.iterator().next();
  }

  public Set<TencentCloudSecurityGroup> getAllMatchingKeyPattern(
      String pattern, boolean includeRules) {
    log.info("Enter getAllMatchingKeyPattern pattern = " + pattern);
    return loadResults(includeRules, cacheView.filterIdentifiers(SECURITY_GROUPS.ns, pattern));
  }

  public Set<TencentCloudSecurityGroup> loadResults(
      boolean includeRules, Collection<String> identifiers) {
    Collection<CacheData> cacheDataCollection =
        cacheView.getAll(SECURITY_GROUPS.ns, identifiers, RelationshipCacheFilter.none());

    return cacheDataCollection.stream().map(this::fromCacheData).collect(Collectors.toSet());
  }

  public TencentCloudSecurityGroup fromCacheData(CacheData cacheData) {
    TencentCloudSecurityGroupDescription sg =
        objectMapper.convertValue(
            cacheData.getAttributes().get(SECURITY_GROUPS.ns),
            TencentCloudSecurityGroupDescription.class);

    Map<String, String> parts = Keys.parse(cacheData.getId());
    Names names = Names.parseName(sg.getSecurityGroupName());

    String application = names.getApp() == null ? "none" : names.getApp();
    String accountName =
        (parts == null || parts.get("account") == null) ? "none" : parts.get("account");
    String region = (parts == null || parts.get("region") == null) ? "none" : parts.get("region");

    return new TencentCloudSecurityGroup(
        sg.getSecurityGroupId(),
        sg.getSecurityGroupName(),
        sg.getSecurityGroupDesc(),
        application,
        accountName,
        region,
        new HashSet<>(),
        new HashSet<>(),
        sg.getInRules(),
        sg.getOutRules());
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
