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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.config.TencentCloudConfigurationProperties;
import com.netflix.spinnaker.clouddriver.tencentcloud.config.TencentCloudConfigurationProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.tencentcloudapi.as.v20180419.models.AutoScalingGroup;
import com.tencentcloudapi.cvm.v20170312.models.Image;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

public class BaseAgentTest {
  static final String REGION = "my-region";

  Registry registry = mock(Registry.class);

  AutoScalingClient asClient = mock(AutoScalingClient.class);

  CloudVirtualMachineClient cvmClient = mock(CloudVirtualMachineClient.class);

  ProviderCache providerCache = mock(ProviderCache.class);

  ObjectMapper objectMapper = new ObjectMapper();

  TencentCloudNamedAccountCredentials credentials;

  @BeforeEach
  void setUp() {
    TencentCloudConfigurationProperties.ManagedAccount managedAccount = new ManagedAccount();
    managedAccount.setName("my-tencent-account");
    managedAccount.setRegions(new ArrayList<>(Arrays.asList(REGION)));
    managedAccount.setSecretId("my-secret-id");
    managedAccount.setSecretKey("my-secret-key");
    credentials = new TencentCloudNamedAccountCredentials(managedAccount);

    setUpAsClient();
    setUpCvmClient();
  }

  private void setUpAsClient() {
    List<AutoScalingGroup> asgList = new ArrayList<>();
    AutoScalingGroup asg1 = new AutoScalingGroup();
    asg1.setAutoScalingGroupId("asg-1");
    asg1.setAutoScalingGroupName("my-asg-1");
    asg1.setAutoScalingGroupStatus("NORMAL");
    asg1.setCreatedTime("2019-12-27T08:54:49Z");
    asg1.setDefaultCooldown(10L);
    asg1.setDesiredCapacity(1L);
    asg1.setEnabledStatus("ENABLED");
    asg1.setInstanceCount(1L);
    asg1.setInServiceInstanceCount(1L);
    asg1.setLaunchConfigurationId("asc-1");
    asg1.setLaunchConfigurationName("my-lc-1");
    asg1.setMaxSize(1L);
    asg1.setMinSize(1L);
    asg1.setVpcId("vpc-1");
    asg1.setRetryPolicy("IMMEDIATE_RETRY");
    asg1.setInActivityStatus("NOT_IN_ACTIVITY");
    asgList.add(asg1);

    when(asClient.getAllAutoScalingGroups()).thenReturn(asgList);
  }

  private void setUpCvmClient() {
    List<Image> imageList = new ArrayList<>();
    when(cvmClient.getImages()).thenReturn(imageList);
  }
}
