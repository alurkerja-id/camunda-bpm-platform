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
package org.camunda.bpm.client.spring.boot.starter.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collections;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.interceptor.auth.BasicAuthProvider;
import org.camunda.bpm.client.spring.boot.starter.BasicAuthProperties;
import org.camunda.bpm.client.spring.boot.starter.ClientProperties;
import org.camunda.bpm.client.spring.impl.client.ClientConfiguration;
import org.camunda.bpm.client.spring.impl.subscription.SubscriptionConfiguration;
import org.junit.Test;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextRefreshedEvent;

public class PropertiesAwareFactoryAndSubscriptionTest {

  @Test
  public void shouldCopyEverySetClientPropertyIntoClientConfiguration() {
    ClientProperties properties = new ClientProperties();
    properties.setBaseUrl("http://engine/rest");
    properties.setWorkerId("w1");
    properties.setMaxTasks(4);
    properties.setUsePriority(false);
    properties.setDefaultSerializationFormat("application/json");
    properties.setDateFormat("yyyy-MM-dd");
    properties.setLockDuration(1234L);
    properties.setAsyncResponseTimeout(555L);
    properties.setDisableAutoFetching(true);
    properties.setDisableBackoffStrategy(true);
    PropertiesAwareClientFactory factory = new PropertiesAwareClientFactory(properties);

    factory.applyPropertiesFrom(properties);

    ClientConfiguration applied = factory.getClientConfiguration();
    assertThat(applied.getBaseUrl()).isEqualTo("http://engine/rest");
    assertThat(applied.getWorkerId()).isEqualTo("w1");
    assertThat(applied.getMaxTasks()).isEqualTo(4);
    assertThat(applied.getUsePriority()).isFalse();
    assertThat(applied.getDefaultSerializationFormat()).isEqualTo("application/json");
    assertThat(applied.getDateFormat()).isEqualTo("yyyy-MM-dd");
    assertThat(applied.getLockDuration()).isEqualTo(1234L);
    assertThat(applied.getAsyncResponseTimeout()).isEqualTo(555L);
    assertThat(applied.getDisableAutoFetching()).isTrue();
    assertThat(applied.getDisableBackoffStrategy()).isTrue();
  }

  @Test
  public void shouldLeaveUnsetClientPropertiesOutOfClientConfiguration() {
    ClientProperties properties = new ClientProperties();
    properties.setUsePriority(true);
    properties.setDisableAutoFetching(false);
    properties.setDisableBackoffStrategy(false);
    PropertiesAwareClientFactory factory = new PropertiesAwareClientFactory(properties);

    factory.applyPropertiesFrom(properties);

    ClientConfiguration applied = factory.getClientConfiguration();
    assertThat(applied.getBaseUrl()).isNull();
    assertThat(applied.getWorkerId()).isNull();
    assertThat(applied.getMaxTasks()).isNull();
    assertThat(applied.getUsePriority()).isNull();
    assertThat(applied.getLockDuration()).isNull();
    assertThat(applied.getAsyncResponseTimeout()).isNull();
    assertThat(applied.getDisableAutoFetching()).isNull();
    assertThat(applied.getDisableBackoffStrategy()).isNull();
  }

  @Test
  public void shouldRegisterBasicAuthInterceptorOnlyWhenCredentialsAreConfigured() {
    ClientProperties withAuth = new ClientProperties();
    BasicAuthProperties auth = new BasicAuthProperties();
    auth.setUsername("demo");
    auth.setPassword("demo");
    withAuth.setBasicAuth(auth);
    PropertiesAwareClientFactory authFactory = new PropertiesAwareClientFactory(withAuth);
    PropertiesAwareClientFactory plainFactory = new PropertiesAwareClientFactory(new ClientProperties());

    authFactory.addBasicAuthInterceptor();
    plainFactory.addBasicAuthInterceptor();

    assertThat(authFactory.getRequestInterceptors()).hasSize(1).first().isInstanceOf(BasicAuthProvider.class);
    assertThat(plainFactory.getRequestInterceptors()).isEmpty();
  }

  protected PropertiesAwareSpringTopicSubscription subscription(ClientProperties properties, SubscriptionConfiguration base) {
    PropertiesAwareSpringTopicSubscription subscription = new PropertiesAwareSpringTopicSubscription(
        mock(ExternalTaskClient.class), mock(ApplicationEventPublisher.class), properties);
    subscription.setSubscriptionConfiguration(base);
    return subscription;
  }

