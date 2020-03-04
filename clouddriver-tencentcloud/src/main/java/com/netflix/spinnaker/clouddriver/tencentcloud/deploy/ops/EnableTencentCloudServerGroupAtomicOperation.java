package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.EnableDisableTencentCloudServerGroupDescription;

public class EnableTencentCloudServerGroupAtomicOperation
    extends AbstractEnableDisableAtomicOperation {

  private static final String BASE_PHASE = "ENABLE_SERVER_GROUP";
  private static final boolean DISABLE = false;

  public EnableTencentCloudServerGroupAtomicOperation(
      AutoScalingClient asClient, EnableDisableTencentCloudServerGroupDescription description) {
    super(asClient, description);
  }

  public final String getBasePhase() {
    return BASE_PHASE;
  }

  public final boolean getDisable() {
    return DISABLE;
  }

  public final boolean isDisable() {
    return DISABLE;
  }
}
