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
package org.camunda.bpm.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.client.UrlResolver;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.impl.ExternalTaskImpl;
import org.camunda.bpm.client.task.impl.dto.BpmnErrorRequestDto;
import org.camunda.bpm.client.task.impl.dto.CompleteRequestDto;
import org.camunda.bpm.client.task.impl.dto.ExtendLockRequestDto;
import org.camunda.bpm.client.task.impl.dto.FailureRequestDto;
import org.camunda.bpm.client.task.impl.dto.LockRequestDto;
import org.camunda.bpm.client.task.impl.dto.SetVariablesRequestDto;
import org.camunda.bpm.client.topic.impl.dto.FetchAndLockRequestDto;
import org.camunda.bpm.client.variable.impl.TypedValueField;
import org.camunda.bpm.client.variable.impl.TypedValues;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class EngineClientTest {

  protected static final String BASE = "http://engine/rest";

  protected RequestExecutor executor;
  protected TypedValues typedValues;
  protected EngineClient client;

  @Before
  public void setUp() {
    executor = mock(RequestExecutor.class);
    typedValues = mock(TypedValues.class);
    client = new EngineClient("worker", 3, 1000L, BASE, executor);
    client.setTypedValues(typedValues);
  }

  @Test
  public void shouldFetchAndLockWithConfiguredPayload() {
    ExternalTaskImpl task = new ExternalTaskImpl();
    when(executor.postRequest(eq(BASE + "/external-task/fetchAndLock"), any(), eq(ExternalTaskImpl[].class)))
        .thenReturn(new ExternalTaskImpl[] { task });

    List<ExternalTask> result = client.fetchAndLock(Collections.emptyList());

    assertThat(result).containsExactly(task);
    ArgumentCaptor<FetchAndLockRequestDto> captor = ArgumentCaptor.forClass(FetchAndLockRequestDto.class);
    verify(executor).postRequest(eq(BASE + "/external-task/fetchAndLock"), captor.capture(), eq(ExternalTaskImpl[].class));
    assertThat(captor.getValue().getMaxTasks()).isEqualTo(3);
    assertThat(captor.getValue().getAsyncResponseTimeout()).isEqualTo(1000L);
    assertThat(captor.getValue().isUsePriority()).isTrue();
    assertThat(captor.getValue().getWorkerId()).isEqualTo("worker");
    assertThat(captor.getValue().getSorting()).isEmpty();
  }

  @Test
  public void shouldLockTask() {
    client.lock("42", 500L);

    ArgumentCaptor<LockRequestDto> captor = ArgumentCaptor.forClass(LockRequestDto.class);
    verify(executor).postRequest(eq(BASE + "/external-task/42/lock"), captor.capture(), eq(Void.class));
    assertThat(captor.getValue().getLockDuration()).isEqualTo(500L);
    assertThat(captor.getValue().getWorkerId()).isEqualTo("worker");
  }

  @Test
  public void shouldUnlockTaskWithoutPayload() {
    client.unlock("42");

    verify(executor).postRequest(eq(BASE + "/external-task/42/unlock"), isNull(), eq(Void.class));
  }

  @Test
  public void shouldCompleteTaskWithSerializedVariables() {
    Map<String, Object> vars = Collections.<String, Object>singletonMap("a", 1);
    Map<String, Object> local = Collections.<String, Object>singletonMap("b", 2);
    Map<String, TypedValueField> serializedVars = Collections.singletonMap("a", new TypedValueField());
    Map<String, TypedValueField> serializedLocal = Collections.singletonMap("b", new TypedValueField());
    when(typedValues.serializeVariables(vars)).thenReturn(serializedVars);
    when(typedValues.serializeVariables(local)).thenReturn(serializedLocal);

    client.complete("42", vars, local);

    ArgumentCaptor<CompleteRequestDto> captor = ArgumentCaptor.forClass(CompleteRequestDto.class);
    verify(executor).postRequest(eq(BASE + "/external-task/42/complete"), captor.capture(), eq(Void.class));
    assertThat(captor.getValue().getVariables()).isSameAs(serializedVars);
    assertThat(captor.getValue().getLocalVariables()).isSameAs(serializedLocal);
  }

  @Test
  public void shouldSetVariablesOnProcessInstance() {
    Map<String, Object> vars = Collections.<String, Object>singletonMap("a", 1);
    Map<String, TypedValueField> serialized = Collections.singletonMap("a", new TypedValueField());
    when(typedValues.serializeVariables(vars)).thenReturn(serialized);

    client.setVariables("proc1", vars);

    ArgumentCaptor<SetVariablesRequestDto> captor = ArgumentCaptor.forClass(SetVariablesRequestDto.class);
    verify(executor).postRequest(eq(BASE + "/process-instance/proc1/variables"), captor.capture(), eq(Void.class));
    assertThat(captor.getValue().getModifications()).isSameAs(serialized);
  }

  @Test
  public void shouldReportFailure() {
    client.failure("42", "msg", "details", 2, 3000L, null, null);

    ArgumentCaptor<FailureRequestDto> captor = ArgumentCaptor.forClass(FailureRequestDto.class);
    verify(executor).postRequest(eq(BASE + "/external-task/42/failure"), captor.capture(), eq(Void.class));
    FailureRequestDto dto = captor.getValue();
    assertThat(dto.getErrorMessage()).isEqualTo("msg");
    assertThat(dto.getErrorDetails()).isEqualTo("details");
    assertThat(dto.getRetries()).isEqualTo(2);
    assertThat(dto.getRetryTimeout()).isEqualTo(3000L);
  }

  @Test
  public void shouldReportBpmnError() {
    client.bpmnError("42", "CODE", "message", null);

    ArgumentCaptor<BpmnErrorRequestDto> captor = ArgumentCaptor.forClass(BpmnErrorRequestDto.class);
    verify(executor).postRequest(eq(BASE + "/external-task/42/bpmnError"), captor.capture(), eq(Void.class));
    assertThat(captor.getValue().getErrorCode()).isEqualTo("CODE");
    assertThat(captor.getValue().getErrorMessage()).isEqualTo("message");
  }

  @Test
  public void shouldExtendLock() {
    client.extendLock("42", 9000L);

    ArgumentCaptor<ExtendLockRequestDto> captor = ArgumentCaptor.forClass(ExtendLockRequestDto.class);
    verify(executor).postRequest(eq(BASE + "/external-task/42/extendLock"), captor.capture(), eq(Void.class));
    assertThat(captor.getValue().getNewDuration()).isEqualTo(9000L);
  }

  @Test
  public void shouldLoadLocalBinaryVariable() {
    byte[] data = { 1, 2, 3 };
    when(executor.getRequest(BASE + "/process-instance/exec1/variables/file/data")).thenReturn(data);

    assertThat(client.getLocalBinaryVariable("file", "exec1")).isEqualTo(data);
  }

  @Test
  public void shouldExposeConfigurationAndHonourUrlResolver() {
    UrlResolver resolver = () -> "http://other";
    EngineClient custom = new EngineClient("w2", 5, null, resolver, executor);

    assertThat(custom.getBaseUrl()).isEqualTo("http://other");
    assertThat(custom.getWorkerId()).isEqualTo("w2");
    assertThat(client.getBaseUrl()).isEqualTo(BASE);
    assertThat(client.getWorkerId()).isEqualTo("worker");
  }

}
