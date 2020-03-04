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

package com.netflix.spinnaker.clouddriver.tencentcloud.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.model.Image;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
public class TencentCloudImage implements Image {

  private String name;
  private String region;
  private String type;
  private String createdTime;
  private String imageId;
  private String osPlatform;
  private List<Map<String, Object>> snapshotSet;

  @Override
  @JsonIgnore
  public String getId() {
    return imageId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getCreatedTime() {
    return createdTime;
  }

  public void setCreatedTime(String createdTime) {
    this.createdTime = createdTime;
  }

  public String getImageId() {
    return imageId;
  }

  public void setImageId(String imageId) {
    this.imageId = imageId;
  }

  public String getOsPlatform() {
    return osPlatform;
  }

  public void setOsPlatform(String osPlatform) {
    this.osPlatform = osPlatform;
  }

  public List<Map<String, Object>> getSnapshotSet() {
    return snapshotSet;
  }

  public void setSnapshotSet(List<Map<String, Object>> snapshotSet) {
    this.snapshotSet = snapshotSet;
  }
}
