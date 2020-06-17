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
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.TencentCloudDeployDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("tencentCloudDeployDescriptionValidator")
public class TencentCloudDeployDescriptionValidator
    extends DescriptionValidator<TencentCloudDeployDescription> {

  @Override
  public void validate(
      List priorDescriptions, TencentCloudDeployDescription description, Errors errors) {

    if (description.getApplication() == null) {
      errors.rejectValue("application", "tencentCloudDeployDescription.application.empty");
    }

    if (description.getImageId() == null) {
      errors.rejectValue("imageId", "tencentCloudDeployDescription.imageId.empty");
    }

    if (description.getInstanceType() == null) {
      errors.rejectValue("instanceType", "tencentCloudDeployDescription.instanceType.empty");
    }

    if (CollectionUtils.isEmpty(description.getZones())
        && CollectionUtils.isEmpty(description.getSubnetIds())) {
      errors.rejectValue(
          "zones or subnetIds", "tencentCloudDeployDescription.subnetIds.or.zones.not.supplied");
    }

    if (description.getMaxSize() == null) {
      errors.rejectValue("maxSize", "tencentCloudDeployDescription.maxSize.empty");
    }

    if (description.getMinSize() == null) {
      errors.rejectValue("minSize", "tencentCloudDeployDescription.minSize.empty");
    }

    if (description.getDesiredCapacity() == null) {
      errors.rejectValue("desiredCapacity", "tencentCloudDeployDescription.desiredCapacity.empty");
    }
  }
}
