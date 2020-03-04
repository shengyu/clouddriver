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
import com.netflix.spinnaker.clouddriver.tencentcloud.client.LoadBalancerClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.DeleteTencentCloudLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops.DeleteTencentCloudLoadBalancerAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentCloudOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteTencentCloudLoadBalancerDescription")
public class DeleteTencentCloudLoadBalancerAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    DeleteTencentCloudLoadBalancerDescription description = convertDescription(input);
    LoadBalancerClient lbClient =
        new LoadBalancerClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    return new DeleteTencentCloudLoadBalancerAtomicOperation(lbClient, description);
  }

  @Override
  public DeleteTencentCloudLoadBalancerDescription convertDescription(Map input) {
    DeleteTencentCloudLoadBalancerDescription description =
        getObjectMapper().convertValue(input, DeleteTencentCloudLoadBalancerDescription.class);
    description.setCredentials(getCredentialsObject((String) input.get("credentials")));
    return description;
  }
}
