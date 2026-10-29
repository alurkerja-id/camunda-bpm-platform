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
package org.camunda.bpm.client.topic.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;

import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.topic.impl.dto.TopicRequestDto;
import org.junit.Test;

public class TopicSubscriptionImplTest {

  protected TopicSubscriptionImpl subscription(String topic, TopicSubscriptionManager manager) {
    return new TopicSubscriptionImpl(topic, 1000L, mock(ExternalTaskHandler.class), manager,
        Arrays.asList("v1"), "bk");
  }

  @Test
  public void shouldUnsubscribeFromManagerOnClose() {
    TopicSubscriptionManager manager = mock(TopicSubscriptionManager.class);
    TopicSubscriptionImpl subscription = subscription("t", manager);

    subscription.close();

    verify(manager).unsubscribe(subscription);
  }

  @Test
  public void shouldReturnSelfFromFluentSetters() {
    TopicSubscriptionImpl subscription = subscription("t", mock(TopicSubscriptionManager.class));

    assertThat(subscription.setProcessDefinitionId("id")).isSameAs(subscription);
    assertThat(subscription.setProcessDefinitionIdIn(Arrays.asList("a"))).isSameAs(subscription);
    assertThat(subscription.setProcessDefinitionKey("k")).isSameAs(subscription);
    assertThat(subscription.setProcessDefinitionKeyIn(Arrays.asList("b"))).isSameAs(subscription);
    assertThat(subscription.setTenantIdIn(Arrays.asList("t1"))).isSameAs(subscription);
    assertThat(subscription.getProcessDefinitionId()).isEqualTo("id");
    assertThat(subscription.getProcessDefinitionIdIn()).containsExactly("a");
    assertThat(subscription.getProcessDefinitionKey()).isEqualTo("k");
    assertThat(subscription.getProcessDefinitionKeyIn()).containsExactly("b");
    assertThat(subscription.getTenantIdIn()).containsExactly("t1");
  }

  @Test
  public void shouldMergeProcessVariablesAcrossCalls() {
    TopicSubscriptionImpl subscription = subscription("t", mock(TopicSubscriptionManager.class));

    subscription.setProcessVariables(Collections.singletonMap("a", 1));
    subscription.setProcessVariables(Collections.singletonMap("b", 2));

    assertThat(subscription.getProcessVariables()).containsEntry("a", 1).containsEntry("b", 2).hasSize(2);
  }

  @Test
  public void shouldCompareEqualityAndHashCodeByTopicName() {
    TopicSubscriptionImpl first = subscription("t", null);
    TopicSubscriptionImpl same = subscription("t", null);
    TopicSubscriptionImpl other = subscription("other", null);
    TopicSubscriptionImpl noName = subscription(null, null);

    assertThat(first).isEqualTo(first).isEqualTo(same).isNotEqualTo(other).isNotEqualTo(null)
        .isNotEqualTo("t").isNotEqualTo(noName);
    assertThat(noName).isEqualTo(subscription(null, null)).isNotEqualTo(first);
    assertThat(first.hashCode()).isEqualTo(same.hashCode()).isNotEqualTo(other.hashCode());
    assertThat(noName.hashCode()).isEqualTo(subscription(null, null).hashCode());
  }

