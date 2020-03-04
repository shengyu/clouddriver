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
