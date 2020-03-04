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

package com.netflix.spinnaker.clouddriver.tencentcloud.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TencentCloudInstance implements Instance, TencentCloudBasicResource {

  private final String cloudProvider = TencentCloudProvider.ID;
  private final String providerType = TencentCloudProvider.ID;
  private String instanceName;
  private String account;
  private String name;
  private Long launchTime;
  private String zone;
  private TencentCloudInstanceHealth instanceHealth;
  private TencentCloudTargetHealth targetHealth;
  private String vpcId;
  private String subnetId;
  private List<String> privateIpAddresses = new ArrayList<>();
  private List<String> publicIpAddresses = new ArrayList<>();
  private String instanceType;
  private String imageId;
  private List<String> securityGroupIds = new ArrayList<>();
  private List<Map<String, String>> tags = new ArrayList<>();
  private String serverGroupName;

  @Override
  public String getHumanReadableName() {
    return instanceName;
  }

  @Override
  @JsonIgnore
  public String getMonikerName() {
    return serverGroupName;
  }

  public List<Map<String, Object>> getHealth() {
    ObjectMapper objectMapper = new ObjectMapper();
    List<Map<String, Object>> healths = new ArrayList<>();

    if (instanceHealth != null) {
      healths.add(objectMapper.convertValue(instanceHealth, Map.class));
    }

    if (targetHealth != null) {
      healths.add(objectMapper.convertValue(targetHealth, Map.class));
    }

    return healths;
  }

  @Override
  public HealthState getHealthState() {
    if (someUpRemainingUnknown(getHealth())) {
      return HealthState.Up;
    } else {
      if (anyStarting(getHealth())) {
        return HealthState.Starting;
      } else {
        if (anyDown(getHealth())) {
          return HealthState.Down;
        } else {
          if (anyOutOfService(getHealth())) {
            return HealthState.OutOfService;
          }
        }
      }
    }
    return HealthState.Unknown;
  }

  public Moniker getMoniker() {
    return NamerRegistry.lookup()
        .withProvider(TencentCloudProvider.ID)
        .withAccount(account)
        .withResource(TencentCloudBasicResource.class)
        .deriveMoniker(this);
  }

  private static boolean someUpRemainingUnknown(List<Map<String, Object>> healthList) {
    List<Map<String, Object>> knownHealthList =
        healthList.stream()
            .filter(it -> HealthState.fromString((String) it.get("state")) != HealthState.Unknown)
            .collect(Collectors.toList());

    return knownHealthList.stream()
        .allMatch(knownHealth -> knownHealth.get("state").equals(HealthState.Up.toString()));
  }

  private static boolean anyStarting(List<Map<String, Object>> healthList) {
    return healthList.stream()
        .anyMatch(health -> health.get("state").equals(HealthState.Starting.toString()));
  }

  private static boolean anyDown(List<Map<String, Object>> healthList) {
    return healthList.stream()
        .anyMatch(health -> health.get("state").equals(HealthState.Down.toString()));
  }

  private static boolean anyOutOfService(List<Map<String, Object>> healthList) {
    return healthList.stream()
        .anyMatch(health -> health.get("state").equals(HealthState.OutOfService.toString()));
  }

  public final String getCloudProvider() {
    return cloudProvider;
  }

  public final String getProviderType() {
    return providerType;
  }

  public String getInstanceName() {
    return instanceName;
  }

  public void setInstanceName(String instanceName) {
    this.instanceName = instanceName;
  }

  public String getAccount() {
    return account;
  }

  public void setAccount(String account) {
    this.account = account;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Long getLaunchTime() {
    return launchTime;
  }

  public void setLaunchTime(Long launchTime) {
    this.launchTime = launchTime;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }

  public TencentCloudInstanceHealth getInstanceHealth() {
    return instanceHealth;
  }

  public void setInstanceHealth(TencentCloudInstanceHealth instanceHealth) {
    this.instanceHealth = instanceHealth;
  }

  public TencentCloudTargetHealth getTargetHealth() {
    return targetHealth;
  }

  public void setTargetHealth(TencentCloudTargetHealth targetHealth) {
    this.targetHealth = targetHealth;
  }

  public String getVpcId() {
    return vpcId;
  }

  public void setVpcId(String vpcId) {
    this.vpcId = vpcId;
  }

  public String getSubnetId() {
    return subnetId;
  }

  public void setSubnetId(String subnetId) {
    this.subnetId = subnetId;
  }

  public List<String> getPrivateIpAddresses() {
    return privateIpAddresses;
  }

  public void setPrivateIpAddresses(List<String> privateIpAddresses) {
    this.privateIpAddresses = privateIpAddresses;
  }

  public List<String> getPublicIpAddresses() {
    return publicIpAddresses;
  }

  public void setPublicIpAddresses(List<String> publicIpAddresses) {
    this.publicIpAddresses = publicIpAddresses;
  }

  public String getInstanceType() {
    return instanceType;
  }

  public void setInstanceType(String instanceType) {
    this.instanceType = instanceType;
  }

  public String getImageId() {
    return imageId;
  }

  public void setImageId(String imageId) {
    this.imageId = imageId;
  }

  public List<String> getSecurityGroupIds() {
    return securityGroupIds;
  }

  public void setSecurityGroupIds(List<String> securityGroupIds) {
    this.securityGroupIds = securityGroupIds;
  }

  public List<Map<String, String>> getTags() {
    return tags;
  }

  public void setTags(List<Map<String, String>> tags) {
    this.tags = tags;
  }

  public String getServerGroupName() {
    return serverGroupName;
  }

  public void setServerGroupName(String serverGroupName) {
    this.serverGroupName = serverGroupName;
  }
}
