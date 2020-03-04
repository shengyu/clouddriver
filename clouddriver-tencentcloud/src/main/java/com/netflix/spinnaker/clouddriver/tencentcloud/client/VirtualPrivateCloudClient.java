package com.netflix.spinnaker.clouddriver.tencentcloud.client;

import com.netflix.spinnaker.clouddriver.tencentcloud.exception.TencentCloudOperationException;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudSecurityGroupRule;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.vpc.v20170312.VpcClient;
import com.tencentcloudapi.vpc.v20170312.models.CreateSecurityGroupPoliciesRequest;
import com.tencentcloudapi.vpc.v20170312.models.CreateSecurityGroupRequest;
import com.tencentcloudapi.vpc.v20170312.models.CreateSecurityGroupResponse;
import com.tencentcloudapi.vpc.v20170312.models.DeleteSecurityGroupPoliciesRequest;
import com.tencentcloudapi.vpc.v20170312.models.DeleteSecurityGroupRequest;
import com.tencentcloudapi.vpc.v20170312.models.DescribeSecurityGroupPoliciesRequest;
import com.tencentcloudapi.vpc.v20170312.models.DescribeSecurityGroupPoliciesResponse;
import com.tencentcloudapi.vpc.v20170312.models.DescribeSecurityGroupsRequest;
import com.tencentcloudapi.vpc.v20170312.models.DescribeSecurityGroupsResponse;
import com.tencentcloudapi.vpc.v20170312.models.DescribeSubnetsRequest;
import com.tencentcloudapi.vpc.v20170312.models.DescribeSubnetsResponse;
import com.tencentcloudapi.vpc.v20170312.models.DescribeVpcsRequest;
import com.tencentcloudapi.vpc.v20170312.models.DescribeVpcsResponse;
import com.tencentcloudapi.vpc.v20170312.models.Filter;
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroup;
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroupPolicy;
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroupPolicySet;
import com.tencentcloudapi.vpc.v20170312.models.Subnet;
import com.tencentcloudapi.vpc.v20170312.models.Vpc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VirtualPrivateCloudClient {

  private static final int DEFAULT_LIMIT = 100;
  private static final String DEFAULT_LIMIT_STR = "100";
  private VpcClient client;

  public VirtualPrivateCloudClient(String secretId, String secretKey, String region) {
    this.client = new VpcClient(new Credential(secretId, secretKey), region);
  }

  public String createSecurityGroup(String groupName, String groupDesc) {
    try {
      CreateSecurityGroupRequest req = new CreateSecurityGroupRequest();
      req.setGroupName(groupName);
      if (groupDesc == null) {
        groupDesc = "spinnaker create";
      }

      req.setGroupDescription(groupDesc);
      CreateSecurityGroupResponse resp = client.CreateSecurityGroup(req);
      return resp.getSecurityGroup().getSecurityGroupId();
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void createSecurityGroupRules(
      String groupId,
      List<TencentCloudSecurityGroupRule> inRules,
      List<TencentCloudSecurityGroupRule> outRules) {
    try {
      CreateSecurityGroupPoliciesRequest req = new CreateSecurityGroupPoliciesRequest();
      req.setSecurityGroupId(groupId);
      if (inRules.size() > 0) {
        List<SecurityGroupPolicy> ingressList = new ArrayList<>();
        for (TencentCloudSecurityGroupRule rule : inRules) {
          SecurityGroupPolicy ingress = new SecurityGroupPolicy();
          ingress.setProtocol(rule.getProtocol());
          if (!ingress.getProtocol().equalsIgnoreCase("ICMP")) { // ICMP not port
            ingress.setPort(rule.getPort());
          }

          ingress.setAction(rule.getAction());
          ingress.setCidrBlock(rule.getCidrBlock());
          ingressList.add(ingress);
        }
        SecurityGroupPolicySet policySet = new SecurityGroupPolicySet();
        policySet.setIngress(ingressList.toArray(new SecurityGroupPolicy[0]));
        req.setSecurityGroupPolicySet(policySet);
      }
      client.CreateSecurityGroupPolicies(req);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void deleteSecurityGroupInRules(
      String groupId, List<TencentCloudSecurityGroupRule> inRules) {
    try {
      DeleteSecurityGroupPoliciesRequest req = new DeleteSecurityGroupPoliciesRequest();
      req.setSecurityGroupId(groupId);
      if (inRules.size() > 0) {
        List<SecurityGroupPolicy> ingressList = new ArrayList<>();
        for (TencentCloudSecurityGroupRule rule : inRules) {
          SecurityGroupPolicy ingress = new SecurityGroupPolicy();
          ingress.setProtocol(rule.getProtocol());
          if (!ingress.getProtocol().equalsIgnoreCase("ICMP")) { // ICMP not port
            ingress.setPort(rule.getPort());
          }

          ingress.setAction(rule.getAction());
          ingress.setCidrBlock(rule.getCidrBlock());
          ingressList.add(ingress);
        }
        SecurityGroupPolicySet policySet = new SecurityGroupPolicySet();
        policySet.setIngress(ingressList.toArray(new SecurityGroupPolicy[0]));
        req.setSecurityGroupPolicySet(policySet);
      }

      client.DeleteSecurityGroupPolicies(req);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<SecurityGroup> getSecurityGroupsAll() {
    try {
      DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest();
      req.setLimit(DEFAULT_LIMIT_STR);
      DescribeSecurityGroupsResponse resp = client.DescribeSecurityGroups(req);
      List<SecurityGroup> securityGroupAll =
          new ArrayList<>(Arrays.asList(resp.getSecurityGroupSet()));
      int totalCount = resp.getTotalCount();
      int counter = DEFAULT_LIMIT;
      while (totalCount > counter) {
        req.setOffset(String.valueOf(counter));
        DescribeSecurityGroupsResponse respMore = client.DescribeSecurityGroups(req);
        securityGroupAll.addAll(Arrays.asList(respMore.getSecurityGroupSet()));
        counter += respMore.getSecurityGroupSet().length;
      }
      return securityGroupAll;
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<SecurityGroup> getSecurityGroupById(String securityGroupId) {
    try {
      DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest();
      req.setSecurityGroupIds(new String[] {securityGroupId});
      DescribeSecurityGroupsResponse resp = client.DescribeSecurityGroups(req);
      return Arrays.asList(resp.getSecurityGroupSet());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<SecurityGroup> getSecurityGroupByName(String securityGroupName) {
    try {
      DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest();

      Filter filter = new Filter();
      filter.setName("security-group-name");
      filter.setValues(new String[] {securityGroupName});
      req.setFilters(new Filter[] {filter});

      DescribeSecurityGroupsResponse resp = client.DescribeSecurityGroups(req);
      return Arrays.asList(resp.getSecurityGroupSet());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void deleteSecurityGroup(String securityGroupId) {
    try {
      DeleteSecurityGroupRequest req = new DeleteSecurityGroupRequest();
      req.setSecurityGroupId(securityGroupId);
      client.DeleteSecurityGroup(req);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public SecurityGroupPolicySet getSecurityGroupPolicies(String securityGroupId) {
    try {
      DescribeSecurityGroupPoliciesRequest req = new DescribeSecurityGroupPoliciesRequest();
      req.setSecurityGroupId(securityGroupId);
      DescribeSecurityGroupPoliciesResponse resp = client.DescribeSecurityGroupPolicies(req);
      return resp.getSecurityGroupPolicySet();
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<Vpc> getNetworksAll() {
    try {
      DescribeVpcsRequest req = new DescribeVpcsRequest();
      req.setLimit(DEFAULT_LIMIT_STR);
      DescribeVpcsResponse resp = client.DescribeVpcs(req);
      List<Vpc> networkAll = new ArrayList<>(Arrays.asList(resp.getVpcSet()));
      int totalCount = resp.getTotalCount();
      int counter = DEFAULT_LIMIT;
      while (totalCount > counter) {
        req.setOffset(String.valueOf(counter));
        DescribeVpcsResponse respMore = client.DescribeVpcs(req);
        networkAll.addAll(Arrays.asList(respMore.getVpcSet()));
        counter += respMore.getVpcSet().length;
      }

      return networkAll;
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<Subnet> getSubnetsAll() {
    try {
      DescribeSubnetsRequest req = new DescribeSubnetsRequest();
      req.setLimit(DEFAULT_LIMIT_STR);
      DescribeSubnetsResponse resp = client.DescribeSubnets(req);
      List<Subnet> subnetAll = new ArrayList<>(Arrays.asList(resp.getSubnetSet()));
      int totalCount = resp.getTotalCount();
      int counter = DEFAULT_LIMIT;
      while (totalCount > counter) {
        req.setOffset(String.valueOf(counter));
        DescribeSubnetsResponse respMore = client.DescribeSubnets(req);
        subnetAll.addAll(Arrays.asList(respMore.getSubnetSet()));
        counter += respMore.getSubnetSet().length;
      }

      return subnetAll;
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public VpcClient getClient() {
    return client;
  }

  public void setClient(VpcClient client) {
    this.client = client;
  }
}
