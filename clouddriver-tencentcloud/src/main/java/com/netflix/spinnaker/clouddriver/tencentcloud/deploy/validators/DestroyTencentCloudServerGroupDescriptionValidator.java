package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.DestroyTencentCloudServerGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component("destroyTencentCloudServerGroupDescriptionValidator")
public class DestroyTencentCloudServerGroupDescriptionValidator
    extends DescriptionValidator<DestroyTencentCloudServerGroupDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      DestroyTencentCloudServerGroupDescription description,
      Errors errors) {
    if (description.getRegion() == null) {
      errors.rejectValue("region", "tencentCloudDestroyServerGroupDescription.region.empty");
    }

    if (description.getServerGroupName() == null) {
      errors.rejectValue(
          "serverGroupName", "tencentCloudDestroyServerGroupDescription.serverGroupName.empty");
    }
  }
}
