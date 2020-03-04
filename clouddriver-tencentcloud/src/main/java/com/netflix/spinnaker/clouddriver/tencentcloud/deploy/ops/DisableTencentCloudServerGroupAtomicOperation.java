package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.ops;

import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.EnableDisableTencentCloudServerGroupDescription;

public class DisableTencentCloudServerGroupAtomicOperation
    extends AbstractEnableDisableAtomicOperation {

  private static final String BASE_PHASE = "DISABLE_SERVER_GROUP";

  private static final boolean DISABLE = true;

  public DisableTencentCloudServerGroupAtomicOperation(
      AutoScalingClient asClient, EnableDisableTencentCloudServerGroupDescription description) {
    super(asClient, description);
  }

  public final String getBasePhase() {
    return BASE_PHASE;
  }

  public boolean getDisable() {
    return DISABLE;
  }

  public boolean isDisable() {
    return DISABLE;
  }
}
