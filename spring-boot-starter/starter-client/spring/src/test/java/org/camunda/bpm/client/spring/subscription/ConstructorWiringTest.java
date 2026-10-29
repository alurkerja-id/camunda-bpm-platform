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
package org.camunda.bpm.client.spring.subscription;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.spring.MockedTest;
import org.camunda.bpm.client.spring.SpringTopicSubscription;
import org.camunda.bpm.client.spring.impl.subscription.SpringTopicSubscriptionImpl;
import org.camunda.bpm.client.spring.subscription.configuration.AutoOpenFalseConfiguration;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ContextConfiguration(classes = {
    AutoOpenFalseConfiguration.class
})
public class ConstructorWiringTest extends MockedTest {

  @Autowired
  protected SpringTopicSubscription subscription;

  @Autowired
  protected ExternalTaskClient externalTaskClient;

  @Autowired
  protected ApplicationContext applicationContext;

  @Test
  public void shouldWireSubscriptionThroughConstructor() {
    SpringTopicSubscriptionImpl impl = (SpringTopicSubscriptionImpl) subscription;

    assertThat(impl.getClass()).isEqualTo(SpringTopicSubscriptionImpl.class);
    assertThat(ReflectionTestUtils.getField(impl, "client")).isSameAs(externalTaskClient);
    assertThat(ReflectionTestUtils.getField(impl, "applicationEventPublisher")).isSameAs(applicationContext);
  }

  @Test
  public void shouldStartSubscriptionOnContextRefresh() {
    assertThat(subscription.isAutoOpen()).isFalse();
    verify(externalTaskClient).subscribe("topic-name");
    assertThat(ReflectionTestUtils.getField(subscription, "topicSubscriptionBuilder")).isNotNull();
  }

}
