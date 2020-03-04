package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudScalingPolicyDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.UPSERT_SCALING_POLICY)
@Component("upsertTencentCloudScalingPolicyDescriptionValidator")
public class UpsertTencentCloudScalingPolicyDescriptionValidator
    extends DescriptionValidator<UpsertTencentCloudScalingPolicyDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      UpsertTencentCloudScalingPolicyDescription description,
      Errors errors) {
    if (description.getRegion() == null) {
      errors.rejectValue("region", "upsertScalingPolicyDescription.region.empty");
    }

    if (description.getServerGroupName() == null) {
      errors.rejectValue("serverGroupName", "upsertScalingPolicyDescription.serverGroupName.empty");
    }
  }
}
