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
package org.camunda.bpm.client.task;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Map;

import org.camunda.bpm.client.exception.ExternalTaskClientException;
import org.camunda.bpm.client.impl.EngineClient;
import org.camunda.bpm.client.impl.EngineClientException;
import org.camunda.bpm.client.task.impl.ExternalTaskImpl;
import org.camunda.bpm.client.task.impl.ExternalTaskServiceImpl;
import org.junit.Before;
import org.junit.Test;

public class ExternalTaskServiceImplTest {

  protected EngineClient engineClient;
  protected ExternalTaskServiceImpl service;
  protected ExternalTask task;
  protected Map<String, Object> vars = Collections.<String, Object>singletonMap("a", 1);
  protected Map<String, Object> local = Collections.<String, Object>singletonMap("b", 2);

  @Before
  public void setUp() {
    engineClient = mock(EngineClient.class);
    service = new ExternalTaskServiceImpl(engineClient);
    ExternalTaskImpl impl = new ExternalTaskImpl();
    impl.setId("t1");
    impl.setProcessInstanceId("p1");
    task = impl;
  }

  @Test
  public void shouldDelegateLock() {
    service.lock(task, 10L);
    service.lock("t2", 20L);

    verify(engineClient).lock("t1", 10L);
    verify(engineClient).lock("t2", 20L);
  }

  @Test
  public void shouldDelegateUnlock() {
    service.unlock(task);

    verify(engineClient).unlock("t1");
  }

  @Test
  public void shouldDelegateCompleteOverloads() {
    service.complete(task);
    service.complete(task, vars);
    service.complete(task, vars, local);
    service.complete("t2", vars, local);

    verify(engineClient).complete("t1", null, null);
    verify(engineClient).complete("t1", vars, null);
    verify(engineClient).complete("t1", vars, local);
    verify(engineClient).complete("t2", vars, local);
  }

  @Test
  public void shouldDelegateSetVariables() {
    service.setVariables("p9", vars);
    service.setVariables(task, vars);

    verify(engineClient).setVariables("p9", vars);
    verify(engineClient).setVariables("p1", vars);
  }

  @Test
  public void shouldDelegateHandleFailureOverloads() {
    service.handleFailure(task, "m", "d", 1, 2L);
    service.handleFailure("t2", "m", "d", 3, 4L);
    service.handleFailure("t3", "m", "d", 5, 6L, vars, local);

    verify(engineClient).failure("t1", "m", "d", 1, 2L, null, null);
    verify(engineClient).failure("t2", "m", "d", 3, 4L, null, null);
    verify(engineClient).failure("t3", "m", "d", 5, 6L, vars, local);
  }

  @Test
  public void shouldDelegateHandleBpmnErrorOverloads() {
    service.handleBpmnError(task, "C");
    service.handleBpmnError(task, "C", "msg");
    service.handleBpmnError(task, "C", "msg", vars);
    service.handleBpmnError("t2", "C2", "msg2", vars);

    verify(engineClient).bpmnError("t1", "C", null, null);
    verify(engineClient).bpmnError("t1", "C", "msg", null);
    verify(engineClient).bpmnError("t1", "C", "msg", vars);
    verify(engineClient).bpmnError("t2", "C2", "msg2", vars);
  }

  @Test
  public void shouldDelegateExtendLock() {
    service.extendLock(task, 5L);
    service.extendLock("t2", 6L);

    verify(engineClient).extendLock("t1", 5L);
    verify(engineClient).extendLock("t2", 6L);
  }

  @Test
  public void shouldWrapEngineClientFailuresOfEveryOperation() {
    EngineClientException failure = new EngineClientException("boom", new RuntimeException("cause")) { };
    doThrow(failure).when(engineClient).lock(anyString(), anyLong());
    doThrow(failure).when(engineClient).unlock(anyString());
    doThrow(failure).when(engineClient).setVariables(anyString(), anyMap());
    doThrow(failure).when(engineClient).complete(anyString(), isNull(), isNull());
    doThrow(failure).when(engineClient).failure(anyString(), anyString(), anyString(), anyInt(), anyLong(), isNull(), isNull());
    doThrow(failure).when(engineClient).bpmnError(anyString(), anyString(), isNull(), isNull());
    doThrow(failure).when(engineClient).extendLock(anyString(), anyLong());

    assertThatThrownBy(() -> service.lock("t", 1L)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> service.unlock(task)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> service.setVariables("p", vars)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> service.setVariables(task, vars)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> service.complete(task)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> service.handleFailure("t", "m", "d", 1, 1L)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> service.handleBpmnError("t", "c", null, null)).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> service.extendLock("t", 1L)).isInstanceOf(ExternalTaskClientException.class);
  }

}
