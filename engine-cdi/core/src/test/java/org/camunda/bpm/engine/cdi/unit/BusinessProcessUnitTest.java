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
package org.camunda.bpm.engine.cdi.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.Conversation;
import javax.enterprise.inject.Instance;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.cdi.BusinessProcess;
import org.camunda.bpm.engine.cdi.ProcessEngineCdiException;
import org.camunda.bpm.engine.cdi.impl.context.ContextAssociationManager;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class BusinessProcessUnitTest {

  @Mock private ProcessEngine processEngine;
  @Mock private ContextAssociationManager associationManager;
  @Mock private Instance<Conversation> conversationInstance;
  @Mock private Conversation conversation;
  @Mock private RuntimeService runtimeService;
  @Mock private TaskService taskService;
  @InjectMocks private BusinessProcess businessProcess;

  private VariableMap cached;
  private VariableMap cachedLocal;

  @Before
  public void setUp() {
    when(processEngine.getRuntimeService()).thenReturn(runtimeService);
    when(processEngine.getTaskService()).thenReturn(taskService);
    when(conversationInstance.get()).thenReturn(conversation);
    cached = Variables.createVariables().putValue("a", 1);
    cachedLocal = Variables.createVariables().putValue("l", 2);
    when(associationManager.getCachedVariables()).thenReturn(cached);
    when(associationManager.getCachedLocalVariables()).thenReturn(cachedLocal);
  }

  private ProcessInstance instance(boolean ended) {
    ProcessInstance instance = mock(ProcessInstance.class);
    when(instance.isEnded()).thenReturn(ended);
    when(instance.getId()).thenReturn("pi-1");
    return instance;
  }

  private void stubExecutionLookup(String id, Execution execution) {
    org.camunda.bpm.engine.runtime.ExecutionQuery query = mock(org.camunda.bpm.engine.runtime.ExecutionQuery.class);
    when(runtimeService.createExecutionQuery()).thenReturn(query);
    when(query.executionId(id)).thenReturn(query);
    when(query.singleResult()).thenReturn(execution);
  }

  private void stubTaskLookup(String id, Task task) {
    org.camunda.bpm.engine.task.TaskQuery query = mock(org.camunda.bpm.engine.task.TaskQuery.class);
    when(taskService.createTaskQuery()).thenReturn(query);
    when(query.taskId(id)).thenReturn(query);
    when(query.singleResult()).thenReturn(task);
  }

  // ---- start process ----

  @Test
  public void startProcessByIdVariants() {
    ProcessInstance running = instance(false);
    when(runtimeService.startProcessInstanceById(eq("def"), any(VariableMap.class))).thenReturn(running);
    when(runtimeService.startProcessInstanceById(eq("def"), eq("bk"), any(VariableMap.class))).thenReturn(running);
    stubExecutionLookup("pi-1", running);

    assertThat(businessProcess.startProcessById("def")).isSameAs(running);
    assertThat(businessProcess.startProcessById("def", "bk")).isSameAs(running);
    Map<String, Object> vars = new HashMap<>();
    vars.put("x", "y");
    assertThat(businessProcess.startProcessById("def", vars)).isSameAs(running);
    assertThat(businessProcess.startProcessById("def", "bk", vars)).isSameAs(running);

    verify(associationManager, Mockito.times(4)).setExecution(running);
  }

  @Test
  public void startProcessByIdMergesVariablesIntoRuntimeCall() {
    ProcessInstance ended = instance(true);
    when(runtimeService.startProcessInstanceById(eq("def"), any(VariableMap.class))).thenReturn(ended);
    Map<String, Object> vars = new HashMap<>();
    vars.put("x", "y");

    businessProcess.startProcessById("def", vars);

    org.mockito.ArgumentCaptor<VariableMap> captor = org.mockito.ArgumentCaptor.forClass(VariableMap.class);
    verify(runtimeService).startProcessInstanceById(eq("def"), captor.capture());
    assertThat(captor.getValue()).containsEntry("x", "y").containsEntry("a", 1);
    assertThat(cached).isEmpty();
  }

  @Test
  public void startProcessByKeyVariants() {
    ProcessInstance running = instance(false);
    when(runtimeService.startProcessInstanceByKey(eq("key"), any(VariableMap.class))).thenReturn(running);
    when(runtimeService.startProcessInstanceByKey(eq("key"), eq("bk"), any(VariableMap.class))).thenReturn(running);
    stubExecutionLookup("pi-1", running);

    assertThat(businessProcess.startProcessByKey("key")).isSameAs(running);
    assertThat(businessProcess.startProcessByKey("key", "bk")).isSameAs(running);
    assertThat(businessProcess.startProcessByKey("key", new HashMap<String, Object>())).isSameAs(running);
    assertThat(businessProcess.startProcessByKey("key", "bk", new HashMap<String, Object>())).isSameAs(running);
  }

  @Test
  public void startProcessByKeyDoesNotAssociateEndedInstance() {
    ProcessInstance ended = instance(true);
    when(runtimeService.startProcessInstanceByKey(eq("key"), any(VariableMap.class))).thenReturn(ended);

    businessProcess.startProcessByKey("key");

    verify(associationManager, never()).setExecution(any());
  }

  @Test
  public void startProcessByMessageVariants() {
    ProcessInstance running = instance(false);
    ProcessInstance ended = instance(true);
    when(runtimeService.startProcessInstanceByMessage(eq("msg"), any(Map.class))).thenReturn(running);
    when(runtimeService.startProcessInstanceByMessage(eq("msg"), eq("bk"), any(Map.class))).thenReturn(ended);
    stubExecutionLookup("pi-1", running);

    assertThat(businessProcess.startProcessByMessage("msg")).isSameAs(running);
    assertThat(businessProcess.startProcessByMessage("msg", new HashMap<String, Object>())).isSameAs(running);
    assertThat(businessProcess.startProcessByMessage("msg", "bk", Map.of("k", "v"))).isSameAs(ended);

    verify(associationManager, Mockito.times(2)).setExecution(running);
    verify(associationManager, never()).setExecution(ended);
  }

  @Test
  public void startMethodsRejectActiveCommandContext() {
    Context.setCommandContext(mock(org.camunda.bpm.engine.impl.interceptor.CommandContext.class));
    try {
      assertThatThrownBy(() -> businessProcess.startProcessById("def"))
        .isInstanceOf(ProcessEngineCdiException.class).hasMessageContaining("active command context");
      assertThatThrownBy(() -> businessProcess.saveTask()).isInstanceOf(ProcessEngineCdiException.class);
      assertThatThrownBy(() -> businessProcess.stopTask()).isInstanceOf(ProcessEngineCdiException.class);
    } finally {
      Context.removeCommandContext();
    }
  }

  // ---- association ----

  @Test
  public void associateExecutionByIdSetsExecution() {
    Execution execution = mock(Execution.class);
    stubExecutionLookup("e1", execution);

    businessProcess.associateExecutionById("e1");

    verify(associationManager).setExecution(execution);
  }

  @Test
  public void associateExecutionByIdFailsWhenMissing() {
    stubExecutionLookup("nope", null);

    assertThatThrownBy(() -> businessProcess.associateExecutionById("nope"))
      .isInstanceOf(ProcessEngineCdiException.class).hasMessageContaining("nope");
  }

  @Test
  public void isAssociatedReflectsExecutionId() {
    assertThat(businessProcess.isAssociated()).isFalse();
    when(associationManager.getExecutionId()).thenReturn("e1");
    assertThat(businessProcess.isAssociated()).isTrue();
  }

  @Test
  public void setExecutionAndSetExecutionIdDelegateToAssociation() {
    Execution execution = mock(Execution.class);
    when(execution.getId()).thenReturn("e1");
    stubExecutionLookup("e1", execution);

    businessProcess.setExecution(execution);
    verify(associationManager).setExecution(execution);
  }

  // ---- signal ----

  @Test
  public void signalExecutionRequiresAssociation() {
    assertThatThrownBy(() -> businessProcess.signalExecution())
      .isInstanceOf(ProcessEngineCdiException.class).hasMessageContaining("No execution associated");
  }

  @Test
  public void signalExecutionFlushesVariablesAndDisassociates() {
    Execution execution = mock(Execution.class);
    when(associationManager.getExecution()).thenReturn(execution);
    when(associationManager.getExecutionId()).thenReturn("e1");

    businessProcess.signalExecution();

    verify(runtimeService).setVariablesLocal(eq("e1"), any(Map.class));
    verify(runtimeService).signal(eq("e1"), any(Map.class));
    verify(associationManager).disAssociate();
    assertThat(cached).isEmpty();
    assertThat(cachedLocal).isEmpty();
  }

  @Test
  public void signalExecutionEndsConversationOnlyWhenAsked() {
    when(associationManager.getExecution()).thenReturn(mock(Execution.class));
    when(associationManager.getExecutionId()).thenReturn("e1");

    businessProcess.signalExecution(false);
    verify(conversation, never()).end();

    businessProcess.signalExecution(true);
    verify(conversation).end();
  }

  // ---- tasks ----

  @Test
  public void startTaskReturnsCurrentTaskWhenAlreadyAssociated() {
    Task current = mock(Task.class);
    when(current.getId()).thenReturn("t1");
    when(associationManager.getTask()).thenReturn(current);

    assertThat(businessProcess.startTask("t1")).isSameAs(current);
    verify(taskService, never()).createTaskQuery();
  }

  @Test
  public void startTaskFailsWhenTaskMissing() {
    stubTaskLookup("t9", null);

    assertThatThrownBy(() -> businessProcess.startTask("t9"))
      .isInstanceOf(ProcessEngineCdiException.class).hasMessageContaining("t9");
  }

  @Test
  public void startTaskAssociatesTaskAndExecution() {
    Task task = mock(Task.class);
    when(task.getExecutionId()).thenReturn("e1");
    stubTaskLookup("t1", task);
    Execution execution = mock(Execution.class);
    stubExecutionLookup("e1", execution);

    assertThat(businessProcess.startTask("t1")).isSameAs(task);

    verify(associationManager).setTask(task);
    verify(associationManager).setExecution(execution);
  }

  @Test
  public void startTaskBeginsConversationOnlyWhenTransient() {
    Task task = mock(Task.class);
    when(task.getExecutionId()).thenReturn("e1");
    stubTaskLookup("t1", task);
    stubExecutionLookup("e1", mock(Execution.class));

    when(conversation.isTransient()).thenReturn(true);
    businessProcess.startTask("t1", true);
    verify(conversation).begin();

    when(conversation.isTransient()).thenReturn(false);
    businessProcess.startTask("t1", true);
    verify(conversation, Mockito.times(1)).begin();

    businessProcess.startTask("t1", false);
    verify(conversation, Mockito.times(1)).begin();
  }

  @Test
  public void setTaskAndSetTaskIdStartTask() {
    Task task = mock(Task.class);
    when(task.getId()).thenReturn("t1");
    when(associationManager.getTask()).thenReturn(task);

    businessProcess.setTask(task);
    businessProcess.setTaskId("t1");

    verify(taskService, never()).createTaskQuery();
  }

  @Test
  public void completeTaskRequiresTask() {
    assertThatThrownBy(() -> businessProcess.completeTask())
      .isInstanceOf(ProcessEngineCdiException.class).hasMessageContaining("No task associated");
  }

  @Test
  public void completeTaskPushesVariablesCompletesAndDisassociates() {
    Task task = mock(Task.class);
    when(task.getId()).thenReturn("t1");
    when(associationManager.getTask()).thenReturn(task);

    businessProcess.completeTask(true);

    verify(taskService).setVariablesLocal(eq("t1"), any(Map.class));
    verify(taskService).setVariables(eq("t1"), any(Map.class));
    verify(taskService).complete("t1");
    verify(associationManager).disAssociate();
    verify(conversation).end();
  }

  @Test
  public void completeTaskWithoutEndingConversation() {
    Task task = mock(Task.class);
    when(task.getId()).thenReturn("t1");
    when(associationManager.getTask()).thenReturn(task);

    businessProcess.completeTask(false);

    verify(conversation, never()).end();
  }

  @Test
  public void saveTaskPersistsAssociatedTask() {
    Task task = mock(Task.class);
    when(associationManager.getTask()).thenReturn(task);

    businessProcess.saveTask();

    verify(taskService).saveTask(task);
  }

  @Test
  public void saveTaskRequiresTask() {
    assertThatThrownBy(() -> businessProcess.saveTask()).isInstanceOf(ProcessEngineCdiException.class);
  }

  @Test
  public void stopTaskDisassociatesAndOptionallyEndsConversation() {
    when(associationManager.getTask()).thenReturn(mock(Task.class));

    businessProcess.stopTask(false);
    verify(conversation, never()).end();
    businessProcess.stopTask(true);
    verify(conversation).end();
    verify(associationManager, Mockito.times(2)).disAssociate();
  }

  @Test
  public void stopTaskRequiresTask() {
    assertThatThrownBy(() -> businessProcess.stopTask()).isInstanceOf(ProcessEngineCdiException.class);
  }

  @Test
  public void isTaskAssociatedReflectsTask() {
    assertThat(businessProcess.isTaskAssociated()).isFalse();
    when(associationManager.getTask()).thenReturn(mock(Task.class));
    assertThat(businessProcess.isTaskAssociated()).isTrue();
  }

  // ---- variables ----

  @Test
  public void getVariableUnwrapsTypedValue() {
    TypedValue typed = Variables.stringValue("hello");
    when(associationManager.getVariable("v")).thenReturn(typed);

    String value = businessProcess.getVariable("v");
    assertThat(value).isEqualTo("hello");
    assertThat((TypedValue) businessProcess.getVariableTyped("v")).isSameAs(typed);
  }

  @Test
  public void getVariableReturnsNullWhenMissingOrNullValued() {
    assertThat((Object) businessProcess.getVariable("missing")).isNull();
    assertThat((Object) businessProcess.getVariableTyped("missing")).isNull();
    when(associationManager.getVariable("n")).thenReturn(Variables.untypedNullValue());
    assertThat((Object) businessProcess.getVariable("n")).isNull();
  }

  @Test
  public void getVariableLocalUnwrapsTypedValue() {
    TypedValue typed = Variables.integerValue(5);
    when(associationManager.getVariableLocal("v")).thenReturn(typed);

    Integer value = businessProcess.getVariableLocal("v");
    assertThat(value).isEqualTo(5);
    assertThat((TypedValue) businessProcess.getVariableLocalTyped("v")).isSameAs(typed);
  }

  @Test
  public void getVariableLocalReturnsNullWhenMissingOrNullValued() {
    assertThat((Object) businessProcess.getVariableLocal("missing")).isNull();
    assertThat((Object) businessProcess.getVariableLocalTyped("missing")).isNull();
    when(associationManager.getVariableLocal("n")).thenReturn(Variables.untypedNullValue());
    assertThat((Object) businessProcess.getVariableLocal("n")).isNull();
  }

  @Test
  public void settersDelegateToAssociationManager() {
    businessProcess.setVariable("a", 1);
    businessProcess.setVariableLocal("b", 2);
    businessProcess.flushVariableCache();

    verify(associationManager).setVariable("a", 1);
    verify(associationManager).setVariableLocal("b", 2);
    verify(associationManager).flushVariableCache();
  }

  @Test
  public void cachedVariableMapsAreCopies() {
    assertThat(businessProcess.getCachedVariableMap()).containsEntry("a", 1);
    assertThat(businessProcess.getVariableCache()).containsEntry("a", 1);
    assertThat(businessProcess.getCachedLocalVariableMap()).containsEntry("l", 2);
    assertThat(businessProcess.getVariableLocalCache()).containsEntry("l", 2);
    assertThat(businessProcess.getCachedVariableMap()).isNotSameAs(cached);
    assertThat(cached).isNotEmpty();
  }

  @Test
  public void getAndClearReturnsCopyAndEmptiesCache() {
    assertThat(businessProcess.getAndClearVariableCache()).containsEntry("a", 1);
    assertThat(cached).isEmpty();
    assertThat(businessProcess.getAndClearVariableLocalCache()).containsEntry("l", 2);
    assertThat(cachedLocal).isEmpty();
  }

  // ---- getters ----

  @Test
  public void gettersReturnNullWithoutAssociation() {
    assertThat(businessProcess.getProcessInstanceId()).isNull();
    assertThat(businessProcess.getTaskId()).isNull();
    assertThat(businessProcess.getExecutionId()).isNull();
    assertThat(businessProcess.getExecution()).isNull();
    assertThat(businessProcess.getTask()).isNull();
    assertThat(businessProcess.getProcessInstance()).isNull();
  }

  @Test
  public void gettersReflectAssociation() {
    Task task = mock(Task.class);
    when(task.getId()).thenReturn("t1");
    Execution execution = mock(Execution.class);
    when(execution.getId()).thenReturn("e1");
    when(execution.getProcessInstanceId()).thenReturn("pi");
    when(associationManager.getTask()).thenReturn(task);
    when(associationManager.getExecution()).thenReturn(execution);

    assertThat(businessProcess.getTaskId()).isEqualTo("t1");
    assertThat(businessProcess.getExecutionId()).isEqualTo("e1");
    assertThat(businessProcess.getProcessInstanceId()).isEqualTo("pi");
    assertThat(businessProcess.getTask()).isSameAs(task);
    assertThat(businessProcess.getExecution()).isSameAs(execution);
  }

  @Test
  public void getProcessInstanceQueriesWhenExecutionIsChild() {
    Execution child = mock(Execution.class);
    when(child.getId()).thenReturn("child");
    when(child.getProcessInstanceId()).thenReturn("pi");
    when(associationManager.getExecution()).thenReturn(child);
    ProcessInstance instance = mock(ProcessInstance.class);
    org.camunda.bpm.engine.runtime.ProcessInstanceQuery query = mock(org.camunda.bpm.engine.runtime.ProcessInstanceQuery.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
    when(query.processInstanceId("pi")).thenReturn(query);
    when(query.singleResult()).thenReturn(instance);

    assertThat(businessProcess.getProcessInstance()).isSameAs(instance);
  }

  @Test
  public void getProcessInstanceCastsExecutionWhenItIsTheInstance() {
    ProcessInstance root = mock(ProcessInstance.class);
    when(root.getId()).thenReturn("pi");
    when(root.getProcessInstanceId()).thenReturn("pi");
    when(associationManager.getExecution()).thenReturn(root);

    assertThat(businessProcess.getProcessInstance()).isSameAs(root);
    verify(runtimeService, never()).createProcessInstanceQuery();
  }
}
