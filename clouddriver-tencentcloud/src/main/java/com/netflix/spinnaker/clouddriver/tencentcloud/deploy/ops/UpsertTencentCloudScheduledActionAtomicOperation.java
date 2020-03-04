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
