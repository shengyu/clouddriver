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
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudScheduledActionDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudScheduledActionDescription.OperationType;
import com.netflix.spinnaker.clouddriver.tencentcloud.exception.TencentCloudOperationException;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class UpsertTencentCloudScheduledActionAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "UPSERT_SCHEDULED_ACTIONS";

  private AutoScalingClient asClient;
  private UpsertTencentCloudScheduledActionDescription description;
  @Autowired private TencentCloudClusterProvider tencentCloudClusterProvider;

  public UpsertTencentCloudScheduledActionAtomicOperation(
      AutoScalingClient asClient, UpsertTencentCloudScheduledActionDescription description) {
    this.asClient = asClient;
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String region = description.getRegion();
    String serverGroupName = description.getServerGroupName();
    String accountName = description.getAccountName();
    String asgId =
        tencentCloudClusterProvider.getServerGroupAsgId(serverGroupName, accountName, region);

    if (asgId == null) {
      throw new TencentCloudOperationException("ASG of " + serverGroupName + " is not found.");
    }

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing upsert scheduled action " + serverGroupName + " in " + region + "...");

    if (description.getOperationType().equals(OperationType.CREATE)) {
      getTask().updateStatus(BASE_PHASE, "create scheduled action in " + serverGroupName + "...");
      String scalingPolicyId = asClient.createScheduledAction(asgId, description);
      getTask()
          .updateStatus(BASE_PHASE, "new scheduled action " + scalingPolicyId + " is created.");
    } else if (description.getOperationType().equals(OperationType.MODIFY)) {
      String scheduledActionId = description.getScheduledActionId();
      getTask()
          .updateStatus(
              BASE_PHASE,
              "update scheduled action " + scheduledActionId + " in " + serverGroupName + "...");
      asClient.modifyScheduledAction(scheduledActionId, description);
    } else {
      throw new TencentCloudOperationException("unknown operation type, operation quit.");
    }

    getTask().updateStatus(BASE_PHASE, "Complete upsert scheduled action.");

    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
