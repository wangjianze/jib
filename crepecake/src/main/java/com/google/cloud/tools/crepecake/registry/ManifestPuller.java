/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.crepecake.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.http.HttpMethods;
import com.google.cloud.tools.crepecake.blob.Blobs;
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.image.json.ManifestTemplate;
import com.google.cloud.tools.crepecake.image.json.UnknownManifestFormatException;
import com.google.cloud.tools.crepecake.image.json.V21ManifestTemplate;
import com.google.cloud.tools.crepecake.image.json.V22ManifestTemplate;
import com.google.cloud.tools.crepecake.json.JsonTemplateMapper;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/** Pulls an image's manifest. */
class ManifestPuller<T extends ManifestTemplate> implements RegistryEndpointProvider<T> {

  private final RegistryEndpointProperties registryEndpointProperties;
  private final String imageTag;
  private final Class<T> manifestTemplateClass;

  ManifestPuller(
      RegistryEndpointProperties registryEndpointProperties,
      String imageTag,
      Class<T> manifestTemplateClass) {
    this.registryEndpointProperties = registryEndpointProperties;
    this.imageTag = imageTag;
    this.manifestTemplateClass = manifestTemplateClass;
  }

  @Override
  public void buildRequest(Request.Builder builder) {
    if (manifestTemplateClass.equals(V21ManifestTemplate.class)) {
      builder.setAccept(V21ManifestTemplate.MEDIA_TYPE);
    } else if (manifestTemplateClass.equals(V22ManifestTemplate.class)) {
      builder.setAccept(V22ManifestTemplate.MEDIA_TYPE);
    } else {
      builder.setAccept(V22ManifestTemplate.MEDIA_TYPE + ", " + V21ManifestTemplate.MEDIA_TYPE);
    }
  }

  /** Parses the response body into a {@link ManifestTemplate}. */
  @Override
  public T handleResponse(Response response) throws IOException, UnknownManifestFormatException {
    return getManifestTemplateFromJson(Blobs.writeToString(response.getBody()));
  }

  @Override
  public URL getApiRoute(String apiRouteBase) throws MalformedURLException {
    return new URL(
        apiRouteBase + registryEndpointProperties.getImageName() + "/manifests/" + imageTag);
  }

  @Override
  public String getHttpMethod() {
    return HttpMethods.GET;
  }

  public String getActionDescription() {
    return "pull image manifest for "
        + registryEndpointProperties.getServerUrl()
        + "/"
        + registryEndpointProperties.getImageName()
        + ":"
        + imageTag;
  }

  /**
   * Instantiates a {@link ManifestTemplate} from a JSON string. This checks the {@code
   * schemaVersion} field of the JSON to determine which manifest version to use.
   */
  private T getManifestTemplateFromJson(String jsonString)
      throws IOException, UnknownManifestFormatException {
    ObjectNode node = new ObjectMapper().readValue(jsonString, ObjectNode.class);
    if (!node.has("schemaVersion")) {
      throw new UnknownManifestFormatException("Cannot find field 'schemaVersion' in manifest");
    }

    if (!manifestTemplateClass.equals(ManifestTemplate.class)) {
      return JsonTemplateMapper.readJson(jsonString, manifestTemplateClass);
    }

    int schemaVersion = node.get("schemaVersion").asInt(-1);
    switch (schemaVersion) {
      case 1:
        return manifestTemplateClass.cast(
            JsonTemplateMapper.readJson(jsonString, V21ManifestTemplate.class));

      case 2:
        return manifestTemplateClass.cast(
            JsonTemplateMapper.readJson(jsonString, V22ManifestTemplate.class));

      case -1:
        throw new UnknownManifestFormatException("`schemaVersion` field is not an integer");

      default:
        throw new UnknownManifestFormatException(
            "Unknown schemaVersion: " + schemaVersion + " - only 1 and 2 are supported");
    }
  }
}
