/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
