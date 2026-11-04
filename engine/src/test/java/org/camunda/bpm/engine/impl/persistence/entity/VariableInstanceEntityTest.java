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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.camunda.bpm.engine.impl.cmmn.entity.runtime.CaseExecutionEntity;
import org.junit.Test;

public class VariableInstanceEntityTest {

  @Test
  public void shouldBuildPersistentStateFromSetFields() {
    VariableInstanceEntity entity = new VariableInstanceEntity();
    entity.setLongValue(1L);
    entity.setDoubleValue(2.5d);
    entity.setTextValue("a");
    entity.setTextValue2("b");
    entity.setSerializerName("ser");
    entity.setByteArrayValueId("bytes");
    entity.setExecutionId("exec");
    entity.setTaskId("task");
    entity.setCaseExecutionId("ce");
    entity.setCaseInstanceId("ci");
    entity.setTenantId("t");
    entity.setProcessInstanceId("pi");
    entity.setProcessDefinitionId("pd");
    entity.setConcurrentLocal(true);
    entity.setSequenceCounter(5);

    @SuppressWarnings("unchecked")
    Map<String, Object> state = (Map<String, Object>) entity.getPersistentState();

    assertThat(state).containsEntry("serializerName", "ser").containsEntry("longValue", 1L)
        .containsEntry("doubleValue", 2.5d).containsEntry("textValue", "a").containsEntry("textValue2", "b")
        .containsEntry("byteArrayValueId", "bytes").containsEntry("sequenceCounter", 5L)
        .containsEntry("concurrentLocal", true).containsEntry("executionId", "exec")
        .containsEntry("taskId", "task").containsEntry("caseExecutionId", "ce")
        .containsEntry("caseInstanceId", "ci").containsEntry("tenantId", "t")
        .containsEntry("processInstanceId", "pi").containsEntry("processDefinitionId", "pd");
  }

  @Test
  public void shouldOmitUnsetValuesFromPersistentState() {
    @SuppressWarnings("unchecked")
    Map<String, Object> state = (Map<String, Object>) new VariableInstanceEntity().getPersistentState();

    assertThat(state).doesNotContainKeys("serializerName", "longValue", "doubleValue", "textValue", "textValue2",
        "byteArrayValueId").containsEntry("sequenceCounter", 1L).containsEntry("concurrentLocal", false);
  }

  @Test
  public void shouldResolveVariableScopeIdByPrecedence() {
    VariableInstanceEntity entity = new VariableInstanceEntity();
    assertThat(entity.getVariableScopeId()).isNull();
    entity.setCaseExecutionId("ce");
    assertThat(entity.getVariableScopeId()).isEqualTo("ce");
    entity.setExecutionId("exec");
    assertThat(entity.getVariableScopeId()).isEqualTo("exec");
    entity.setTaskId("task");
    assertThat(entity.getVariableScopeId()).isEqualTo("task");
    entity.setVariableScopeId("explicit");
    assertThat(entity.getVariableScopeId()).isEqualTo("explicit");
  }

  @Test
  public void shouldCopyIdsFromExecution() {
    ExecutionEntity execution = mock(ExecutionEntity.class);
    when(execution.getId()).thenReturn("exec");
    when(execution.getProcessDefinitionId()).thenReturn("pd");
    when(execution.getProcessInstanceId()).thenReturn("pi");
    when(execution.getTenantId()).thenReturn("t");
    VariableInstanceEntity entity = new VariableInstanceEntity();

    entity.setExecution(execution);

    assertThat(entity.getExecution()).isSameAs(execution);
    assertThat(entity.getExecutionId()).isEqualTo("exec");
    assertThat(entity.getProcessDefinitionId()).isEqualTo("pd");
    assertThat(entity.getProcessInstanceId()).isEqualTo("pi");
    assertThat(entity.getTenantId()).isEqualTo("t");
    assertThat(entity.wasCreatedBefore713()).isFalse();

    entity.setExecution(null);

    assertThat(entity.getExecutionId()).isNull();
    assertThat(entity.getProcessDefinitionId()).isNull();
    assertThat(entity.getProcessInstanceId()).isNull();
    assertThat(entity.getTenantId()).isNull();
    assertThat(entity.wasCreatedBefore713()).isTrue();
  }

  @Test
  public void shouldCopyIdsFromCaseExecution() {
    CaseExecutionEntity caseExecution = mock(CaseExecutionEntity.class);
    when(caseExecution.getId()).thenReturn("ce");
    when(caseExecution.getCaseInstanceId()).thenReturn("ci");
    when(caseExecution.getTenantId()).thenReturn("t");
    VariableInstanceEntity entity = new VariableInstanceEntity();

    entity.setCaseExecution(caseExecution);

    assertThat(entity.getCaseExecutionId()).isEqualTo("ce");
    assertThat(entity.getCaseInstanceId()).isEqualTo("ci");
    assertThat(entity.getTenantId()).isEqualTo("t");

    entity.setCaseExecution(null);

    assertThat(entity.getCaseExecutionId()).isNull();
    assertThat(entity.getCaseInstanceId()).isNull();
    assertThat(entity.getTenantId()).isNull();
  }

