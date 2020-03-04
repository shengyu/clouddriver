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

package com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tencentcloudapi.as.v20180419.models.MetricAlarm;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UpsertTencentCloudScalingPolicyDescription
    extends AbstractTencentCloudCredentialsDescription {

  @JsonProperty("credentials")
  private String accountName;

  private String serverGroupName;
  private String region;
  private OperationType operationType;
  private String scalingPolicyId;
  private String adjustmentType;
  private Integer adjustmentValue;
  private MetricAlarm metricAlarm;
  private List<String> notificationUserGroupIds = new ArrayList<>();
  private Integer cooldown;

  public enum OperationType {
    CREATE,
    MODIFY
  }
}
