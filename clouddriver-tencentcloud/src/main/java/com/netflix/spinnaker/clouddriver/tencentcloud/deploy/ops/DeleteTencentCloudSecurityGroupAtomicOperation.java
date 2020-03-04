package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.VirtualPrivateCloudClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.DeleteTencentCloudSecurityGroupDescription;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeleteTencentCloudSecurityGroupAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_SECURITY_GROUP";

  private DeleteTencentCloudSecurityGroupDescription description;
  private VirtualPrivateCloudClient vpcClient;

  public DeleteTencentCloudSecurityGroupAtomicOperation(
      VirtualPrivateCloudClient vpcClient, DeleteTencentCloudSecurityGroupDescription description) {
    this.vpcClient = vpcClient;
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing delete of Tencent Cloud securityGroup "
                + description.getSecurityGroupId()
                + " in "
                + description.getRegion()
                + "...");
    String securityGroupId = description.getSecurityGroupId();
    getTask().updateStatus(BASE_PHASE, "Start delete securityGroup " + securityGroupId + " ...");
    vpcClient.deleteSecurityGroup(securityGroupId);
    getTask().updateStatus(BASE_PHASE, "Delete securityGroup " + securityGroupId + " end");

    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
