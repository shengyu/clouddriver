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

package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.TerminateTencentCloudInstancesDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops.TerminateTencentCloudInstancesAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentCloudOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component("terminateTencentCloudInstancesDescription")
public class TerminateTencentCloudInstancesAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    TerminateTencentCloudInstancesDescription description = convertDescription(input);
    CloudVirtualMachineClient cvmClient =
        new CloudVirtualMachineClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    return new TerminateTencentCloudInstancesAtomicOperation(cvmClient, description);
  }

  @Override
  public TerminateTencentCloudInstancesDescription convertDescription(Map input) {
    TerminateTencentCloudInstancesDescription description =
        getObjectMapper().convertValue(input, TerminateTencentCloudInstancesDescription.class);
    description.setCredentials(getCredentialsObject((String) input.get("credentials")));
    return description;
  }
}
