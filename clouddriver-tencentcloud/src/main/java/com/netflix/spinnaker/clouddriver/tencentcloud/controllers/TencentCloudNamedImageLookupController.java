package com.netflix.spinnaker.clouddriver.tencentcloud.controllers;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencentcloud.cache.Keys.Namespace;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/tencentcloud/images")
public class TencentCloudNamedImageLookupController {

  private static final int MAX_SEARCH_RESULTS = 1000;
  private static final int MIN_NAME_FILTER = 3;
  private static final String EXCEPTION_REASON =
      "Minimum of " + MIN_NAME_FILTER + " characters required to filter namedImages";

  private final String IMG_GLOB_PATTERN = "/^img-([a-f0-9]{8})$/";

  @Autowired private Cache cacheView;

  @RequestMapping(value = "/{account}/{region}/{imageId:.+}", method = RequestMethod.GET)
  public List<NamedImage> getByImgId(
      @PathVariable final String account,
      @PathVariable final String region,
      @PathVariable final String imageId) {
    CacheData cache =
        cacheView.get(Namespace.IMAGES.ns, Keys.getImageKey(imageId, account, region));
    if (cache == null) {
      throw new NotFoundException(imageId + " not found in " + account + "/" + region);
    }

    Collection<String> namedImageKeys = cache.getRelationships().get(Namespace.NAMED_IMAGES.ns);
    if (CollectionUtils.isEmpty(namedImageKeys)) {
      throw new NotFoundException(
          "Name not found on image " + imageId + " in " + account + "/" + region);
    }

    return render(null, new ArrayList<>(Collections.singletonList(cache)), region);
  }

  @RequestMapping(value = "/find", method = RequestMethod.GET)
  public List<NamedImage> list(LookupOptions lookupOptions, HttpServletRequest request) {
    validateLookupOptions(lookupOptions);

    String glob = lookupOptions.getQ().trim();
    boolean isImgId = Pattern.matches(IMG_GLOB_PATTERN, glob);

    // Wrap in '*' if there are no glob-style characters in the query string
    if (!isImgId
        && !glob.contains("*")
        && !glob.contains("?")
        && !glob.contains("[")
        && !glob.contains("\\")) {
      glob = "*" + glob + "*";
    }

    String account = lookupOptions.getAccount() != null ? lookupOptions.account : "*";
    String region = lookupOptions.getRegion() != null ? lookupOptions.region : "*";

    String namedImageSearch = Keys.getNamedImageKey(glob, account);
    String imageSearch = Keys.getImageKey(glob, account, region);

    List<String> namedImageIdentifiers =
        !isImgId
            ? new ArrayList<>(
                cacheView.filterIdentifiers(Namespace.NAMED_IMAGES.toString(), namedImageSearch))
            : new ArrayList<>();

    Collection<String> imageIdentifiers =
        namedImageIdentifiers.isEmpty()
            ? cacheView.filterIdentifiers(Namespace.IMAGES.toString(), imageSearch)
            : new ArrayList<>();

    namedImageIdentifiers =
        namedImageIdentifiers.subList(
            0, Math.min(MAX_SEARCH_RESULTS, namedImageIdentifiers.size()));

    Collection<CacheData> matchesByName =
        cacheView.getAll(
            Namespace.NAMED_IMAGES.toString(),
            namedImageIdentifiers,
            RelationshipCacheFilter.include(Namespace.IMAGES.toString()));
    Collection<CacheData> matchesByImageId =
        cacheView.getAll(Namespace.IMAGES.toString(), imageIdentifiers);

    return render(matchesByName, matchesByImageId, lookupOptions.getRegion());
  }

  public void validateLookupOptions(LookupOptions lookupOptions) {
    if (lookupOptions.getQ() == null || lookupOptions.getQ().length() < MIN_NAME_FILTER) {
      throw new InvalidRequestException(EXCEPTION_REASON);
    }

    String glob = lookupOptions.getQ().trim();
    boolean isImgId = Pattern.matches(IMG_GLOB_PATTERN, glob);
    if (glob.equals("img") || (!isImgId && glob.startsWith("img-"))) {
      throw new InvalidRequestException(
          "Searches by Image Id must be an exact match (img-xxxxxxxx)");
    }
  }

  private List<NamedImage> render(
      Collection<CacheData> namedImages, Collection<CacheData> images, String requiredRegion) {

    Map<String, NamedImage> byImageName = new HashMap<>();
    if (!CollectionUtils.isEmpty(namedImages)) {
      for (CacheData data : namedImages) {
        Map<String, String> keyParts = Keys.parse(data.getId());
        NamedImage namedImage = new NamedImage();
        namedImage.setImageName(keyParts.get("imageName"));
        namedImage.getAttributes().putAll(data.getAttributes());
        namedImage.getAccounts().add(keyParts.get("account"));

        if (data.getRelationships().get(IMAGES.ns) != null) {
          for (String imageKey : data.getRelationships().get(IMAGES.ns)) {
            Map<String, String> imageParts = Keys.parse(imageKey);
            String region = imageParts.get("region");
            String imageId = imageParts.get("imageId");
            if (namedImage.getImgIds().containsKey(region)) {
              namedImage.getImgIds().get(region).add(imageId);
            } else {
              namedImage.getImgIds().put(region, Arrays.asList(imageId));
            }
          }
        }
        byImageName.put(keyParts.get("imageName"), namedImage);
      }
    }

    if (!CollectionUtils.isEmpty(images)) {
      for (CacheData cacheData : images) {
        Map<String, String> keyParts = Keys.parse(cacheData.getId());
        Map<String, String> namedImageKeyParts =
            Keys.parse(cacheData.getRelationships().get(NAMED_IMAGES.ns).iterator().next());

        NamedImage namedImage = new NamedImage();
        namedImage.setImageName(namedImageKeyParts.get("imageName"));
        Map<String, String> image = (Map<String, String>) cacheData.getAttributes().get("image");
        namedImage.getAttributes().put("osPlatform", image.get("osPlatform"));
        namedImage.getAttributes().put("type", image.get("type"));
        namedImage.getAttributes().put("snapshotSet", image.get("snapshotSet"));
        namedImage.getAttributes().put("createdTime", image.get("createdTime"));
        namedImage.getAccounts().add(namedImageKeyParts.get("account"));
        String region = keyParts.get("region");
        String imageId = keyParts.get("imageId");
        if (namedImage.getImgIds().containsKey(region)) {
          namedImage.getImgIds().get(region).add(imageId);
        } else {
          namedImage.getImgIds().put(region, Arrays.asList(imageId));
        }

        byImageName.put(namedImageKeyParts.get("imageName"), namedImage);
      }
    }

    return byImageName.values().stream()
        .filter(it -> requiredRegion == null || it.getImgIds().containsKey(requiredRegion))
        .collect(Collectors.toList());
  }

  @Data
  private static class NamedImage {
    private String imageName;
    private Map<String, Object> attributes = new HashMap<>();
    private Set<String> accounts = new HashSet<>();
    private Map<String, Collection<String>> imgIds = new HashMap<>();
  }

  @Data
  private static class LookupOptions {
    private String q;
    private String account;
    private String region;
  }
}
