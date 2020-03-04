package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.DeleteTencentCloudLoadBalancerDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteTencentCloudLoadBalancerDescriptionValidator")
public class DeleteTencentCloudLoadBalancerDescriptionValidator
    extends DescriptionValidator<DeleteTencentCloudLoadBalancerDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      DeleteTencentCloudLoadBalancerDescription description,
      Errors errors) {

    if (description.getApplication() == null) {
      errors.rejectValue(
          "application", "DeleteTencentCloudLoadBalancerDescription.application.empty");
    }

    if (description.getRegion() == null) {
      errors.rejectValue("region", "UpsertTencentCloudLoadBalancerDescription.region.empty");
    }

    if (description.getLoadBalancerId() == null) {
      errors.rejectValue(
          "loadBalancerId", "DeleteTencentCloudLoadBalancerDescription.loadBalancerId.empty");
    }
  }
}
