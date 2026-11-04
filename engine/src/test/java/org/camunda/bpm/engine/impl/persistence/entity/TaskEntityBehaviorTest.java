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
package org.camunda.bpm.engine.impl.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.task.DelegationState;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

/** Behaviour of {@link TaskEntity} that does not depend on a running process: state, tenants, listeners. */
public class TaskEntityBehaviorTest {

  @Rule
  public ProcessEngineRule engineRule = new ProvidedProcessEngineRule();

  private TaskService taskService;

  @After
  public void cleanUp() {
    for (Task t : engineRule.getTaskService().createTaskQuery().list()) {
      engineRule.getTaskService().deleteTask(t.getId(), true);
    }
  }

  // plain-object behaviour ///////////////////////////////////////////////////

  @Test
  public void shouldRefuseTransitionIntoInitState() {
    TaskEntity task = new TaskEntity();
    task.setId("t1");

    assertThatThrownBy(() -> task.transitionTo(TaskEntity.TaskState.STATE_INIT))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("t1")
        .hasMessageContaining("cannot transition");
  }

  @Test
  public void shouldIgnoreUpdateEventUnlessTaskIsCreated() {
    TaskEntity task = new TaskEntity(TaskEntity.TaskState.STATE_INIT);

    assertThat(task.triggerUpdateEvent()).isTrue();
    assertThat(task.getTaskState()).isNotEqualTo("Updated");
  }

  @Test
  public void shouldMapDelegationStateFromAndToString() {
    TaskEntity task = new TaskEntity();

    task.setDelegationStateString("PENDING");
    assertThat(task.getDelegationState()).isEqualTo(DelegationState.PENDING);
    assertThat(task.getDelegationStateString()).isEqualTo("PENDING");

    task.setDelegationStateString("RESOLVED");
    assertThat(task.getDelegationState()).isEqualTo(DelegationState.RESOLVED);

    task.setDelegationStateString(null);
    assertThat(task.getDelegationState()).isNull();
    assertThat(task.getDelegationStateString()).isNull();
  }

