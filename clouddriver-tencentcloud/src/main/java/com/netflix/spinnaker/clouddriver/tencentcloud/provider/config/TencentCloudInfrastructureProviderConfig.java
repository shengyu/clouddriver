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

package com.netflix.spinnaker.clouddriver.tencentcloud.provider.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.LoadBalancerClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.VirtualPrivateCloudClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.TencentCloudInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent.TencentCloudImageCachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent.TencentCloudInstanceCachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent.TencentCloudInstanceTypeCachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent.TencentCloudKeyPairCachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent.TencentCloudLoadBalancerCachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent.TencentCloudLoadBalancerInstanceStateCachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent.TencentCloudNetworkCachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent.TencentCloudSecurityGroupCachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent.TencentCloudServerGroupCachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.agent.TencentCloudSubnetCachingAgent;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials.TencentCloudRegion;
import com.netflix.spinnaker.config.TencentCloudConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

@Configuration
@Import(TencentCloudConfiguration.class)
@EnableConfigurationProperties
public class TencentCloudInfrastructureProviderConfig {

  @Autowired private Registry registry;

  @Bean
  @DependsOn("tencentCloudNamedAccountCredentials")
  public TencentCloudInfrastructureProvider tencentCloudInfrastructureProvider(
      AccountCredentialsRepository accountCredentialsRepository,
      ObjectMapper objectMapper,
      Registry registry) {

    List<TencentCloudNamedAccountCredentials> allAccounts =
        accountCredentialsRepository.getAll().stream()
            .filter(credentials -> credentials instanceof TencentCloudNamedAccountCredentials)
            .map(it -> (TencentCloudNamedAccountCredentials) it)
            .collect(Collectors.toList());

    // enable multiple accounts and multiple regions in each account
    List<Agent> agents = new ArrayList<>();
    for (TencentCloudNamedAccountCredentials credentials : allAccounts) {
      for (TencentCloudRegion region : credentials.getRegions()) {
        AutoScalingClient autoScalingClient =
            new AutoScalingClient(
                credentials.getCredentials().getSecretId(),
                credentials.getCredentials().getSecretKey(),
                region.getName());
        CloudVirtualMachineClient cvmClient =
            new CloudVirtualMachineClient(
                credentials.getCredentials().getSecretId(),
                credentials.getCredentials().getSecretKey(),
                region.getName());
        LoadBalancerClient lbClient =
            new LoadBalancerClient(
                credentials.getCredentials().getSecretId(),
                credentials.getCredentials().getSecretKey(),
                region.getName());
        VirtualPrivateCloudClient vpcClient =
            new VirtualPrivateCloudClient(
                credentials.getCredentials().getSecretId(),
                credentials.getCredentials().getSecretKey(),
                region.getName());

        agents.add(
            new TencentCloudServerGroupCachingAgent(
                autoScalingClient, credentials, objectMapper, registry, region.getName()));

        agents.add(
            new TencentCloudInstanceTypeCachingAgent(
                cvmClient, credentials, objectMapper, region.getName()));

        agents.add(
            new TencentCloudKeyPairCachingAgent(
                cvmClient, credentials, objectMapper, region.getName()));

        agents.add(
            new TencentCloudImageCachingAgent(
                cvmClient, credentials, objectMapper, region.getName()));

        agents.add(
            new TencentCloudInstanceCachingAgent(
                autoScalingClient, cvmClient, credentials, objectMapper, region.getName()));

        agents.add(
            new TencentCloudLoadBalancerCachingAgent(
                lbClient, credentials, objectMapper, registry, region.getName()));

        agents.add(
            new TencentCloudSecurityGroupCachingAgent(
                vpcClient, credentials, objectMapper, registry, region.getName()));

        agents.add(
            new TencentCloudNetworkCachingAgent(
                vpcClient, credentials, objectMapper, region.getName()));

        agents.add(
            new TencentCloudSubnetCachingAgent(
                vpcClient, credentials, objectMapper, region.getName()));

        agents.add(
            new TencentCloudLoadBalancerInstanceStateCachingAgent(
                lbClient, credentials, objectMapper, region.getName()));
      }
    }

    return new TencentCloudInfrastructureProvider(agents);
  }
}
