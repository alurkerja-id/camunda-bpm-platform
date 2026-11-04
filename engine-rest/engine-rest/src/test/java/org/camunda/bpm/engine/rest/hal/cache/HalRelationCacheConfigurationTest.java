/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.rest.hal.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.engine.rest.cache.Cache;
import org.junit.Test;

/**
 * Covers {@link HalRelationCacheConfiguration}: the JSON configuration of the HAL relation caches
 * (a cache implementation class plus one settings map per HAL resource class) and each way it can
 * be misconfigured.
 */
public class HalRelationCacheConfigurationTest {

  private static final String CACHE = DefaultHalResourceCache.class.getName();

  @Test
  public void shouldStartEmpty() {
    HalRelationCacheConfiguration configuration = new HalRelationCacheConfiguration();

    assertThat(configuration.getCacheImplementationClass()).isNull();
    assertThat(configuration.getCacheConfigurations()).isEmpty();
  }

  @Test
  public void shouldParseImplementationAndPerResourceSettings() {
    HalRelationCacheConfiguration configuration = new HalRelationCacheConfiguration(
        "{\"cacheImplementation\":\"" + CACHE + "\","
        + "\"caches\":{\"java.lang.String\":{\"capacity\":100,\"secondsToLive\":900},"
        + "\"java.lang.Integer\":{\"capacity\":5}}}");

    assertThat(configuration.getCacheImplementationClass()).isEqualTo(DefaultHalResourceCache.class);
    Map<Class<?>, Map<String, Object>> caches = configuration.getCacheConfigurations();
    assertThat(caches).hasSize(2);
    assertThat(caches.get(String.class)).containsEntry("capacity", 100).containsEntry("secondsToLive", 900);
    assertThat(caches.get(Integer.class)).containsEntry("capacity", 5).hasSize(1);
  }

  @Test
  public void shouldAcceptAConfigurationWithoutAnyCaches() {
    HalRelationCacheConfiguration configuration =
        new HalRelationCacheConfiguration("{\"cacheImplementation\":\"" + CACHE + "\"}");

    assertThat(configuration.getCacheImplementationClass()).isEqualTo(DefaultHalResourceCache.class);
    assertThat(configuration.getCacheConfigurations()).isEmpty();
  }

  @Test
  public void shouldRejectAConfigurationWithoutAnImplementation() {
    assertThatThrownBy(() -> new HalRelationCacheConfiguration("{\"caches\":{}}"))
        .isInstanceOf(HalRelationCacheConfigurationException.class)
        .hasMessage("Unable to find the cacheImplementation parameter");
  }

  @Test
  public void shouldRejectMalformedJson() {
    assertThatThrownBy(() -> new HalRelationCacheConfiguration("{ not json"))
        .isInstanceOf(HalRelationCacheConfigurationException.class)
        .hasMessage("Unable to parse cache configuration")
        .hasCauseInstanceOf(java.io.IOException.class);
  }

  @Test
  public void shouldRejectAnUnknownImplementationClass() {
    assertThatThrownBy(() -> new HalRelationCacheConfiguration("{\"cacheImplementation\":\"no.such.Cache\"}"))
        .isInstanceOf(HalRelationCacheConfigurationException.class)
        .hasMessage("Unable to load class of cache configuration no.such.Cache")
        .hasCauseInstanceOf(ClassNotFoundException.class);
  }

  @Test
  public void shouldRejectAnUnknownHalResourceClass() {
    assertThatThrownBy(() -> new HalRelationCacheConfiguration(
        "{\"cacheImplementation\":\"" + CACHE + "\",\"caches\":{\"no.such.Resource\":{}}}"))
        .isInstanceOf(HalRelationCacheConfigurationException.class)
        .hasMessage("Unable to load class of cache configuration no.such.Resource");
  }

  @Test
  public void shouldRejectAnImplementationThatIsNotACache() {
    HalRelationCacheConfiguration configuration = new HalRelationCacheConfiguration();

    assertThatThrownBy(() -> configuration.setCacheImplementationClass(String.class))
        .isInstanceOf(HalRelationCacheConfigurationException.class)
        .hasMessage("Cache implementation class java.lang.String does not implement the interface "
            + Cache.class.getName());
    assertThat(configuration.getCacheImplementationClass()).isNull();
  }

  @Test
  public void shouldSupportProgrammaticConfiguration() {
    HalRelationCacheConfiguration configuration = new HalRelationCacheConfiguration();
    configuration.setCacheImplementationClass(DefaultHalResourceCache.class);
    configuration.addCacheConfiguration(String.class, Collections.<String, Object>singletonMap("capacity", 3));

    assertThat(configuration.getCacheImplementationClass()).isEqualTo(DefaultHalResourceCache.class);
    assertThat(configuration.getCacheConfigurations().get(String.class)).containsEntry("capacity", 3);

    Map<Class<?>, Map<String, Object>> replacement = new HashMap<>();
    configuration.setCacheConfigurations(replacement);
    assertThat(configuration.getCacheConfigurations()).isSameAs(replacement);
  }
}
