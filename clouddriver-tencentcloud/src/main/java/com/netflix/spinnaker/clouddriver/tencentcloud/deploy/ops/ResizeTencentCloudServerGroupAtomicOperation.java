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

package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.ResizeTencentCloudServerGroupDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class ResizeTencentCloudServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP";

  @Autowired private TencentCloudClusterProvider tencentCloudClusterProvider;
  private ResizeTencentCloudServerGroupDescription description;
  private AutoScalingClient asClient;

  public ResizeTencentCloudServerGroupAtomicOperation(
      AutoScalingClient asClient, ResizeTencentCloudServerGroupDescription description) {
    this.asClient = asClient;
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "Initializing resize of server group %s in %s...",
                description.getServerGroupName(), description.getRegion()));

    String accountName = description.getAccountName();
    String region = description.getRegion();
    String serverGroupName = description.getServerGroupName();
    String asgId =
        tencentCloudClusterProvider.getServerGroupAsgId(serverGroupName, accountName, region);

    asClient.resizeAutoScalingGroup(asgId, description.getCapacity());
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Complete resize of server group "
                + description.getServerGroupName()
                + " in "
                + description.getRegion()
                + ".");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}