  @Test
  public void shouldExposeAllValuesOnTopicRequestDto() {
    TopicRequestDto dto = new TopicRequestDto("t", 7L, Arrays.asList("x"), "bk");
    dto.setLocalVariables(true);
    dto.setProcessDefinitionId("id");
    dto.setProcessDefinitionIdIn(Arrays.asList("i1"));
    dto.setProcessDefinitionKey("key");
    dto.setProcessDefinitionKeyIn(Arrays.asList("k1"));
    dto.setProcessDefinitionVersionTag("tag");
    dto.setProcessVariables(Collections.<String, Object>singletonMap("p", 1));
    dto.setWithoutTenantId(true);
    dto.setTenantIdIn(Arrays.asList("t1"));
    dto.setIncludeExtensionProperties(true);

    assertThat(dto.getTopicName()).isEqualTo("t");
    assertThat(dto.getLockDuration()).isEqualTo(7L);
    assertThat(dto.getVariables()).containsExactly("x");
    assertThat(dto.getBusinessKey()).isEqualTo("bk");
    assertThat(dto.isLocalVariables()).isTrue();
    assertThat(dto.getProcessDefinitionId()).isEqualTo("id");
    assertThat(dto.getProcessDefinitionIdIn()).containsExactly("i1");
    assertThat(dto.getProcessDefinitionKey()).isEqualTo("key");
    assertThat(dto.getProcessDefinitionKeyIn()).containsExactly("k1");
    assertThat(dto.getProcessDefinitionVersionTag()).isEqualTo("tag");
    assertThat(dto.getProcessVariables()).containsEntry("p", 1);
    assertThat(dto.isWithoutTenantId()).isTrue();
    assertThat(dto.getTenantIdIn()).containsExactly("t1");
    assertThat(dto.isIncludeExtensionProperties()).isTrue();
  }

  @Test
  public void shouldBuildRequestDtoFromFullyConfiguredSubscription() {
    TopicSubscriptionImpl subscription = subscription("t", null);
    subscription.setProcessDefinitionId("id");
    subscription.setProcessDefinitionIdIn(Arrays.asList("i1"));
    subscription.setProcessDefinitionKey("key");
    subscription.setProcessDefinitionKeyIn(Arrays.asList("k1"));
    subscription.setProcessDefinitionVersionTag("tag");
    subscription.setProcessVariables(Collections.<String, Object>singletonMap("p", 1));
    subscription.setWithoutTenantId(true);
    subscription.setTenantIdIn(Arrays.asList("t1"));
    subscription.setLocalVariables(true);
    subscription.setIncludeExtensionProperties(true);

    TopicRequestDto dto = TopicRequestDto.fromTopicSubscription(subscription, 99L);

    assertThat(dto.getTopicName()).isEqualTo("t");
    assertThat(dto.getLockDuration()).isEqualTo(1000L);
    assertThat(dto.getVariables()).containsExactly("v1");
    assertThat(dto.getBusinessKey()).isEqualTo("bk");
    assertThat(dto.getProcessDefinitionId()).isEqualTo("id");
    assertThat(dto.getProcessDefinitionIdIn()).containsExactly("i1");
    assertThat(dto.getProcessDefinitionKey()).isEqualTo("key");
    assertThat(dto.getProcessDefinitionKeyIn()).containsExactly("k1");
    assertThat(dto.getProcessDefinitionVersionTag()).isEqualTo("tag");
    assertThat(dto.getProcessVariables()).containsEntry("p", 1);
    assertThat(dto.isWithoutTenantId()).isTrue();
    assertThat(dto.getTenantIdIn()).containsExactly("t1");
    assertThat(dto.isLocalVariables()).isTrue();
    assertThat(dto.isIncludeExtensionProperties()).isTrue();
  }

  @Test
  public void shouldFallBackToClientLockDurationAndLeaveFiltersUnset() {
    TopicSubscriptionImpl subscription = new TopicSubscriptionImpl("t", null, mock(ExternalTaskHandler.class), null, null, null);

    TopicRequestDto dto = TopicRequestDto.fromTopicSubscription(subscription, 99L);

    assertThat(dto.getLockDuration()).isEqualTo(99L);
    assertThat(dto.getProcessDefinitionId()).isNull();
    assertThat(dto.getProcessDefinitionIdIn()).isNull();
    assertThat(dto.getProcessDefinitionKey()).isNull();
    assertThat(dto.getProcessDefinitionKeyIn()).isNull();
    assertThat(dto.getProcessDefinitionVersionTag()).isNull();
    assertThat(dto.getProcessVariables()).isNull();
    assertThat(dto.getTenantIdIn()).isNull();
    assertThat(dto.isWithoutTenantId()).isFalse();
    assertThat(dto.isLocalVariables()).isFalse();
    assertThat(dto.isIncludeExtensionProperties()).isFalse();
  }

}
