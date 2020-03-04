package com.netflix.spinnaker.clouddriver.tencentcloud.client;

import static java.lang.Thread.sleep;

import com.netflix.spinnaker.clouddriver.tencentcloud.exception.TencentCloudOperationException;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.cvm.v20170312.CvmClient;
import com.tencentcloudapi.cvm.v20170312.models.DescribeImagesRequest;
import com.tencentcloudapi.cvm.v20170312.models.DescribeImagesResponse;
import com.tencentcloudapi.cvm.v20170312.models.DescribeInstanceTypeConfigsRequest;
import com.tencentcloudapi.cvm.v20170312.models.DescribeInstanceTypeConfigsResponse;
import com.tencentcloudapi.cvm.v20170312.models.DescribeInstancesRequest;
import com.tencentcloudapi.cvm.v20170312.models.DescribeInstancesResponse;
import com.tencentcloudapi.cvm.v20170312.models.DescribeKeyPairsRequest;
import com.tencentcloudapi.cvm.v20170312.models.DescribeKeyPairsResponse;
import com.tencentcloudapi.cvm.v20170312.models.Image;
import com.tencentcloudapi.cvm.v20170312.models.Instance;
import com.tencentcloudapi.cvm.v20170312.models.InstanceTypeConfig;
import com.tencentcloudapi.cvm.v20170312.models.KeyPair;
import com.tencentcloudapi.cvm.v20170312.models.RebootInstancesRequest;
import com.tencentcloudapi.cvm.v20170312.models.TerminateInstancesRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

@Slf4j
public class CloudVirtualMachineClient extends AbstractTencentCloudServiceClient {

  private static final String END_POINT = "cvm.tencentcloudapi.com";
  private CvmClient client;

  public CloudVirtualMachineClient(String secretId, String secretKey, String region) {
    super(secretId, secretKey);
    client = new CvmClient(getCredential(), region, getClientProfile());
  }

  public void terminateInstances(List<String> instanceIds) {
    try {
      TerminateInstancesRequest request = new TerminateInstancesRequest();
      int len = instanceIds.size();
      for (int i = 0; i < len; i += DEFAULT_LIMIT) {
        int endIndex = Math.min(len, i + DEFAULT_LIMIT);
        request.setInstanceIds(instanceIds.subList(i, endIndex).toArray(new String[0]));
        client.TerminateInstances(request);
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void rebootInstances(List<String> instanceIds) {
    try {
      RebootInstancesRequest request = new RebootInstancesRequest();
      int len = instanceIds.size();
      for (int i = 0; i < len; i += DEFAULT_LIMIT) {
        int endIndex = Math.min(len, i + DEFAULT_LIMIT);
        request.setInstanceIds(instanceIds.subList(i, endIndex).toArray(new String[0]));
        client.RebootInstances(request);
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public InstanceTypeConfig[] getInstanceTypes() {
    try {
      DescribeInstanceTypeConfigsRequest request = new DescribeInstanceTypeConfigsRequest();
      DescribeInstanceTypeConfigsResponse response = client.DescribeInstanceTypeConfigs(request);
      return response.getInstanceTypeConfigSet();
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<KeyPair> getKeyPairs() {
    List<KeyPair> result = new ArrayList<>();
    DescribeKeyPairsRequest request = new DescribeKeyPairsRequest();
    try {
      int offset = 0;
      int queryIndex = 0;
      while (queryIndex++ < MAX_QUERY_TIME) {
        request.setOffset(offset);
        request.setLimit(DEFAULT_LIMIT);
        DescribeKeyPairsResponse response = client.DescribeKeyPairs(request);

        if (response == null
            || response.getKeyPairSet() == null
            || response.getKeyPairSet().length <= 0) {
          break;
        }
        result.addAll(Arrays.asList(response.getKeyPairSet()));
        offset += DEFAULT_LIMIT;
        if (result.size() == response.getTotalCount()) {
          break;
        }
        sleep(500);
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }
    return result;
  }

  public List<Image> getImages() {
    List<Image> result = new ArrayList<>();
    DescribeImagesRequest request = new DescribeImagesRequest();
    try {
      int offset = 0;
      int queryIndex = 0;
      while (queryIndex++ < MAX_QUERY_TIME) {
        request.setOffset(offset);
        request.setLimit(DEFAULT_LIMIT);
        DescribeImagesResponse response = client.DescribeImages(request);

        if (response == null
            || response.getImageSet() == null
            || response.getImageSet().length <= 0) {
          break;
        }
        result.addAll(Arrays.asList(response.getImageSet()));
        offset += DEFAULT_LIMIT;
        if (result.size() == response.getTotalCount()) {
          break;
        }
        sleep(500);
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }
    return result;
  }

  public List<Instance> getInstances(List<String> instanceIds) {
    List<Instance> result = new ArrayList<>();
    DescribeInstancesRequest request = new DescribeInstancesRequest();
    try {
      int offset = 0;
      int queryIndex = 0;
      while (queryIndex++ < MAX_QUERY_TIME) {
        request.setOffset(offset);
        request.setLimit(DEFAULT_LIMIT);

        if (!CollectionUtils.isEmpty(instanceIds)) {
          int end = Math.min(offset + DEFAULT_LIMIT, instanceIds.size());
          if (offset < end) {
            request.setInstanceIds(instanceIds.subList(offset, end).toArray(new String[0]));
          }
        }
        DescribeInstancesResponse response = client.DescribeInstances(request);
        if (response == null
            || response.getInstanceSet() == null
            || response.getInstanceSet().length <= 0) {
          break;
        }
        result.addAll(Arrays.asList(response.getInstanceSet()));
        offset += DEFAULT_LIMIT;
        if (result.size() == response.getTotalCount()) {
          break;
        }
        sleep(500);
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }

    return result;
  }

  public final String getEndPoint() {
    return END_POINT;
  }
}