  @Test
  public void shouldOverrideSubscriptionConfigurationWithMatchingTopicProperties() {
    SubscriptionConfiguration base = new SubscriptionConfiguration();
    base.setTopicName("topic");
    base.setLockDuration(1L);
    base.setBusinessKey("annotation-key");
    SubscriptionConfiguration override = new SubscriptionConfiguration();
    override.setAutoOpen(false);
    override.setLockDuration(500L);
    override.setVariableNames(Arrays.asList("a", "b"));
    override.setBusinessKey("property-key");
    override.setProcessDefinitionId("pd");
    override.setProcessDefinitionIdIn(Collections.singletonList("pd1"));
    override.setProcessDefinitionKey("pk");
    override.setProcessDefinitionKeyIn(Collections.singletonList("pk1"));
    override.setProcessDefinitionVersionTag("v1");
    override.setProcessVariables(Collections.<String, Object>singletonMap("x", 1));
    override.setWithoutTenantId(true);
    override.setTenantIdIn(Collections.singletonList("t1"));
    override.setIncludeExtensionProperties(true);
    ClientProperties properties = new ClientProperties();
    properties.setSubscriptions(Collections.singletonMap("topic", override));
    PropertiesAwareSpringTopicSubscription subscription = subscription(properties, base);

    subscription.mergeSubscriptionWithProperties();

    SubscriptionConfiguration merged = subscription.getSubscriptionConfiguration();
    assertThat(merged.getAutoOpen()).isFalse();
    assertThat(merged.getLockDuration()).isEqualTo(500L);
    assertThat(merged.getVariableNames()).containsExactly("a", "b");
    assertThat(merged.getBusinessKey()).isEqualTo("property-key");
    assertThat(merged.getProcessDefinitionId()).isEqualTo("pd");
    assertThat(merged.getProcessDefinitionIdIn()).containsExactly("pd1");
    assertThat(merged.getProcessDefinitionKey()).isEqualTo("pk");
    assertThat(merged.getProcessDefinitionKeyIn()).containsExactly("pk1");
    assertThat(merged.getProcessDefinitionVersionTag()).isEqualTo("v1");
    assertThat(merged.getProcessVariables()).containsEntry("x", 1);
    assertThat(merged.getWithoutTenantId()).isTrue();
    assertThat(merged.getTenantIdIn()).containsExactly("t1");
    assertThat(merged.getIncludeExtensionProperties()).isTrue();
  }

  @Test
  public void shouldKeepAnnotationValuesForFieldsNotSetInProperties() {
    SubscriptionConfiguration base = new SubscriptionConfiguration();
    base.setTopicName("topic");
    base.setLockDuration(7L);
    SubscriptionConfiguration override = new SubscriptionConfiguration();
    override.setBusinessKey("only-this");
    ClientProperties properties = new ClientProperties();
    properties.setSubscriptions(Collections.singletonMap("topic", override));
    PropertiesAwareSpringTopicSubscription subscription = subscription(properties, base);

    subscription.mergeSubscriptionWithProperties();

    assertThat(subscription.getSubscriptionConfiguration().getLockDuration()).isEqualTo(7L);
    assertThat(subscription.getSubscriptionConfiguration().getBusinessKey()).isEqualTo("only-this");
  }

  @Test
  public void shouldIgnorePropertiesOfOtherTopics() {
    SubscriptionConfiguration base = new SubscriptionConfiguration();
    base.setTopicName("topic");
    base.setLockDuration(7L);
    SubscriptionConfiguration other = new SubscriptionConfiguration();
    other.setLockDuration(999L);
    ClientProperties properties = new ClientProperties();
    properties.setSubscriptions(Collections.singletonMap("other-topic", other));
    PropertiesAwareSpringTopicSubscription subscription = subscription(properties, base);

    subscription.mergeSubscriptionWithProperties();

    assertThat(subscription.getSubscriptionConfiguration().getLockDuration()).isEqualTo(7L);
  }

  @Test
  public void shouldStartSubscriptionOnlyOnApplicationStartedEvent() {
    PropertiesAwareSpringTopicSubscription subscription = subscription(new ClientProperties(), new SubscriptionConfiguration());

    assertThat(subscription.isEventThatCanStartSubscription().test(mock(ApplicationStartedEvent.class))).isTrue();
    assertThat(subscription.isEventThatCanStartSubscription().test(mock(ContextRefreshedEvent.class))).isFalse();
    assertThat(subscription.isEventThatCanStartSubscription().test(mock(ApplicationEvent.class))).isFalse();
  }

}
