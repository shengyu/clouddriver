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
