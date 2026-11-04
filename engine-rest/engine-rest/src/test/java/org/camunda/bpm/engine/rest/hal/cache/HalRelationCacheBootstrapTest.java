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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import org.camunda.bpm.engine.rest.cache.Cache;
import org.camunda.bpm.engine.rest.hal.Hal;
import org.junit.After;
import org.junit.Test;

/**
 * Covers {@link HalRelationCacheBootstrap}: building caches from the servlet context parameter,
 * setting their properties reflectively, registering them with {@link Hal}, and tearing them down.
 */
public class HalRelationCacheBootstrapTest {

  /** A cache whose only setter throws. */
  public static class ExplodingCache implements Cache {
    public void setBoom(String value) {
      throw new IllegalStateException(value);
    }
    @Override public void put(String id, Object resource) { }
    @Override public Object get(String id) { return null; }
    @Override public void destroy() { }
  }

  /** A cache that cannot be instantiated reflectively. */
  public abstract static class AbstractCache implements Cache { }

  private final HalRelationCacheBootstrap bootstrap = new HalRelationCacheBootstrap();

  @After
  public void clearRegisteredCaches() {
    Hal.getInstance().destroyHalRelationCaches();
  }

  private static ServletContextEvent eventWithParameter(String value) {
    ServletContext context = mock(ServletContext.class);
    when(context.getInitParameter(HalRelationCacheBootstrap.CONTEXT_PARAM_NAME)).thenReturn(value);
    return new ServletContextEvent(context);
  }

  private static Map<String, Object> settings(String key, Object value) {
    Map<String, Object> map = new HashMap<>();
    map.put(key, value);
    return map;
  }

  private static HalRelationCacheConfiguration configurationFor(Class<?> cacheClass, Map<String, Object> settings) {
    HalRelationCacheConfiguration configuration = new HalRelationCacheConfiguration();
    configuration.setCacheImplementationClass(cacheClass);
    configuration.addCacheConfiguration(String.class, settings);
    return configuration;
  }

  @Test
  public void shouldDoNothingWithoutTheContextParameter() {
    bootstrap.contextInitialized(eventWithParameter(null));

    assertThat(Hal.getInstance().getHalRelationCache(String.class)).isNull();
  }

  @Test
  public void shouldBuildConfigureAndRegisterTheCachesFromTheContextParameter() {
    bootstrap.contextInitialized(eventWithParameter(
        "{\"cacheImplementation\":\"" + DefaultHalResourceCache.class.getName() + "\","
        + "\"caches\":{\"java.lang.String\":{\"capacity\":7,\"secondsToLive\":30}}}"));

    Cache cache = Hal.getInstance().getHalRelationCache(String.class);
    assertThat(cache).isInstanceOf(DefaultHalResourceCache.class);
    assertThat(((DefaultHalResourceCache) cache).getCapacity()).isEqualTo(7);
    assertThat(((DefaultHalResourceCache) cache).getSecondsToLive()).isEqualTo(30L);
  }

  @Test
  public void shouldCreateAnIndependentCachePerResourceClass() {
    HalRelationCacheConfiguration configuration = configurationFor(DefaultHalResourceCache.class, settings("capacity", 2));
    configuration.addCacheConfiguration(Integer.class, settings("capacity", 9));

    bootstrap.configureCaches(configuration);

    Cache strings = Hal.getInstance().getHalRelationCache(String.class);
    Cache integers = Hal.getInstance().getHalRelationCache(Integer.class);
    assertThat(strings).isNotSameAs(integers);
    assertThat(((DefaultHalResourceCache) strings).getCapacity()).isEqualTo(2);
    assertThat(((DefaultHalResourceCache) integers).getCapacity()).isEqualTo(9);
  }

  @Test
  public void shouldDestroyAndForgetTheCachesWhenTheContextIsDestroyed() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache();
    cache.put("id", "value");
    Hal.getInstance().registerHalRelationCache(String.class, cache);

    bootstrap.contextDestroyed(eventWithParameter(null));

    assertThat(Hal.getInstance().getHalRelationCache(String.class)).isNull();
    assertThat(cache.size()).isZero();
  }

  @Test
  public void shouldRejectASettingWithoutASetter() {
    HalRelationCacheConfiguration configuration = configurationFor(DefaultHalResourceCache.class, settings("nothing", 1));

    assertThatThrownBy(() -> bootstrap.configureCaches(configuration))
        .isInstanceOf(HalRelationCacheConfigurationException.class)
        .hasMessage("Unable to find setter for property nothing");
  }

  @Test
  public void shouldRejectAnAmbiguousSetter() {
    // two overloads of setCapacity with different parameter types
    class Ambiguous extends DefaultHalResourceCache {
      public void setCapacity(String capacity) { }
    }

    assertThatThrownBy(() -> bootstrap.configureCache(new Ambiguous(), "capacity", 1))
        .isInstanceOf(HalRelationCacheConfigurationException.class)
        .hasMessage("Unable to find setter for property capacity");
  }

  @Test
  public void shouldRejectASettingValueOfTheWrongType() {
    HalRelationCacheConfiguration configuration = configurationFor(DefaultHalResourceCache.class, settings("capacity", "many"));

    assertThatThrownBy(() -> bootstrap.configureCaches(configuration))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldReportASetterThatThrows() {
    HalRelationCacheConfiguration configuration = configurationFor(ExplodingCache.class, settings("boom", "bang"));

    assertThatThrownBy(() -> bootstrap.configureCaches(configuration))
        .isInstanceOf(HalRelationCacheConfigurationException.class)
        .hasMessage("Unable to invoke setter for property boom");
  }

  @Test
  public void shouldReportACacheClassThatCannotBeInstantiated() {
    HalRelationCacheConfiguration configuration = configurationFor(AbstractCache.class, settings("x", 1));

    assertThatThrownBy(() -> bootstrap.configureCaches(configuration))
        .isInstanceOf(HalRelationCacheConfigurationException.class)
        .hasMessageContaining("Unable to instantiate cache class " + AbstractCache.class.getName());
  }
}
