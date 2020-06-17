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
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudLoadBalancerDescription;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Slf4j
@TencentCloudOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertTencentCloudLoadBalancerDescriptionValidator")
public class UpsertTencentCloudLoadBalancerDescriptionValidator
    extends DescriptionValidator<UpsertTencentCloudLoadBalancerDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      UpsertTencentCloudLoadBalancerDescription description,
      Errors errors) {
    log.info("Enter tencentcloud validate " + description);
    if (description.getApplication() == null) {
      errors.rejectValue(
          "application", "UpsertTencentCloudLoadBalancerDescription.application.empty");
    }

    if (description.getAccountName() == null) {
      errors.rejectValue(
          "accountName", "UpsertTencentCloudLoadBalancerDescription.accountName.empty");
    }

    if (description.getLoadBalancerName() == null) {
      errors.rejectValue(
          "loadBalancerName", "UpsertTencentCloudLoadBalancerDescription.loadBalancerName.empty");
    }

    if (description.getRegion() == null) {
      errors.rejectValue("region", "UpsertTencentCloudLoadBalancerDescription.region.empty");
    }

    if (description.getLoadBalancerType() == null) {
      errors.rejectValue(
          "loadBalancerType", "UpsertTencentCloudLoadBalancerDescription.loadBalancerType.empty");
    }
  }
}
