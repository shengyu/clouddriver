package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.ResizeTencentCloudServerGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component("resizeTencentCloudServerGroupDescriptionValidator")
public class ResizeTencentCloudServerGroupDescriptionValidator
    extends DescriptionValidator<ResizeTencentCloudServerGroupDescription> {

  @Override
  public void validate(
      List priorDescriptions, ResizeTencentCloudServerGroupDescription description, Errors errors) {
    if (description.getRegion() == null) {
      errors.rejectValue("region", "ResizeTencentCloudServerGroupDescription.region.empty");
    }

    if (description.getServerGroupName() == null) {
      errors.rejectValue(
          "serverGroupName", "ResizeTencentCloudServerGroupDescription.serverGroupName.empty");
    }

    if (description.getCapacity() == null) {
      errors.rejectValue("capacity", "ResizeTencentCloudServerGroupDescription.capacity.empty");
    }
  }
}
