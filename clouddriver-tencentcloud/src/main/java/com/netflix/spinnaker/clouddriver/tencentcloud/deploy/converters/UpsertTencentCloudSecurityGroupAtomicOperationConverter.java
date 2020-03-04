package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.VirtualPrivateCloudClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops.UpsertTencentCloudSecurityGroupAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@TencentCloudOperation(AtomicOperations.UPSERT_SECURITY_GROUP)
@Component("upsertTencentCloudSecurityGroupDescription")
public class UpsertTencentCloudSecurityGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    UpsertTencentCloudSecurityGroupDescription description = convertDescription(input);
    VirtualPrivateCloudClient vpcClient =
        new VirtualPrivateCloudClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    return new UpsertTencentCloudSecurityGroupAtomicOperation(vpcClient, description);
  }

  @Override
  public UpsertTencentCloudSecurityGroupDescription convertDescription(Map input) {
    UpsertTencentCloudSecurityGroupDescription description =
        getObjectMapper().convertValue(input, UpsertTencentCloudSecurityGroupDescription.class);
    description.setCredentials(getCredentialsObject((String) input.get("credentials")));
    return description;
  }
}
