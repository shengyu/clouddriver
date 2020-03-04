package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.DeleteTencentCloudSecurityGroupDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.DELETE_SECURITY_GROUP)
@Component("deleteTencentCloudSecurityGroupDescriptionValidator")
public class DeleteTencentCloudSecurityGroupDescriptionValidator
    extends DescriptionValidator<DeleteTencentCloudSecurityGroupDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      DeleteTencentCloudSecurityGroupDescription description,
      Errors errors) {
    if (description.getSecurityGroupId() == null) {
      errors.rejectValue(
          "securityGroupId", "DeleteTencentCloudSecurityGroupDescription.securityGroupId.empty");
    }

    if (description.getAccountName() == null) {
      errors.rejectValue(
          "accountName", "DeleteTencentCloudSecurityGroupDescription.accountName.empty");
    }

    if (description.getRegion() == null) {
      errors.rejectValue("region", "DeleteTencentCloudSecurityGroupDescription.region.empty");
    }
  }
}
