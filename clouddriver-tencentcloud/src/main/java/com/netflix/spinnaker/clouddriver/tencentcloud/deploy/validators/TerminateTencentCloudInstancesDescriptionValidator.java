package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.TerminateTencentCloudInstancesDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component("terminateTencentCloudInstancesDescriptionValidator")
public class TerminateTencentCloudInstancesDescriptionValidator
    extends DescriptionValidator<TerminateTencentCloudInstancesDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      TerminateTencentCloudInstancesDescription description,
      Errors errors) {
    if (description.getRegion() == null) {
      errors.rejectValue("region", "TerminateTencentCloudInstancesDescription.region.empty");
    }

    if (CollectionUtils.isEmpty(description.getInstanceIds())) {
      errors.rejectValue(
          "instanceIds", "TerminateTencentCloudInstancesDescription.instanceIds.empty");
    }
  }
}
