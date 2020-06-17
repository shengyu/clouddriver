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
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.DeleteTencentCloudScheduledActionDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component("deleteTencentCloudScheduledActionDescriptionValidator")
public class DeleteTencentCloudScheduledActionDescriptionValidator
    extends DescriptionValidator<DeleteTencentCloudScheduledActionDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      DeleteTencentCloudScheduledActionDescription description,
      Errors errors) {
    if (description.getRegion() == null) {
      errors.rejectValue("region", "deleteScheduledActionDescription.region.empty");
    }

    if (description.getServerGroupName() == null) {
      errors.rejectValue(
          "serverGroupName", "deleteScheduledActionDescription.serverGroupName.empty");
    }

    if (description.getScheduledActionId() == null) {
      errors.rejectValue(
          "scheduledActionId", "deleteScheduledActionDescription.scalingPolicyId.empty");
    }
  }
}
