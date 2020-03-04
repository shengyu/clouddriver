package com.netflix.spinnaker.clouddriver.tencentcloud.deploy;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudBasicResource;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudCluster;
import com.netflix.spinnaker.clouddriver.tencentcloud.provider.view.TencentCloudClusterProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.security.TencentCloudNamedAccountCredentials;
import com.netflix.spinnaker.moniker.Namer;
import com.tencentcloudapi.as.v20180419.models.AutoScalingGroup;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class TencentCloudServerGroupNameResolver extends AbstractServerGroupNameResolver {

  private static final String TENCENTCLOUD_PHASE = "TENCENTCLOUD_DEPLOY";

  private final String accountName;
  private final String region;
  private final TencentCloudClusterProvider tencentCloudClusterProvider;
  private final AutoScalingClient autoScalingClient;
  private final Namer namer;

  public TencentCloudServerGroupNameResolver(
      String accountName,
      String region,
      TencentCloudClusterProvider tencentCloudClusterProvider,
      TencentCloudNamedAccountCredentials credentials) {
    this.accountName = accountName;
    this.region = region;
    this.tencentCloudClusterProvider = tencentCloudClusterProvider;
    this.namer =
        NamerRegistry.lookup()
            .withProvider(TencentCloudProvider.ID)
            .withAccount(accountName)
            .withResource(TencentCloudBasicResource.class);
    this.autoScalingClient =
        new AutoScalingClient(
            credentials.getCredentials().getSecretId(),
            credentials.getCredentials().getSecretKey(),
            region);
  }

  @Override
  public String getPhase() {
    return TENCENTCLOUD_PHASE;
  }

  @Override
  public String getRegion() {
    return region;
  }

  @Override
  public List<TakenSlot> getTakenSlots(final String clusterName) {
    String applicationName = Names.parseName(clusterName).getApp();
    TencentCloudCluster cluster =
        tencentCloudClusterProvider.getCluster(applicationName, accountName, clusterName);

    if (cluster == null) {
      return new ArrayList<>();
    } else {
      List<AutoScalingGroup> autoScalingGroups = autoScalingClient.getAllAutoScalingGroups();
      List<AutoScalingGroup> serverGroupsInCluster =
          autoScalingGroups.stream()
              .filter(
                  it ->
                      Names.parseName(it.getAutoScalingGroupName())
                          .getCluster()
                          .equals(clusterName))
              .collect(Collectors.toList());

      List<TakenSlot> result = new ArrayList<>();
      for (AutoScalingGroup autoScalingGroup : serverGroupsInCluster) {
        String name = autoScalingGroup.getAutoScalingGroupName();
        Date date = AutoScalingClient.convertToIsoDateTime(autoScalingGroup.getCreatedTime());
        TakenSlot takenSlot = new TakenSlot();
        takenSlot.setServerGroupName(name);
        takenSlot.setSequence(Names.parseName(name).getSequence());
        takenSlot.setCreatedTime(date);
        result.add(takenSlot);
      }
      return result;
    }
  }
}
