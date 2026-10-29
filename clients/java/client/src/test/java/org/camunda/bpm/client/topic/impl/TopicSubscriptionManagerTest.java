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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.camunda.bpm.client.backoff.BackoffStrategy;
import org.camunda.bpm.client.backoff.ErrorAwareBackoffStrategy;
import org.camunda.bpm.client.exception.ExternalTaskClientException;
import org.camunda.bpm.client.impl.EngineClient;
import org.camunda.bpm.client.impl.EngineClientException;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.client.task.impl.ExternalTaskImpl;
import org.camunda.bpm.client.topic.TopicSubscription;
import org.camunda.bpm.client.variable.impl.TypedValues;
import org.junit.Before;
import org.junit.Test;

public class TopicSubscriptionManagerTest {

  protected EngineClient engineClient;
  protected TypedValues typedValues;
  protected ExternalTaskHandler handler;
  protected TopicSubscriptionManager manager;

  @Before
  public void setUp() {
    engineClient = mock(EngineClient.class);
    typedValues = mock(TypedValues.class);
    handler = mock(ExternalTaskHandler.class);
    manager = new TopicSubscriptionManager(engineClient, typedValues, 5000L);
    manager.disableBackoffStrategy();
  }

  protected TopicSubscriptionImpl subscription(String topic) {
    return new TopicSubscriptionImpl(topic, null, handler, manager, null, null);
  }

  protected ExternalTaskImpl task(String topic) {
    ExternalTaskImpl task = new ExternalTaskImpl();
    task.setTopicName(topic);
    return task;
  }

  @Test
  public void shouldRejectDuplicateTopicSubscription() {
    manager.subscribe(subscription("a"));

    assertThatThrownBy(() -> manager.subscribe(subscription("a"))).isInstanceOf(ExternalTaskClientException.class);
    assertThat(manager.getSubscriptions()).hasSize(1);
  }

  @Test
  public void shouldRemoveSubscriptionOnUnsubscribe() {
    TopicSubscriptionImpl sub = subscription("a");
    manager.subscribe(sub);

    manager.unsubscribe(sub);

    assertThat(manager.getSubscriptions()).isEmpty();
    assertThat(manager.getEngineClient()).isSameAs(engineClient);
  }

  @Test
  public void shouldSkipFetchWithoutSubscriptions() {
    manager.acquire();

    verify(engineClient, never()).fetchAndLock(anyList());
  }

  @Test
  public void shouldDispatchFetchedTaskToHandlerOfItsTopic() throws Exception {
    manager.subscribe(subscription("a"));
    ExternalTaskImpl fetched = task("a");
    when(engineClient.fetchAndLock(anyList())).thenReturn(Collections.<ExternalTask>singletonList(fetched));

    manager.acquire();

    verify(handler).execute(any(ExternalTask.class), any(ExternalTaskService.class));
    verify(typedValues).wrapVariables(fetched, null);
  }

  @Test
  public void shouldIgnoreTaskWithoutHandlerForItsTopic() throws Exception {
    manager.subscribe(subscription("a"));
    when(engineClient.fetchAndLock(anyList())).thenReturn(Collections.<ExternalTask>singletonList(task("other")));

    manager.acquire();

    verify(handler, never()).execute(any(), any());
  }

  @Test
  public void shouldSurviveHandlerFailures() throws Exception {
    manager.subscribe(subscription("a"));
    doThrow(new IllegalStateException("boom")).doThrow(new ExternalTaskClientException("client", null))
        .when(handler).execute(any(), any());
    when(engineClient.fetchAndLock(anyList())).thenReturn(Collections.<ExternalTask>singletonList(task("a")));

    manager.acquire();
    manager.acquire();

    verify(handler, org.mockito.Mockito.times(2)).execute(any(), any());
  }

  @Test
  public void shouldReportEngineFailureAsResponseError() {
    when(engineClient.fetchAndLock(anyList())).thenThrow(new EngineClientException("fail", new java.io.IOException("down")) { });

    org.camunda.bpm.client.topic.impl.dto.FetchAndLockResponseDto response = manager.fetchAndLock(Collections.emptyList());

    assertThat(response.hasError()).isTrue();
    assertThat(response.getError()).isNotNull();
    assertThat(response.getExternalTasks()).isEmpty();
  }

  @Test
  public void shouldRunPlainBackoffStrategyAfterAcquisition() {
    BackoffStrategy strategy = mock(BackoffStrategy.class);
    manager = new TopicSubscriptionManager(engineClient, typedValues, 5000L);
    manager.setBackoffStrategy(strategy);
    manager.subscribe(subscription("a"));
    List<ExternalTask> tasks = Collections.emptyList();
    when(engineClient.fetchAndLock(anyList())).thenReturn(tasks);

    manager.acquire();

    verify(strategy).reconfigure(tasks);
    verify(strategy).calculateBackoffTime();
  }

  @Test
  public void shouldPassFetchErrorToErrorAwareBackoffStrategy() {
    ErrorAwareBackoffStrategy strategy = mock(ErrorAwareBackoffStrategy.class);
    manager = new TopicSubscriptionManager(engineClient, typedValues, 5000L);
    manager.setBackoffStrategy(strategy);
    manager.subscribe(subscription("a"));
    when(engineClient.fetchAndLock(anyList())).thenThrow(new EngineClientException("fail", new RuntimeException()) { });

    manager.acquire();

    verify(strategy).reconfigure(any(), any(ExternalTaskClientException.class));
  }

  @Test
  public void shouldSwallowBackoffStrategyFailures() {
    BackoffStrategy strategy = mock(BackoffStrategy.class);
    doThrow(new IllegalStateException("bad strategy")).when(strategy).reconfigure(any());
    manager = new TopicSubscriptionManager(engineClient, typedValues, 5000L);
    manager.setBackoffStrategy(strategy);
    manager.subscribe(subscription("a"));
    when(engineClient.fetchAndLock(anyList())).thenReturn(Collections.<ExternalTask>emptyList());

    manager.acquire();

    verify(strategy, never()).calculateBackoffTime();
  }

  @Test
  public void shouldStartPollAndStopCleanly() throws Exception {
    manager.subscribe(subscription("a"));
    java.util.concurrent.CountDownLatch polled = new java.util.concurrent.CountDownLatch(1);
    when(engineClient.fetchAndLock(anyList())).thenAnswer(invocation -> {
      polled.countDown();
      return Collections.<ExternalTask>emptyList();
    });

    manager.start();
    manager.start();
    assertThat(polled.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    assertThat(manager.isRunning()).isTrue();
    manager.stop();
    manager.stop();

    assertThat(manager.isRunning()).isFalse();
  }

  @Test
  public void shouldKeepRunningWhenAcquisitionThrows() throws Exception {
    manager.subscribe(subscription("a"));
    java.util.concurrent.CountDownLatch failedTwice = new java.util.concurrent.CountDownLatch(2);
    when(engineClient.fetchAndLock(anyList())).thenAnswer(invocation -> {
      failedTwice.countDown();
      throw new IllegalStateException("unexpected");
    });

    manager.start();
    assertThat(failedTwice.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    manager.stop();

    assertThat(manager.isRunning()).isFalse();
  }

  @Test
  public void shouldNotWaitForNonPositiveOrStoppedSuspension() {
    manager.suspend(0L);
    manager.suspend(10_000L);

    assertThat(manager.isRunning()).isFalse();
  }

}
