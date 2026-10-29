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
package org.camunda.bpm.spring.boot.starter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.camunda.bpm.spring.boot.starter.rest.spi.SpringBootProcessEngineProvider;
import org.junit.Test;
import org.springframework.boot.jersey.autoconfigure.JerseyApplicationPath;
import org.camunda.bpm.engine.rest.filter.CacheControlFilter;
import org.camunda.bpm.engine.rest.filter.EmptyBodyFilter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;

/**
 * Covers the REST wiring of the starter: servlet filters are registered once on the Jersey path,
 * fetch-and-lock settings are forwarded to the servlet context, and the Jersey resource config
 * registers the Camunda resources.
 */
public class CamundaBpmRestUnitTest {

  protected JerseyApplicationPath path(String value) {
    return () -> value;
  }

  @Test
  public void shouldRegisterRestFiltersOnJerseyPath() {
    var context = mock(ServletContext.class);
    var registration = mock(FilterRegistration.Dynamic.class);
    when(context.addFilter(anyString(), any(Class.class))).thenReturn(registration);
    var properties = new CamundaBpmProperties();
    properties.getRestApi().getFetchAndLock().setQueueCapacity(7);

    new CamundaBpmRestInitializer(path("/engine-rest"), properties).onStartup(context);

    verify(context).addFilter("EmptyBodyFilter", EmptyBodyFilter.class);
    verify(context).addFilter("CacheControlFilter", CacheControlFilter.class);
    verify(registration, times(2)).addMappingForUrlPatterns(any(), eq(true), eq("/engine-rest/*"));
    verify(context).setInitParameter("fetch-and-lock-queue-capacity", "7");
  }

  @Test
  public void shouldNotRegisterFiltersTwice() {
    var context = mock(ServletContext.class);
    when(context.getFilterRegistration(anyString())).thenReturn(mock(FilterRegistration.class));

    new CamundaBpmRestInitializer(path("/engine-rest"), new CamundaBpmProperties()).onStartup(context);

    verify(context, never()).addFilter(anyString(), any(Class.class));
  }

  @Test
  public void shouldRegisterCamundaResourcesOnAfterPropertiesSet() throws Exception {
    var config = new CamundaJerseyResourceConfig();

    config.afterPropertiesSet();

    assertThat(config.getClasses()).isNotEmpty();
    assertThat(config.isRegistered(org.glassfish.jersey.jackson.JacksonFeature.class)).isTrue();
  }

  @Test
  public void shouldCreateRestBeansInAutoConfiguration() {
    var configuration = new CamundaBpmRestJerseyAutoConfiguration();

    assertThat(configuration.createRestConfig()).isNotNull();
    assertThat(configuration.getFetchAndLockContextListener()).isNotNull();
    assertThat(configuration.camundaBpmRestInitializer(path("/engine-rest"), new CamundaBpmProperties())).isNotNull();
  }

  @Test
  public void shouldReportNoEnginesWhenNoneAreRegistered() {
    var provider = new SpringBootProcessEngineProvider();

    assertThat(provider.getProcessEngineNames()).isNotNull();
    assertThat(provider.getProcessEngine("does-not-exist")).isNull();
  }
}
