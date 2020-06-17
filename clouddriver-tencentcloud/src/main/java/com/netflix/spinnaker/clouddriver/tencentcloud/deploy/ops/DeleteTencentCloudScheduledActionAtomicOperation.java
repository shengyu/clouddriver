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
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.DeleteTencentCloudScheduledActionDescription;
import java.util.List;

public class DeleteTencentCloudScheduledActionAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_SCHEDULED_ACTION";

  private DeleteTencentCloudScheduledActionDescription description;
  private AutoScalingClient asClient;

  public DeleteTencentCloudScheduledActionAtomicOperation(
      AutoScalingClient asClient, DeleteTencentCloudScheduledActionDescription description) {
    this.asClient = asClient;
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing delete scheduled action "
                + description.getScheduledActionId()
                + " in "
                + description.getServerGroupName()
                + "...");
    asClient.deleteScheduledAction(description.getScheduledActionId());
    getTask().updateStatus(BASE_PHASE, "Complete delete scheduled action. ");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
