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
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.DestroyTencentCloudServerGroupDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudServerGroup;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class DestroyTencentCloudServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP";

  private DestroyTencentCloudServerGroupDescription description;
  @Autowired private TencentCloudClusterProvider tencentCloudClusterProvider;
  private AutoScalingClient asClient;

  public DestroyTencentCloudServerGroupAtomicOperation(
      AutoScalingClient asClient, DestroyTencentCloudServerGroupDescription description) {
    this.asClient = asClient;
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing destroy server group "
                + description.getServerGroupName()
                + " in "
                + description.getRegion()
                + "...");
    String region = description.getRegion();
    String accountName = description.getAccountName();
    String serverGroupName = description.getServerGroupName();

    getTask().updateStatus(BASE_PHASE, "Start destroy server group " + serverGroupName);
    TencentCloudServerGroup serverGroup =
        tencentCloudClusterProvider.getServerGroup(accountName, region, serverGroupName, false);

    if (serverGroup != null) {
      String asgId = serverGroup.getAsg().getAutoScalingGroupId();
      String lcId = serverGroup.getAsg().getLaunchConfigurationId();

      getTask()
          .updateStatus(
              BASE_PHASE,
              "Server group "
                  + serverGroupName
                  + " is related to "
                  + "auto scaling group "
                  + asgId
                  + " and launch configuration "
                  + lcId
                  + ".");

      getTask().updateStatus(BASE_PHASE, "Deleting auto scaling group " + asgId + "...");
      asClient.deleteAutoScalingGroup(asgId);
      getTask().updateStatus(BASE_PHASE, "Auto scaling group " + asgId + " is deleted.");

      getTask().updateStatus(BASE_PHASE, "Deleting launch configuration " + lcId + "...");
      asClient.deleteLaunchConfiguration(lcId);
      getTask().updateStatus(BASE_PHASE, "Launch configuration " + lcId + " is deleted.");

      getTask().updateStatus(BASE_PHASE, "Complete destroy server group " + serverGroupName + ".");
    } else {
      getTask().updateStatus(BASE_PHASE, "Server group " + serverGroupName + " is not found.");
    }

    getTask().updateStatus(BASE_PHASE, "Complete destroy server group. ");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
