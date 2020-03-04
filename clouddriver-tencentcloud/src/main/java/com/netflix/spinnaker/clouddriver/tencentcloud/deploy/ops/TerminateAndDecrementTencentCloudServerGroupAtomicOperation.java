package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.TerminateAndDecrementTencentCloudServerGroupDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudClusterProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class TerminateAndDecrementTencentCloudServerGroupAtomicOperation
    implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "TERMINATE_AND_DEC_INSTANCES";

  @Autowired private TencentCloudClusterProvider tencentCloudClusterProvider;
  private TerminateAndDecrementTencentCloudServerGroupDescription description;
  private AutoScalingClient asClient;

  public TerminateAndDecrementTencentCloudServerGroupAtomicOperation(
      AutoScalingClient asClient,
      TerminateAndDecrementTencentCloudServerGroupDescription description) {
    this.asClient = asClient;
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String region = description.getRegion();
    String serverGroupName = description.getServerGroupName();
    List<String> instanceIds = new ArrayList<String>(Arrays.asList(description.getInstance()));
    String accountName = description.getCredentials().getName();

    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "Initializing termination of instance (%s) "
                    + "in %s:%s and decrease server group desired capacity...",
                description.getInstance(), description.getRegion(), serverGroupName));

    String asgId =
        tencentCloudClusterProvider.getServerGroupAsgId(serverGroupName, accountName, region);
    asClient.removeInstances(asgId, instanceIds);
    getTask()
        .updateStatus(
            BASE_PHASE, "Complete terminate instance and decrease server group desired capacity.");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
