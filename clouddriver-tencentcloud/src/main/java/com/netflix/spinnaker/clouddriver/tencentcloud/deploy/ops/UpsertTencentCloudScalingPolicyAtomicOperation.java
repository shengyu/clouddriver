package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudScalingPolicyDescription.OperationType;
import com.netflix.spinnaker.clouddriver.tencentcloud.exception.TencentCloudOperationException;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class UpsertTencentCloudScalingPolicyAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "UPSERT_SCALING_POLICY";

  private AutoScalingClient asClient;
  private UpsertTencentCloudScalingPolicyDescription description;
  @Autowired private TencentCloudClusterProvider tencentCloudClusterProvider;

  public UpsertTencentCloudScalingPolicyAtomicOperation(
      AutoScalingClient asClient, UpsertTencentCloudScalingPolicyDescription description) {
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
            "Initializing upsert scaling policy " + serverGroupName + " in " + region + "...");

    if (description.getOperationType().equals(OperationType.CREATE)) {
      getTask().updateStatus(BASE_PHASE, "create scaling policy in " + serverGroupName + "...");
      String scalingPolicyId = asClient.createScalingPolicy(asgId, description);
      getTask().updateStatus(BASE_PHASE, "new scaling policy " + scalingPolicyId + " is created.");
    } else if (description.getOperationType().equals(OperationType.MODIFY)) {
      String scalingPolicyId = description.getScalingPolicyId();
      getTask()
          .updateStatus(
              BASE_PHASE,
              "update scaling policy " + scalingPolicyId + " in " + serverGroupName + "...");
      asClient.modifyScalingPolicy(scalingPolicyId, description);
    } else {
      throw new TencentCloudOperationException("unknown operation type, operation quit.");
    }

    getTask().updateStatus(BASE_PHASE, "Complete upsert scaling policy.");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
