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
package org.camunda.bpm.client.spring.boot.starter.subscription;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.spring.SpringTopicSubscription;
import org.camunda.bpm.client.spring.boot.starter.ClientProperties;
import org.camunda.bpm.client.spring.boot.starter.ParsePropertiesHelper;
import org.camunda.bpm.client.spring.boot.starter.impl.ClientAutoConfiguration;
import org.camunda.bpm.client.spring.boot.starter.impl.PropertiesAwareSpringTopicSubscription;
import org.camunda.bpm.client.spring.boot.starter.subscription.configuration.FullSubscriptionConfiguration;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@ContextConfiguration(classes = {
    ParsePropertiesHelper.TestConfig.class,
    ClientAutoConfiguration.class,
    FullSubscriptionConfiguration.class
})
public class ConstructorWiringTest extends ParsePropertiesHelper {

  @MockitoBean
  public ExternalTaskClient externalTaskClient;

  @Autowired
  public SpringTopicSubscription subscription;

  @Autowired
  public ClientProperties clientProperties;

  @Autowired
  public ApplicationContext applicationContext;

  @Test
  public void shouldWirePropertiesAwareSubscriptionThroughConstructor() {
    assertThat(subscription).isInstanceOf(PropertiesAwareSpringTopicSubscription.class);
    PropertiesAwareSpringTopicSubscription impl = (PropertiesAwareSpringTopicSubscription) subscription;

    assertThat(ReflectionTestUtils.getField(impl, "clientProperties")).isSameAs(clientProperties);
    assertThat(ReflectionTestUtils.getField(impl, "client")).isSameAs(externalTaskClient);
    assertThat(ReflectionTestUtils.getField(impl, "applicationEventPublisher")).isSameAs(applicationContext);
  }

}
