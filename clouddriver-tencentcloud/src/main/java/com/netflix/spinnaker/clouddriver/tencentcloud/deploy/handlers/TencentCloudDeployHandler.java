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

package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.handlers;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.TencentCloudServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.TencentCloudDeployDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudClusterProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TencentCloudDeployHandler implements DeployHandler<TencentCloudDeployDescription> {

  private static final String BASE_PHASE = "DEPLOY";

  @Autowired private TencentCloudClusterProvider tencentCloudClusterProvider;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public boolean handles(DeployDescription description) {
    return description instanceof TencentCloudDeployDescription;
  }

  @Override
  public DeploymentResult handle(TencentCloudDeployDescription description, List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, "Initializing deployment to " + description.getZones());

    String accountName = description.getAccountName();
    String region = description.getRegion();
    TencentCloudServerGroupNameResolver serverGroupNameResolver =
        new TencentCloudServerGroupNameResolver(
            accountName, region, tencentCloudClusterProvider, description.getCredentials());

    getTask().updateStatus(BASE_PHASE, "Looking up next sequence...");

    String serverGroupName =
        serverGroupNameResolver.resolveNextServerGroupName(
            description.getApplication(), description.getStack(), description.getDetail(), false);

    getTask().updateStatus(BASE_PHASE, "Produced server group name: " + serverGroupName);

    description.setServerGroupName(serverGroupName);

    AutoScalingClient autoScalingClient =
        new AutoScalingClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            region);

    getTask().updateStatus(BASE_PHASE, "Composing server group " + serverGroupName + "...");

    autoScalingClient.deploy(description);

    getTask()
        .updateStatus(
            BASE_PHASE, "Done creating server group " + serverGroupName + " in " + region + ".");

    DeploymentResult deploymentResult = new DeploymentResult();
    deploymentResult.setServerGroupNames(
        new ArrayList<>(Arrays.asList(region + ":" + serverGroupName)));
    deploymentResult.getServerGroupNameByRegion().put(region, serverGroupName);
    return deploymentResult;
  }
}
