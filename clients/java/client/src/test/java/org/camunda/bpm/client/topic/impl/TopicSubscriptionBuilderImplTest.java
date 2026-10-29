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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Collections;

import org.camunda.bpm.client.exception.ExternalTaskClientException;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.junit.Before;
import org.junit.Test;

public class TopicSubscriptionBuilderImplTest {

  protected TopicSubscriptionManager manager;
  protected ExternalTaskHandler handler;

  @Before
  public void setUp() {
    manager = mock(TopicSubscriptionManager.class);
    handler = mock(ExternalTaskHandler.class);
  }

  @Test
  public void shouldCopyAllConfiguredFilterValuesToSubscription() {
    TopicSubscriptionImpl subscription = (TopicSubscriptionImpl) new TopicSubscriptionBuilderImpl("topic", manager)
        .lockDuration(5000L)
        .handler(handler)
        .variables("a", "b")
        .localVariables(true)
        .businessKey("bk")
        .processDefinitionId("pdId")
        .processDefinitionIdIn("id1", "id2")
        .processDefinitionKey("pdKey")
        .processDefinitionKeyIn("k1", "k2")
        .processDefinitionVersionTag("v1")
        .processVariableEquals("x", 1)
        .processVariablesEqualsIn(Collections.singletonMap("y", "z"))
        .withoutTenantId()
        .tenantIdIn("t1", "t2")
        .includeExtensionProperties(true)
        .open();

    assertThat(subscription.getTopicName()).isEqualTo("topic");
    assertThat(subscription.getLockDuration()).isEqualTo(5000L);
    assertThat(subscription.getExternalTaskHandler()).isSameAs(handler);
    assertThat(subscription.getVariableNames()).containsExactly("a", "b");
    assertThat(subscription.isLocalVariables()).isTrue();
    assertThat(subscription.getBusinessKey()).isEqualTo("bk");
    assertThat(subscription.getProcessDefinitionId()).isEqualTo("pdId");
    assertThat(subscription.getProcessDefinitionIdIn()).containsExactly("id1", "id2");
    assertThat(subscription.getProcessDefinitionKey()).isEqualTo("pdKey");
    assertThat(subscription.getProcessDefinitionKeyIn()).containsExactly("k1", "k2");
    assertThat(subscription.getProcessDefinitionVersionTag()).isEqualTo("v1");
    assertThat(subscription.getProcessVariables()).containsEntry("x", 1).containsEntry("y", "z").hasSize(2);
    assertThat(subscription.isWithoutTenantId()).isTrue();
    assertThat(subscription.getTenantIdIn()).containsExactly("t1", "t2");
    assertThat(subscription.isIncludeExtensionProperties()).isTrue();
    verify(manager).subscribe(subscription);
  }

  @Test
  public void shouldLeaveOptionalFiltersUnsetByDefault() {
    TopicSubscriptionImpl subscription = (TopicSubscriptionImpl) new TopicSubscriptionBuilderImpl("topic", manager)
        .handler(handler)
        .open();

    assertThat(subscription.getLockDuration()).isNull();
    assertThat(subscription.getVariableNames()).isNull();
    assertThat(subscription.getProcessDefinitionId()).isNull();
    assertThat(subscription.getProcessDefinitionIdIn()).isNull();
    assertThat(subscription.getProcessDefinitionKey()).isNull();
    assertThat(subscription.getProcessDefinitionKeyIn()).isNull();
    assertThat(subscription.getProcessDefinitionVersionTag()).isNull();
    assertThat(subscription.getProcessVariables()).isNull();
    assertThat(subscription.getTenantIdIn()).isNull();
    assertThat(subscription.isWithoutTenantId()).isFalse();
    assertThat(subscription.isLocalVariables()).isFalse();
    assertThat(subscription.isIncludeExtensionProperties()).isFalse();
  }

  @Test
  public void shouldFailOpenWithoutTopicName() {
    TopicSubscriptionBuilderImpl builder = new TopicSubscriptionBuilderImpl(null, manager);
    builder.handler(handler);

    assertThatThrownBy(builder::open).isInstanceOf(ExternalTaskClientException.class);
    verifyNoInteractions(manager);
  }

  @Test
  public void shouldFailOpenWithNonPositiveLockDuration() {
    TopicSubscriptionBuilderImpl builder = new TopicSubscriptionBuilderImpl("topic", manager);
    builder.handler(handler).lockDuration(0L);

    assertThatThrownBy(builder::open).isInstanceOf(ExternalTaskClientException.class);
    verifyNoInteractions(manager);
  }

  @Test
  public void shouldFailOpenWithoutHandler() {
    TopicSubscriptionBuilderImpl builder = new TopicSubscriptionBuilderImpl("topic", manager);

    assertThatThrownBy(builder::open).isInstanceOf(ExternalTaskClientException.class);
    verifyNoInteractions(manager);
  }

  @Test
  public void shouldRejectNullArguments() {
    TopicSubscriptionBuilderImpl builder = new TopicSubscriptionBuilderImpl("topic", manager);

    assertThatThrownBy(() -> builder.variables((String[]) null)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> builder.processDefinitionIdIn((String[]) null)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> builder.processDefinitionKeyIn((String[]) null)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> builder.processDefinitionVersionTag(null)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> builder.processVariablesEqualsIn(null)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> builder.processVariablesEqualsIn(Collections.singletonMap(null, "v")))
        .isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> builder.processVariableEquals(null, "v")).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> builder.tenantIdIn((String[]) null)).isInstanceOf(ExternalTaskClientException.class);
  }

}
