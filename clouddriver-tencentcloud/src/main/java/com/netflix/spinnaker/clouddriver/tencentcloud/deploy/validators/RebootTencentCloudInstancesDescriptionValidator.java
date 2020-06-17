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

package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.RebootTencentCloudInstancesDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.REBOOT_INSTANCES)
@Component("rebootTencentCloudInstancesDescriptionValidator")
public class RebootTencentCloudInstancesDescriptionValidator
    extends DescriptionValidator<RebootTencentCloudInstancesDescription> {

  @Override
  public void validate(
      List priorDescriptions, RebootTencentCloudInstancesDescription description, Errors errors) {
    if (description.getRegion() == null) {
      errors.rejectValue("region", "RebootTencentCloudInstancesDescription.region.empty");
    }

    if (CollectionUtils.isEmpty(description.getInstanceIds())) {
      errors.rejectValue("instanceIds", "RebootTencentCloudInstancesDescription.instanceIds.empty");
    }
  }
}