  @Test
  public void shouldCopyIdsFromTaskAndResetOnNull() {
    TaskEntity task = mock(TaskEntity.class);
    ExecutionEntity execution = mock(ExecutionEntity.class);
    CaseExecutionEntity caseExecution = mock(CaseExecutionEntity.class);
    when(task.getId()).thenReturn("task");
    when(task.getTenantId()).thenReturn("t");
    when(task.getExecution()).thenReturn(execution);
    when(task.getCaseExecution()).thenReturn(caseExecution);
    when(execution.getId()).thenReturn("exec");
    when(caseExecution.getId()).thenReturn("ce");
    VariableInstanceEntity entity = new VariableInstanceEntity();

    entity.setTask(task);

    assertThat(entity.getTaskId()).isEqualTo("task");
    assertThat(entity.getExecutionId()).isEqualTo("exec");
    assertThat(entity.getCaseExecutionId()).isEqualTo("ce");

    entity.setTask(null);

    assertThat(entity.getTaskId()).isNull();
    assertThat(entity.getExecutionId()).isNull();
    assertThat(entity.getCaseExecutionId()).isNull();
    assertThat(entity.getTenantId()).isNull();
  }

  @Test
  public void shouldSkipExecutionAndCaseExecutionWhenTaskHasNone() {
    TaskEntity task = mock(TaskEntity.class);
    when(task.getId()).thenReturn("task");
    VariableInstanceEntity entity = new VariableInstanceEntity();

    entity.setTask(task);

    assertThat(entity.getTaskId()).isEqualTo("task");
    assertThat(entity.getExecutionId()).isNull();
    assertThat(entity.getCaseExecutionId()).isNull();
  }

  @Test
  public void shouldClearValueFields() {
    VariableInstanceEntity entity = new VariableInstanceEntity();
    entity.setLongValue(1L);
    entity.setDoubleValue(1d);
    entity.setTextValue("a");
    entity.setTextValue2("b");

    entity.clearValueFields(false);

    assertThat(entity.getLongValue()).isNull();
    assertThat(entity.getDoubleValue()).isNull();
    assertThat(entity.getTextValue()).isNull();
    assertThat(entity.getTextValue2()).isNull();
  }

  @Test
  public void shouldDetectObjectTypeOnlyWhenOldAndNewAreObject() {
    VariableInstanceEntity entity = new VariableInstanceEntity();
    assertThat(entity.isObjectTyped(null, "object")).isFalse();
    assertThat(entity.isObjectTyped(org.camunda.bpm.engine.variable.type.ValueType.OBJECT, "object")).isTrue();
    assertThat(entity.isObjectTyped(org.camunda.bpm.engine.variable.type.ValueType.OBJECT, "string")).isFalse();
    assertThat(entity.isObjectTyped(org.camunda.bpm.engine.variable.type.ValueType.STRING, "string")).isFalse();
  }

  @Test
  public void shouldNotInsertTransientVariable() {
    VariableInstanceEntity entity = new VariableInstanceEntity();
    entity.setTransient(true);

    // transient variables must not touch the command context
    VariableInstanceEntity.insert(entity);

    assertThat(entity.isTransient()).isTrue();
    entity.setTransient(false);
    assertThat(entity.isTransient()).isFalse();
  }

  @Test
  public void shouldIncrementSequenceCounterAndRevision() {
    VariableInstanceEntity entity = new VariableInstanceEntity();
    entity.setRevision(3);
    entity.incrementSequenceCounter();

    assertThat(entity.getRevision()).isEqualTo(3);
    assertThat(entity.getRevisionNext()).isEqualTo(4);
    assertThat(entity.getSequenceCounter()).isEqualTo(2L);
  }

  @Test
  public void shouldCollectReferencedEntities() {
    VariableInstanceEntity entity = new VariableInstanceEntity();
    assertThat(entity.getReferencedEntityIds()).isEmpty();
    assertThat(entity.getReferencedEntitiesIdAndClass()).isEmpty();

    entity.setProcessInstanceId("pi");
    entity.setExecutionId("exec");
    entity.setCaseInstanceId("ci");
    entity.setCaseExecutionId("ce");
    entity.setByteArrayValueId("bytes");

    assertThat(entity.getReferencedEntitiesIdAndClass())
        .containsEntry("pi", ExecutionEntity.class)
        .containsEntry("exec", ExecutionEntity.class)
        .containsEntry("ci", CaseExecutionEntity.class)
        .containsEntry("ce", CaseExecutionEntity.class)
        .containsEntry("bytes", ByteArrayEntity.class);
  }

  @Test
  public void shouldCompareEqualityAndHashCodeById() {
    VariableInstanceEntity a = new VariableInstanceEntity();
    VariableInstanceEntity b = new VariableInstanceEntity();

    assertThat(a).isEqualTo(a).isEqualTo(b).isNotEqualTo(null).isNotEqualTo("x");
    a.setId("1");
    assertThat(a).isNotEqualTo(b);
    assertThat(b).isNotEqualTo(a);
    b.setId("1");
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    b.setId("2");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  public void shouldRenderToString() {
    VariableInstanceEntity entity = new VariableInstanceEntity();
    entity.setId("id");
    entity.setName("name");

    assertThat(entity.toString()).startsWith("VariableInstanceEntity[id=id").contains("name=name");
  }

  @Test
  public void shouldHaveNoVariableScopeWithoutIds() {
    assertThat(new VariableInstanceEntity().getVariableScope()).isNull();
    assertThat(new VariableInstanceEntity().getTask()).isNull();
    assertThat(new VariableInstanceEntity().getCaseExecution()).isNull();
    assertThat(new VariableInstanceEntity().getContextProcessApplication()).isNull();
  }
}
