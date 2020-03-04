package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudSecurityGroupDescription;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Slf4j
@TencentCloudOperation(AtomicOperations.UPSERT_SECURITY_GROUP)
@Component("upsertTencentCloudSecurityGroupDescriptionValidator")
public class UpsertTencentCloudSecurityGroupDescriptionValidator
    extends DescriptionValidator<UpsertTencentCloudSecurityGroupDescription> {

  @Override
  public void validate(
      List priorDescriptions,
      final UpsertTencentCloudSecurityGroupDescription description,
      Errors errors) {
    log.info("Validate tencentcloud security group description " + description);
    if (description.getSecurityGroupName() == null) {
      errors.rejectValue(
          "securityGroupName",
          "UpsertTencentCloudSecurityGroupDescription.securityGroupName.empty");
    }

    if (description.getAccountName() == null) {
      errors.rejectValue(
          "accountName", "UpsertTencentCloudSecurityGroupDescription.accountName.empty");
    }

    if (description.getRegion() == null) {
      errors.rejectValue("region", "UpsertTencentCloudSecurityGroupDescription.region.empty");
    }
  }
}
