package com.netflix.spinnaker.clouddriver.tencentcloud.controllers;

import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudServerGroup;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudClusterProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.tencentcloudapi.as.v20180419.models.Activity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    "/applications/{application}/clusters/{account}/{clusterName}/tencentcloud/serverGroups/{serverGroupName}")
public class TencentCloudServerGroupController {

  public static final int MAX_SCALING_ACTIVITIES = 500;

  @Autowired private AccountCredentialsProvider accountCredentialsProvider;
  @Autowired private TencentCloudClusterProvider tencentCloudClusterProvider;

  @RequestMapping(value = "/scalingActivities", method = RequestMethod.GET)
  public ResponseEntity getScalingActivities(
      @PathVariable String account,
      @PathVariable String serverGroupName,
      @RequestParam(value = "region") String region) {
    AccountCredentials credentials = accountCredentialsProvider.getCredentials(account);
    if (!(credentials instanceof TencentCloudNamedAccountCredentials)) {
      Map<String, String> map = new HashMap<>();
      map.put("message", account + " is not tencent cloud credential type");
      return new ResponseEntity(map, HttpStatus.BAD_REQUEST);
    }

    TencentCloudServerGroup serverGroup =
        tencentCloudClusterProvider.getServerGroup(account, region, serverGroupName, false);

    String autoScalingGroupId = serverGroup.getAsg().getAutoScalingGroupId();
    AutoScalingClient client =
        new AutoScalingClient(
            ((TencentCloudNamedAccountCredentials) credentials).getCredentials().getSecretId(),
            ((TencentCloudNamedAccountCredentials) credentials).getCredentials().getSecretKey(),
            region);
    List<Activity> scalingActivities =
        client.getAutoScalingActivitiesByAsgId(autoScalingGroupId, MAX_SCALING_ACTIVITIES);
    return new ResponseEntity(scalingActivities, HttpStatus.OK);
  }
}
