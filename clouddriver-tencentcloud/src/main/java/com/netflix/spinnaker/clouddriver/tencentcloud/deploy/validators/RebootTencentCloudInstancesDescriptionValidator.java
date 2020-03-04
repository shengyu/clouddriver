package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.RebootTencentCloudInstancesDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.REBOOT_INSTANCES)
@Component("rebootTencentCloudInstancesDescriptionValidator")
public class RebootTencentCloudInstancesDescriptionValidator
    extends DescriptionValidator<RebootTencentCloudInstancesDescription> {

  @Override
  public void validate(
      List priorDescriptions, RebootTencentCloudInstancesDescription description, Errors errors) {
    if (description.getRegion() == null) {
      errors.rejectValue("region", "RebootTencentCloudInstancesDescription.region.empty");
    }

    if (CollectionUtils.isEmpty(description.getInstanceIds())) {
      errors.rejectValue("instanceIds", "RebootTencentCloudInstancesDescription.instanceIds.empty");
    }
  }
}
