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
package org.camunda.bpm.client.spring.impl.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Properties;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.ExternalTaskClientBuilder;
import org.camunda.bpm.client.interceptor.ClientRequestInterceptor;
import org.camunda.bpm.client.spring.annotation.EnableExternalTaskClient;
import org.camunda.bpm.client.spring.exception.NotInitializedException;
import org.camunda.bpm.client.spring.exception.NotOpenedException;
import org.camunda.bpm.client.spring.exception.SpringExternalTaskClientException;
import org.camunda.bpm.client.spring.impl.util.AnnotationUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.type.StandardAnnotationMetadata;

public class ClientFactoryUnitTest {

  protected ClientFactory factory;
  protected ClientConfiguration configuration;
  protected ExternalTaskClientBuilder builder;

  @Before
  public void setUp() {
    factory = new ClientFactory();
    configuration = new ClientConfiguration();
    factory.setClientConfiguration(configuration);
    builder = mock(ExternalTaskClientBuilder.class, RETURNS_SELF);
  }

  @Test
  public void shouldOrderDescendingWhenUseCreateTimeIsEnabled() {
    configuration.setUseCreateTime(true);

    factory.tryConfigureCreateTimeOrder(builder);

    verify(builder).orderByCreateTime();
    verify(builder).desc();
  }

  @Test
  public void shouldOrderAscendingOrDescendingAsConfigured() {
    configuration.setOrderByCreateTime(EnableExternalTaskClient.STRING_ORDER_BY_ASC_VALUE);
    factory.tryConfigureCreateTimeOrder(builder);
    verify(builder).asc();

    ExternalTaskClientBuilder second = mock(ExternalTaskClientBuilder.class, RETURNS_SELF);
    configuration.setOrderByCreateTime(EnableExternalTaskClient.STRING_ORDER_BY_DESC_VALUE);
    factory.tryConfigureCreateTimeOrder(second);
    verify(second).desc();
  }

  @Test
  public void shouldNotOrderWithoutConfiguration() {
    factory.tryConfigureCreateTimeOrder(builder);

    verify(builder, never()).orderByCreateTime();
  }

  @Test
  public void shouldRejectInvalidOrConflictingCreateTimeConfiguration() {
    configuration.setOrderByCreateTime("sideways");
    assertThatThrownBy(() -> factory.tryConfigureCreateTimeOrder(builder))
        .isInstanceOf(SpringExternalTaskClientException.class).hasMessageContaining("sideways");

    configuration.setUseCreateTime(true);
    assertThatThrownBy(() -> factory.tryConfigureCreateTimeOrder(builder))
        .isInstanceOf(SpringExternalTaskClientException.class).hasMessageContaining("useCreateTime");
  }

  @Test
  public void shouldAddAllConfiguredInterceptorsToBuilder() {
    ClientRequestInterceptor first = context -> { };
    ClientRequestInterceptor second = context -> { };
    factory.getRequestInterceptors().add(first);
    factory.getRequestInterceptors().add(second);

    factory.addClientRequestInterceptors(builder);

    verify(builder).addInterceptor(first);
    verify(builder).addInterceptor(second);
  }

  @Test
  public void shouldResolvePlaceholdersOnlyWhenConfigurerIsPresent() throws Exception {
    assertThat(factory.resolve("${key}")).isEqualTo("${key}");
    assertThat(factory.resolve(null)).isNull();

    PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
    Properties values = new Properties();
    values.setProperty("key", "resolved");
    configurer.setProperties(values);
    configurer.postProcessBeanFactory(new org.springframework.beans.factory.support.DefaultListableBeanFactory());
    factory.setPropertyConfigurer(configurer);

    assertThat(factory.resolve("${key}")).isEqualTo("resolved");
    assertThat(factory.resolve(null)).isNull();
  }

  @Test
  public void shouldStopCreatedClientOnClose() throws Exception {
    factory.close();

    ExternalTaskClient client = mock(ExternalTaskClient.class);
    java.lang.reflect.Field field = ClientFactory.class.getDeclaredField("client");
    field.setAccessible(true);
    field.set(factory, client);
    factory.close();

    verify(client).stop();
    factory.afterPropertiesSet();
  }

  @Test
  public void shouldCarryMessageAndCauseOnSpringExceptions() {
    Throwable cause = new IllegalStateException("root");

    assertThat(new NotOpenedException("m").getMessage()).isEqualTo("m");
    assertThat(new NotOpenedException("m", cause).getCause()).isSameAs(cause);
    assertThat(new NotInitializedException("m").getMessage()).isEqualTo("m");
    assertThat(new NotInitializedException("m", cause).getCause()).isSameAs(cause);
    assertThat(new SpringExternalTaskClientException("m", cause).getCause()).isSameAs(cause);
  }

  @EnableExternalTaskClient(baseUrl = "http://x")
  public static class Annotated {
  }

  public static class Plain {
  }

  @Test
  public void shouldReturnAnnotationOnlyWhenPresent() {
    EnableExternalTaskClient found = AnnotationUtil.get(EnableExternalTaskClient.class, new StandardAnnotationMetadata(Annotated.class));
    EnableExternalTaskClient missing = AnnotationUtil.get(EnableExternalTaskClient.class, new StandardAnnotationMetadata(Plain.class));

    assertThat(found).isNotNull();
    assertThat(found.baseUrl()).isEqualTo("http://x");
    assertThat(missing).isNull();
  }

}
