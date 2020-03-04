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
