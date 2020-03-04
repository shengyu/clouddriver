package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.validators;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.TencentCloudDeployDescription;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.Errors;

@TencentCloudOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("tencentCloudDeployDescriptionValidator")
public class TencentCloudDeployDescriptionValidator
    extends DescriptionValidator<TencentCloudDeployDescription> {

  @Override
  public void validate(
      List priorDescriptions, TencentCloudDeployDescription description, Errors errors) {

    if (description.getApplication() == null) {
      errors.rejectValue("application", "tencentCloudDeployDescription.application.empty");
    }

    if (description.getImageId() == null) {
      errors.rejectValue("imageId", "tencentCloudDeployDescription.imageId.empty");
    }

    if (description.getInstanceType() == null) {
      errors.rejectValue("instanceType", "tencentCloudDeployDescription.instanceType.empty");
    }

    if (CollectionUtils.isEmpty(description.getZones())
        && CollectionUtils.isEmpty(description.getSubnetIds())) {
      errors.rejectValue(
          "zones or subnetIds", "tencentCloudDeployDescription.subnetIds.or.zones.not.supplied");
    }

    if (description.getMaxSize() == null) {
      errors.rejectValue("maxSize", "tencentCloudDeployDescription.maxSize.empty");
    }

    if (description.getMinSize() == null) {
      errors.rejectValue("minSize", "tencentCloudDeployDescription.minSize.empty");
    }

    if (description.getDesiredCapacity() == null) {
      errors.rejectValue("desiredCapacity", "tencentCloudDeployDescription.desiredCapacity.empty");
    }
  }
}
