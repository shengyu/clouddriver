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

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.VirtualPrivateCloudClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudSecurityGroupRule;
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroupPolicy;
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroupPolicySet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class UpsertTencentCloudSecurityGroupAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "UPSERT_SECURITY_GROUP";

  private VirtualPrivateCloudClient vpcClient;
  private UpsertTencentCloudSecurityGroupDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public UpsertTencentCloudSecurityGroupAtomicOperation(
      VirtualPrivateCloudClient vpcClient, UpsertTencentCloudSecurityGroupDescription description) {
    this.vpcClient = vpcClient;
    this.description = description;
  }

  @Override
  public Map operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing upsert of Tencent Cloud securityGroup "
                + description.getSecurityGroupName()
                + " in "
                + description.getRegion()
                + "...");
    log.info("params = " + description);

    String securityGroupId = description.getSecurityGroupId();
    if (StringUtils.isEmpty(securityGroupId)) {
      insertSecurityGroup(description);
    } else {
      updateSecurityGroup(description);
    }

    log.info(
        "upsert securityGroup name:"
            + description.getSecurityGroupName()
            + ", id:"
            + description.getSecurityGroupId());

    Map<String, Map<String, Map<String, String>>> map = new HashMap<>();
    Map<String, Map<String, String>> map1 = new HashMap<>();
    Map<String, String> map2 = new HashMap<>();
    map2.put("name", description.getSecurityGroupName());
    map2.put("id", description.getSecurityGroupId());
    map1.put(description.getRegion(), map2);
    map.put("securityGroups", map1);
    return map;
  }

  private String updateSecurityGroup(final UpsertTencentCloudSecurityGroupDescription description) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start update securityGroup "
                + description.getSecurityGroupName()
                + " "
                + description.getSecurityGroupId()
                + " ...");
    String securityGroupId = description.getSecurityGroupId();
    SecurityGroupPolicySet oldGroupRules = vpcClient.getSecurityGroupPolicies(securityGroupId);
    List<TencentCloudSecurityGroupRule> newGroupInRules = description.getInRules();

    // del in rules
    List<TencentCloudSecurityGroupRule> delGroupInRules = new ArrayList<>();
    for (SecurityGroupPolicy ingress : oldGroupRules.getIngress()) {
      TencentCloudSecurityGroupRule keepRule =
          newGroupInRules.stream()
              .filter(itr -> itr.getIndex().equals(ingress.getPolicyIndex()))
              .findAny()
              .orElse(null);
      if (keepRule == null) {
        TencentCloudSecurityGroupRule delInRule =
            TencentCloudSecurityGroupRule.builder()
                .index(ingress.getPolicyIndex())
                .action(ingress.getAction())
                .cidrBlock(ingress.getCidrBlock())
                .port(ingress.getPort())
                .protocol(ingress.getProtocol())
                .build();
        delGroupInRules.add(delInRule);
      }
    }
    if (!delGroupInRules.isEmpty()) {
      getTask()
          .updateStatus(BASE_PHASE, "Start delete securityGroup " + securityGroupId + " rules ...");
      vpcClient.deleteSecurityGroupInRules(securityGroupId, delGroupInRules);
      getTask().updateStatus(BASE_PHASE, "delete securityGroup " + securityGroupId + " rules end");
    }

    // add in rules
    List<TencentCloudSecurityGroupRule> addGroupInRules = new ArrayList<>();
    for (TencentCloudSecurityGroupRule rule : newGroupInRules) {
      if (rule.getIndex() == null) {
        addGroupInRules.add(rule);
      }
    }
    if (!addGroupInRules.isEmpty()) {
      getTask()
          .updateStatus(BASE_PHASE, "Start add securityGroup " + securityGroupId + " rules ...");
      vpcClient.createSecurityGroupRules(securityGroupId, addGroupInRules, null);
      getTask().updateStatus(BASE_PHASE, "add securityGroup " + securityGroupId + " rules end");
    }

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Update securityGroup "
                + description.getSecurityGroupName()
                + " "
                + description.getSecurityGroupId()
                + " end");
    return "";
  }

  private void insertSecurityGroup(final UpsertTencentCloudSecurityGroupDescription description) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start create new securityGroup " + description.getSecurityGroupName() + " ...");

    String securityGroupId =
        vpcClient.createSecurityGroup(
            description.getSecurityGroupName(), description.getSecurityGroupDesc());
    description.setSecurityGroupId(securityGroupId);

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Create new securityGroup "
                + description.getSecurityGroupName()
                + " success, id is "
                + securityGroupId
                + ".");

    if (description.getInRules().size() > 0) {
      getTask()
          .updateStatus(
              BASE_PHASE, "Start create new securityGroup rules in " + securityGroupId + " ...");
      vpcClient.createSecurityGroupRules(
          securityGroupId, description.getInRules(), description.getOutRules());
      getTask()
          .updateStatus(
              BASE_PHASE, "Create new securityGroup rules in " + securityGroupId + " end");
    }
  }
}
