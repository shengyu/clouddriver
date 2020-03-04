package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.EnableDisableTencentCloudServerGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.ENABLE_SERVER_GROUP)
@Component("enableTencentCloudServerGroupDescriptionValidator")
public class EnableTencentCloudServerGroupDescriptionValidator
    extends DescriptionValidator<EnableDisableTencentCloudServerGroupDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      EnableDisableTencentCloudServerGroupDescription description,
      Errors errors) {
    if (description.getRegion() == null) {
      errors.rejectValue("region", "enableTencentCloudServerGroupDescription.region.empty");
    }

    if (description.getServerGroupName() == null) {
      errors.rejectValue(
          "serverGroupName", "enableTencentCloudServerGroupDescription.serverGroupName.empty");
    }
  }
}
