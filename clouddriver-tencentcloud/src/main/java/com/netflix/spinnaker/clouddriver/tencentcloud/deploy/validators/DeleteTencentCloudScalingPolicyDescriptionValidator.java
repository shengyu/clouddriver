package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.DeleteTencentCloudScalingPolicyDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.DELETE_SCALING_POLICY)
@Component("deleteTencentCloudScalingPolicyDescriptionValidator")
public class DeleteTencentCloudScalingPolicyDescriptionValidator
    extends DescriptionValidator<DeleteTencentCloudScalingPolicyDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      DeleteTencentCloudScalingPolicyDescription description,
      Errors errors) {
    if (description.getRegion() == null) {
      errors.rejectValue("region", "deleteScalingPolicyDescription.region.empty");
    }

    if (description.getServerGroupName() == null) {
      errors.rejectValue("serverGroupName", "deleteScalingPolicyDescription.serverGroupName.empty");
    }

    if (description.getScalingPolicyId() == null) {
      errors.rejectValue("scalingPolicyId", "deleteScalingPolicyDescription.scalingPolicyId.empty");
    }
  }
}
