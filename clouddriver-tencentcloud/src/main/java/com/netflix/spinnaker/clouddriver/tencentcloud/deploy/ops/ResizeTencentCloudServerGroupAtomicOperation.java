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
