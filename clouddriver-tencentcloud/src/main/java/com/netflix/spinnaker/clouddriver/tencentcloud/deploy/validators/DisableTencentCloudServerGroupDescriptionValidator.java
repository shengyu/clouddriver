package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.EnableDisableTencentCloudServerGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.DISABLE_SERVER_GROUP)
@Component("disableTencentCloudServerGroupDescriptionValidator")
public class DisableTencentCloudServerGroupDescriptionValidator
    extends DescriptionValidator<EnableDisableTencentCloudServerGroupDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      EnableDisableTencentCloudServerGroupDescription description,
      Errors errors) {
    if (description.getRegion() == null) {
      errors.rejectValue("region", "disableTencentCloudServerGroupDescription.region.empty");
    }

    if (description.getServerGroupName() == null) {
      errors.rejectValue(
          "serverGroupName", "disableTencentCloudServerGroupDescription.serverGroupName.empty");
    }
  }
}
