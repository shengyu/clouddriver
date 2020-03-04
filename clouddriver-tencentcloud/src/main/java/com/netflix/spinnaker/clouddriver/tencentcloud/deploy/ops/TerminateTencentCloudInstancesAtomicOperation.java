package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.TerminateTencentCloudInstancesDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class TerminateTencentCloudInstancesAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "TERMINATE_INSTANCES";

  @Autowired private TencentCloudClusterProvider tencentCloudClusterProvider;
  private TerminateTencentCloudInstancesDescription description;
  private CloudVirtualMachineClient cvmClient;

  public TerminateTencentCloudInstancesAtomicOperation(
      CloudVirtualMachineClient cvmClient, TerminateTencentCloudInstancesDescription description) {
    this.cvmClient = cvmClient;
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "Initializing termination of instances (%s) in %s:%s...",
                description.getInstanceIds(),
                description.getRegion(),
                description.getServerGroupName()));
    cvmClient.terminateInstances(description.getInstanceIds());
    getTask().updateStatus(BASE_PHASE, "Complete termination of instance.");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
