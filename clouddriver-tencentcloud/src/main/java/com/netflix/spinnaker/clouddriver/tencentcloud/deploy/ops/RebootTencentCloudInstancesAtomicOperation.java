package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.RebootTencentCloudInstancesDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class RebootTencentCloudInstancesAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "REBOOT_INSTANCES";

  private CloudVirtualMachineClient cvmClient;
  private RebootTencentCloudInstancesDescription description;
  @Autowired private TencentCloudClusterProvider tencentCloudClusterProvider;

  public RebootTencentCloudInstancesAtomicOperation(
      CloudVirtualMachineClient cvmClient, RebootTencentCloudInstancesDescription description) {
    this.cvmClient = cvmClient;
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing reboot of instances ("
                + description.getInstanceIds()
                + ") in "
                + description.getRegion()
                + ":"
                + description.getServerGroupName()
                + "...");
    cvmClient.rebootInstances(description.getInstanceIds());
    getTask().updateStatus(BASE_PHASE, "Complete reboot of instance.");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