  @Test
  public void shouldRejectUnknownDelegationState() {
    TaskEntity task = new TaskEntity();

    assertThatThrownBy(() -> task.setDelegationStateString("NOPE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldReturnEmptyActivityInstanceVariablesWithoutExecution() {
    Map<String, Object> variables = new TaskEntity().getActivityInstanceVariables();

    assertThat(variables).isEmpty();
  }

  @Test
  public void shouldHaveNoBpmnModelWithoutProcessDefinition() {
    TaskEntity task = new TaskEntity();

    assertThat(task.getBpmnModelInstance()).isNull();
    assertThat(task.getBpmnModelElementInstance()).isNull();
  }

  @Test
  public void shouldClearCaseReferencesWhenCaseExecutionIsRemoved() {
    TaskEntity task = new TaskEntity();
    task.setCaseExecutionId("ce");
    task.setCaseInstanceId("ci");
    task.setCaseDefinitionId("cd");
    task.setTenantId("tenant");

    task.setCaseExecution(null);

    assertThat(task.getCaseExecutionId()).isNull();
    assertThat(task.getCaseInstanceId()).isNull();
    assertThat(task.getCaseDefinitionId()).isNull();
    assertThat(task.getTenantId()).isNull();
  }

  @Test
  public void shouldKeepTenantWhenNoExecutionToPropagateFrom() {
    TaskEntity task = new TaskEntity();
    task.setTenantId("tenant");

    task.propagateExecutionTenantId(null);

    assertThat(task.getTenantId()).isEqualTo("tenant");
  }

  @Test
  public void shouldFailTimeoutEventWithoutMatchingTaskListener() {
    TaskEntity task = new TaskEntity();
    task.setId("t2");

    assertThatThrownBy(() -> task.triggerTimeoutEvent("missing"))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("missing");
  }

  @Test
  public void shouldIgnoreRemovalOfCustomListenerWhenNoneRegistered() {
    TaskEntity task = new TaskEntity();

    assertThat(task.removeCustomLifecycleListener(null)).isNull();
    assertThat(task.getVariableInstanceLifecycleListeners())
        .isSameAs(TaskEntity.DEFAULT_VARIABLE_LIFECYCLE_LISTENERS);
  }

  @Test
  public void shouldIgnoreNullIdentityIds() {
    TaskEntity task = new TaskEntity();

    // null ids are dropped before any identity link is created, so no command context is needed
    task.deleteGroupIdentityLink(null, "candidate");
    task.deleteUserIdentityLink(null, "candidate");
  }

  @Test
  public void shouldCompareByIdOnly() {
    TaskEntity a = new TaskEntity();
    a.setId("same");
    TaskEntity b = new TaskEntity();
    b.setId("same");
    TaskEntity c = new TaskEntity();
    c.setId("other");
    TaskEntity noId = new TaskEntity();

    assertThat(a).isEqualTo(a).isEqualTo(b).isNotEqualTo(c).isNotEqualTo(noId).isNotEqualTo(null).isNotEqualTo("x");
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(noId).isEqualTo(new TaskEntity());
    assertThat(noId.hashCode()).isEqualTo(new TaskEntity().hashCode());
  }

  @Test
  public void shouldResetFormKeyOnlyWhenTaskDefinitionKeyChanges() {
    TaskEntity task = new TaskEntity();
    task.setTaskDefinitionKey("a");
    task.isFormKeyInitialized = true;

    task.setTaskDefinitionKey("a");
    assertThat(task.isFormKeyInitialized).isTrue();

    task.setTaskDefinitionKey("b");
    assertThat(task.isFormKeyInitialized).isFalse();

    task.isFormKeyInitialized = true;
    task.setTaskDefinitionKey(null);
    assertThat(task.isFormKeyInitialized).isFalse();
  }

  @Test
  public void shouldCollectReferencedEntitiesOfCaseAndProcessTask() {
    TaskEntity task = new TaskEntity();
    task.setProcessDefinitionId("pd");
    task.setProcessInstanceId("pi");
    task.setExecutionId("ex");
    task.setCaseDefinitionId("cd");
    task.setCaseExecutionId("ce");

    Map<String, Class> refs = task.getReferencedEntitiesIdAndClass();

    assertThat(refs).containsEntry("pd", ProcessDefinitionEntity.class)
        .containsEntry("pi", ExecutionEntity.class)
        .containsEntry("ex", ExecutionEntity.class)
        .containsEntry("cd", org.camunda.bpm.engine.impl.cmmn.entity.repository.CaseDefinitionEntity.class)
        .containsEntry("ce", org.camunda.bpm.engine.impl.cmmn.entity.runtime.CaseExecutionEntity.class);
    assertThat(new TaskEntity().getReferencedEntitiesIdAndClass()).isEmpty();
  }

  // tenant handling through the engine ///////////////////////////////////////

  @Test
  public void shouldInheritTenantOfParentTaskForSubtask() {
    taskService = engineRule.getTaskService();
    Task parent = taskService.newTask();
    parent.setTenantId("tenantA");
    taskService.saveTask(parent);

    Task sub = taskService.newTask();
    sub.setParentTaskId(parent.getId());
    taskService.saveTask(sub);

    assertThat(taskService.createTaskQuery().taskId(sub.getId()).singleResult().getTenantId()).isEqualTo("tenantA");
  }

  @Test
  public void shouldRejectSubtaskWithDifferentTenantThanParent() {
    taskService = engineRule.getTaskService();
    Task parent = taskService.newTask();
    parent.setTenantId("tenantA");
    taskService.saveTask(parent);

    Task sub = taskService.newTask();
    sub.setParentTaskId(parent.getId());
    sub.setTenantId("tenantB");

    assertThatThrownBy(() -> taskService.saveTask(sub))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("tenantA")
        .hasMessageContaining("tenantB");
  }

  @Test
  public void shouldAcceptSubtaskWithSameTenantAsParent() {
    taskService = engineRule.getTaskService();
    Task parent = taskService.newTask();
    parent.setTenantId("tenantA");
    taskService.saveTask(parent);

    Task sub = taskService.newTask();
    sub.setParentTaskId(parent.getId());
    sub.setTenantId("tenantA");
    taskService.saveTask(sub);

    assertThat(taskService.createTaskQuery().taskId(sub.getId()).singleResult().getTenantId()).isEqualTo("tenantA");
  }

  @Test
  public void shouldRejectChangingTenantOfPersistedTask() {
    taskService = engineRule.getTaskService();
    Task task = taskService.newTask();
    task.setTenantId("tenantA");
    taskService.saveTask(task);

    Task persisted = taskService.createTaskQuery().taskId(task.getId()).singleResult();
    persisted.setTenantId("tenantB");

    assertThatThrownBy(() -> taskService.saveTask(persisted))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("tenantA");
  }

  @Test
  public void shouldRejectChangingTenantFromSetToNull() {
    taskService = engineRule.getTaskService();
    Task task = taskService.newTask();
    task.setTenantId("tenantA");
    taskService.saveTask(task);

    Task persisted = taskService.createTaskQuery().taskId(task.getId()).singleResult();
    persisted.setTenantId(null);

    assertThatThrownBy(() -> taskService.saveTask(persisted)).isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldKeepAssigneeNullAndRejectNullOwnerOnUnassignedTask() {
    taskService = engineRule.getTaskService();
    Task task = taskService.newTask();
    taskService.saveTask(task);

    taskService.setAssignee(task.getId(), null);
    assertThatThrownBy(() -> taskService.setOwner(task.getId(), null))
        .isInstanceOf(org.camunda.bpm.engine.exception.NullValueException.class);

    Task reloaded = taskService.createTaskQuery().taskId(task.getId()).singleResult();
    assertThat(reloaded.getAssignee()).isNull();
    assertThat(reloaded.getOwner()).isNull();
    assertThat(taskService.getIdentityLinksForTask(task.getId())).isEmpty();
  }

  @Test
  public void shouldAddAndRemoveCandidatesAndIgnoreRemovalOfUnknownOnes() {
    taskService = engineRule.getTaskService();
    Task task = taskService.newTask();
    taskService.saveTask(task);

    taskService.addCandidateUser(task.getId(), "kermit");
    taskService.addCandidateGroup(task.getId(), "sales");
    assertThat(taskService.getIdentityLinksForTask(task.getId())).hasSize(2);

    taskService.deleteCandidateUser(task.getId(), "kermit");
    taskService.deleteCandidateGroup(task.getId(), "sales");
    taskService.deleteCandidateGroup(task.getId(), "unknown");

    assertThat(taskService.getIdentityLinksForTask(task.getId())).isEmpty();
  }

  @Test
  public void shouldKeepEventNameConstantsStable() {
    // the lifecycle switch relies on these literal event names
    assertThat(TaskListener.EVENTNAME_CREATE).isEqualTo("create");
    assertThat(TaskListener.EVENTNAME_COMPLETE).isEqualTo("complete");
    assertThat(TaskListener.EVENTNAME_DELETE).isEqualTo("delete");
  }
}
