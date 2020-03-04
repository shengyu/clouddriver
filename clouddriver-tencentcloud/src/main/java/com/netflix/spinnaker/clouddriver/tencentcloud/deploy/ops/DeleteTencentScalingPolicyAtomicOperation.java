package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.DeleteTencentCloudScalingPolicyDescription;
import java.util.List;

public class DeleteTencentScalingPolicyAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_SCALING_POLICY";

  private DeleteTencentCloudScalingPolicyDescription description;
  private AutoScalingClient asClient;

  public DeleteTencentScalingPolicyAtomicOperation(
      AutoScalingClient asClient, DeleteTencentCloudScalingPolicyDescription description) {
    this.asClient = asClient;
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing delete scaling policy "
                + description.getScalingPolicyId()
                + " in "
                + description.getServerGroupName()
                + "...");
    asClient.deleteScalingPolicy(description.getScalingPolicyId());
    getTask().updateStatus(BASE_PHASE, "Complete delete scaling policy. ");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
