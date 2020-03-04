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

package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description;

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable;
import com.tencentcloudapi.as.v20180419.models.DataDisk;
import com.tencentcloudapi.as.v20180419.models.EnhancedService;
import com.tencentcloudapi.as.v20180419.models.ForwardLoadBalancer;
import com.tencentcloudapi.as.v20180419.models.InstanceMarketOptionsRequest;
import com.tencentcloudapi.as.v20180419.models.InstanceTag;
import com.tencentcloudapi.as.v20180419.models.InternetAccessible;
import com.tencentcloudapi.as.v20180419.models.LoginSettings;
import com.tencentcloudapi.as.v20180419.models.SystemDisk;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class TencentCloudDeployDescription extends AbstractTencentCloudCredentialsDescription
    implements DeployDescription {
  /** common */
  private String application;

  private String stack;
  private String detail;
  private String region;
  private String accountName;
  private String serverGroupName;

  /** launch configuration part */
  private String instanceType;

  private String imageId;
  private Integer projectId;
  private SystemDisk systemDisk;
  private List<DataDisk> dataDisks;
  private InternetAccessible internetAccessible;
  private LoginSettings loginSettings;
  private List<String> securityGroupIds;
  private EnhancedService enhancedService;
  private String userData;
  private String instanceChargeType;
  private InstanceMarketOptionsRequest instanceMarketOptionsRequest;
  private List<String> instanceTypes;
  private String instanceTypesCheckPolicy;
  private List<InstanceTag> instanceTags;

  /** auto scaling group part */
  private Integer maxSize;

  private Integer minSize;
  private Integer desiredCapacity;
  private String vpcId;
  private Integer defaultCooldown;
  private List<String> loadBalancerIds;
  private List<ForwardLoadBalancer> forwardLoadBalancers;
  private List<String> subnetIds;
  private List<String> terminationPolicies;
  private List<String> zones;
  private String retryPolicy;
  private String zonesCheckPolicy;

  /** clone source */
  private Source source = new Source();

  @Data
  public static class Source implements ServerGroupsNameable {
    private String region;
    private String serverGroupName;

    @Override
    public Collection<String> getServerGroupNames() {
      return Collections.singletonList(serverGroupName);
    }
  }
}
