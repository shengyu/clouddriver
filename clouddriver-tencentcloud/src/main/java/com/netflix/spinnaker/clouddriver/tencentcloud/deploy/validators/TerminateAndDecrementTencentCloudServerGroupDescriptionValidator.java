package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.TerminateAndDecrementTencentCloudServerGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.TERMINATE_INSTANCE_AND_DECREMENT)
@Component("terminateAndDecrementTencentCloudServerGroupDescriptionValidator")
public class TerminateAndDecrementTencentCloudServerGroupDescriptionValidator
    extends DescriptionValidator<TerminateAndDecrementTencentCloudServerGroupDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      TerminateAndDecrementTencentCloudServerGroupDescription description,
      Errors errors) {
    if (description.getRegion() == null) {
      errors.rejectValue(
          "region", "TerminateAndDecrementTencentCloudServerGroupDescription.region.empty");
    }

    if (description.getInstance() == null) {
      errors.rejectValue(
          "instance", "TerminateAndDecrementTencentCloudServerGroupDescription.instance.empty");
    }
  }
}
