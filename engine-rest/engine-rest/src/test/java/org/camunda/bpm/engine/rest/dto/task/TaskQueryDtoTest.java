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
package org.camunda.bpm.engine.rest.dto.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.ws.rs.core.Response.Status;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.exception.NullValueException;
import org.camunda.bpm.engine.impl.TaskQueryImpl;
import org.camunda.bpm.engine.impl.TaskQueryProperty;
import org.camunda.bpm.engine.impl.VariableInstanceQueryProperty;
import org.camunda.bpm.engine.impl.QueryEntityRelationCondition;
import org.camunda.bpm.engine.impl.QueryOrderingProperty;
import org.camunda.bpm.engine.impl.VariableOrderProperty;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.db.ListQueryParameterObject;
import org.camunda.bpm.engine.impl.variable.ValueTypeResolverImpl;
import org.camunda.bpm.engine.rest.dto.ConditionQueryParameterDto;
import org.camunda.bpm.engine.rest.dto.SortingDto;
import org.camunda.bpm.engine.rest.dto.VariableQueryParameterDto;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.rest.exception.RestException;
import org.camunda.bpm.engine.task.DelegationState;
import org.camunda.bpm.engine.task.TaskQuery;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit coverage for {@link TaskQueryDto}: how the REST query parameters are replayed onto a
 * {@link TaskQuery}, how a {@link TaskQueryImpl} is turned back into a DTO (the filter store), and
 * the sort-by translation tables. A mocked TaskQuery proves exactly which fluent call each parameter
 * triggers; a real TaskQueryImpl proves the round trip.
 */
public class TaskQueryDtoTest {

  private final TaskQuery query = mock(TaskQuery.class);
  private final TaskQueryDto dto = new TaskQueryDto(new ObjectMapper(), new javax.ws.rs.core.MultivaluedHashMap<>());

  // array filters: only a non-empty array is applied -------------------------------------------

  private void assertArrayFilter(BiConsumer<TaskQueryDto, String[]> setter, Consumer<TaskQuery> expectedCall) {
    TaskQuery fresh = mock(TaskQuery.class);
    TaskQueryDto subject = new TaskQueryDto();

    setter.accept(subject, new String[] {"a", "b"});
    subject.applyFilters(fresh);
    expectedCall.accept(verify(fresh));
  }

  private void assertArrayFilterSkipsEmpty(BiConsumer<TaskQueryDto, String[]> setter) {
    TaskQuery fresh = mock(TaskQuery.class);
    TaskQueryDto subject = new TaskQueryDto();

    setter.accept(subject, new String[0]);
    subject.applyFilters(fresh);
    verifyNoInteractions(fresh);

    setter.accept(subject, null);
    subject.applyFilters(fresh);
    verifyNoInteractions(fresh);
  }

  @Test
  public void shouldApplyEveryNonEmptyArrayFilter() {
    String[] ab = {"a", "b"};
    assertArrayFilter(TaskQueryDto::setProcessInstanceBusinessKeyIn, q -> q.processInstanceBusinessKeyIn(ab));
    assertArrayFilter(TaskQueryDto::setProcessDefinitionKeyIn, q -> q.processDefinitionKeyIn(ab));
    assertArrayFilter(TaskQueryDto::setActivityInstanceIdIn, q -> q.activityInstanceIdIn(ab));
    assertArrayFilter(TaskQueryDto::setTenantIdIn, q -> q.tenantIdIn(ab));
    assertArrayFilter(TaskQueryDto::setProcessInstanceIdIn, q -> q.processInstanceIdIn(ab));
    assertArrayFilter(TaskQueryDto::setAssigneeIn, q -> q.taskAssigneeIn(ab));
    assertArrayFilter(TaskQueryDto::setAssigneeNotIn, q -> q.taskAssigneeNotIn(ab));
    assertArrayFilter(TaskQueryDto::setTaskIdIn, q -> q.taskIdIn(ab));
    assertArrayFilter(TaskQueryDto::setTaskDefinitionKeyIn, q -> q.taskDefinitionKeyIn(ab));
    assertArrayFilter(TaskQueryDto::setTaskDefinitionKeyNotIn, q -> q.taskDefinitionKeyNotIn(ab));
  }

  @Test
  public void shouldSkipEmptyAndMissingArrayFilters() {
    assertArrayFilterSkipsEmpty(TaskQueryDto::setProcessInstanceBusinessKeyIn);
    assertArrayFilterSkipsEmpty(TaskQueryDto::setProcessDefinitionKeyIn);
    assertArrayFilterSkipsEmpty(TaskQueryDto::setActivityInstanceIdIn);
    assertArrayFilterSkipsEmpty(TaskQueryDto::setTenantIdIn);
    assertArrayFilterSkipsEmpty(TaskQueryDto::setProcessInstanceIdIn);
    assertArrayFilterSkipsEmpty(TaskQueryDto::setAssigneeIn);
    assertArrayFilterSkipsEmpty(TaskQueryDto::setAssigneeNotIn);
    assertArrayFilterSkipsEmpty(TaskQueryDto::setTaskIdIn);
    assertArrayFilterSkipsEmpty(TaskQueryDto::setTaskDefinitionKeyIn);
    assertArrayFilterSkipsEmpty(TaskQueryDto::setTaskDefinitionKeyNotIn);
  }

  // flags: only TRUE switches the filter on ------------------------------------------------------

  private void assertFlag(BiConsumer<TaskQueryDto, Boolean> setter, Consumer<TaskQuery> expectedCall) {
    TaskQuery on = mock(TaskQuery.class);
    TaskQueryDto subject = new TaskQueryDto();
    setter.accept(subject, true);
    subject.applyFilters(on);
    expectedCall.accept(verify(on));

    TaskQuery off = mock(TaskQuery.class);
    setter.accept(subject, false);
    subject.applyFilters(off);
    verifyNoInteractions(off);
  }

  @Test
  public void shouldSwitchOnEachFlagFilterOnlyWhenTrue() {
    assertFlag(TaskQueryDto::setAssigned, TaskQuery::taskAssigned);
    assertFlag(TaskQueryDto::setUnassigned, TaskQuery::taskUnassigned);
    assertFlag(TaskQueryDto::setActive, TaskQuery::active);
    assertFlag(TaskQueryDto::setSuspended, TaskQuery::suspended);
    assertFlag(TaskQueryDto::setVariableValuesIgnoreCase, TaskQuery::matchVariableValuesIgnoreCase);
    assertFlag(TaskQueryDto::setVariableNamesIgnoreCase, TaskQuery::matchVariableNamesIgnoreCase);
    assertFlag(TaskQueryDto::setWithCommentAttachmentInfo, TaskQuery::withCommentAttachmentInfo);
  }

  @Test
  public void shouldApplyTheExpressionFilters() {
    dto.setFollowUpBeforeOrNotExistentExpression("${now()}");
    dto.setUpdatedAfterExpression("${updated}");

    dto.applyFilters(query);

    verify(query).followUpBeforeOrNotExistentExpression("${now()}");
    verify(query).taskUpdatedAfterExpression("${updated}");
  }

  // variable filters ----------------------------------------------------------------------------

  private static VariableQueryParameterDto variable(String name, String operator, Object value) {
    VariableQueryParameterDto v = new VariableQueryParameterDto();
    v.setName(name);
    v.setOperator(operator);
    v.setValue(value);
    return v;
  }

  @Test
  public void shouldTranslateEveryTaskVariableOperator() {
    dto.setTaskVariables(Arrays.asList(
        variable("v", "eq", "x"), variable("v", "neq", "x"), variable("v", "gt", "x"),
        variable("v", "gteq", "x"), variable("v", "lt", "x"), variable("v", "lteq", "x"),
        variable("v", "like", "x")));

    dto.applyFilters(query);

    verify(query).taskVariableValueEquals("v", "x");
    verify(query).taskVariableValueNotEquals("v", "x");
    verify(query).taskVariableValueGreaterThan("v", "x");
    verify(query).taskVariableValueGreaterThanOrEquals("v", "x");
    verify(query).taskVariableValueLessThan("v", "x");
    verify(query).taskVariableValueLessThanOrEquals("v", "x");
    verify(query).taskVariableValueLike("v", "x");
  }

  @Test
  public void shouldTranslateEveryProcessVariableOperator() {
    dto.setProcessVariables(Arrays.asList(
        variable("v", "eq", "x"), variable("v", "neq", "x"), variable("v", "gt", "x"),
        variable("v", "gteq", "x"), variable("v", "lt", "x"), variable("v", "lteq", "x"),
        variable("v", "like", "x"), variable("v", "notLike", "x")));

    dto.applyFilters(query);

    verify(query).processVariableValueEquals("v", "x");
    verify(query).processVariableValueNotEquals("v", "x");
    verify(query).processVariableValueGreaterThan("v", "x");
    verify(query).processVariableValueGreaterThanOrEquals("v", "x");
    verify(query).processVariableValueLessThan("v", "x");
    verify(query).processVariableValueLessThanOrEquals("v", "x");
    verify(query).processVariableValueLike("v", "x");
    verify(query).processVariableValueNotLike("v", "x");
  }

  @Test
  public void shouldTranslateEveryCaseInstanceVariableOperator() {
    dto.setCaseInstanceVariables(Arrays.asList(
        variable("v", "eq", "x"), variable("v", "neq", "x"), variable("v", "gt", "x"),
        variable("v", "gteq", "x"), variable("v", "lt", "x"), variable("v", "lteq", "x"),
        variable("v", "like", "x")));

    dto.applyFilters(query);

    verify(query).caseInstanceVariableValueEquals("v", "x");
    verify(query).caseInstanceVariableValueNotEquals("v", "x");
    verify(query).caseInstanceVariableValueGreaterThan("v", "x");
    verify(query).caseInstanceVariableValueGreaterThanOrEquals("v", "x");
    verify(query).caseInstanceVariableValueLessThan("v", "x");
    verify(query).caseInstanceVariableValueLessThanOrEquals("v", "x");
    verify(query).caseInstanceVariableValueLike("v", "x");
  }

  @Test
  public void shouldRejectAnUnknownOperatorPerVariableScopeWith400() {
    TaskQueryDto task = new TaskQueryDto();
    task.setTaskVariables(Collections.singletonList(variable("v", "bogus", "x")));
    TaskQueryDto process = new TaskQueryDto();
    process.setProcessVariables(Collections.singletonList(variable("v", "bogus", "x")));
    TaskQueryDto caseInstance = new TaskQueryDto();
    caseInstance.setCaseInstanceVariables(Collections.singletonList(variable("v", "bogus", "x")));

    assertThatThrownBy(() -> task.applyFilters(query)).isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid task variable comparator specified: bogus");
    try {
      task.applyFilters(query);
    } catch (InvalidRequestException e) {
      assertThat(e.getStatus()).isEqualTo(Status.BAD_REQUEST);
    }
    assertThatThrownBy(() -> process.applyFilters(query)).isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid process variable comparator specified: bogus");
    assertThatThrownBy(() -> caseInstance.applyFilters(query)).isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid case variable comparator specified: bogus");
  }

  // sorting -------------------------------------------------------------------------------------

  private static ProcessEngine engineWithTypeResolver() {
    ProcessEngine engine = mock(ProcessEngine.class);
    ProcessEngineConfigurationImpl config = mock(ProcessEngineConfigurationImpl.class);
    when(engine.getProcessEngineConfiguration()).thenReturn(config);
    when(config.getValueTypeResolver()).thenReturn(new ValueTypeResolverImpl());
    return engine;
  }

  private static Map<String, Object> sortParameters() {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put(TaskQueryDto.SORT_PARAMETERS_VARIABLE_NAME, "amount");
    parameters.put(TaskQueryDto.SORT_PARAMETERS_VALUE_TYPE, "Integer");
    return parameters;
  }

  @Test
  public void shouldOrderByEachVariableScopeWithTheResolvedValueType() {
    ProcessEngine engine = engineWithTypeResolver();
    org.camunda.bpm.engine.variable.type.ValueType integer = org.camunda.bpm.engine.variable.type.ValueType.INTEGER;

    dto.applySortBy(query, TaskQueryDto.SORT_BY_PROCESS_VARIABLE, sortParameters(), engine);
    dto.applySortBy(query, TaskQueryDto.SORT_BY_EXECUTION_VARIABLE, sortParameters(), engine);
    dto.applySortBy(query, TaskQueryDto.SORT_BY_TASK_VARIABLE, sortParameters(), engine);
    dto.applySortBy(query, TaskQueryDto.SORT_BY_CASE_INSTANCE_VARIABLE, sortParameters(), engine);
    dto.applySortBy(query, TaskQueryDto.SORT_BY_CASE_EXECUTION_VARIABLE, sortParameters(), engine);

    verify(query).orderByProcessVariable("amount", integer);
    verify(query).orderByExecutionVariable("amount", integer);
    verify(query).orderByTaskVariable("amount", integer);
    verify(query).orderByCaseInstanceVariable("amount", integer);
    verify(query).orderByCaseExecutionVariable("amount", integer);
  }

  @Test
  public void shouldReadSortParametersDefensivelyWhenTheyAreMissing() {
    assertThat(dto.getVariableName(null)).isNull();
    assertThat(dto.getValueTypeName(null)).isNull();
    assertThat(dto.getValue(null, "x")).isNull();
    assertThat(dto.getVariableName(sortParameters())).isEqualTo("amount");
    assertThat(dto.getValueTypeName(sortParameters())).isEqualTo("integer");
  }

  @Test
  public void shouldTranslateEveryTaskQueryPropertyToItsSortByValue() {
    Object[][] expected = {
        {TaskQueryProperty.ASSIGNEE, TaskQueryDto.SORT_BY_ASSIGNEE_VALUE},
        {TaskQueryProperty.CASE_EXECUTION_ID, TaskQueryDto.SORT_BY_CASE_EXECUTION_ID_VALUE},
        {TaskQueryProperty.CASE_INSTANCE_ID, TaskQueryDto.SORT_BY_CASE_INSTANCE_ID_VALUE},
        {TaskQueryProperty.CREATE_TIME, TaskQueryDto.SORT_BY_CREATE_TIME_VALUE},
        {TaskQueryProperty.LAST_UPDATED, TaskQueryDto.SORT_BY_LAST_UPDATED_VALUE},
        {TaskQueryProperty.DESCRIPTION, TaskQueryDto.SORT_BY_DESCRIPTION_VALUE},
        {TaskQueryProperty.DUE_DATE, TaskQueryDto.SORT_BY_DUE_DATE_VALUE},
        {TaskQueryProperty.EXECUTION_ID, TaskQueryDto.SORT_BY_EXECUTION_ID_VALUE},
        {TaskQueryProperty.FOLLOW_UP_DATE, TaskQueryDto.SORT_BY_FOLLOW_UP_VALUE},
        {TaskQueryProperty.NAME, TaskQueryDto.SORT_BY_NAME_VALUE},
        {TaskQueryProperty.NAME_CASE_INSENSITIVE, TaskQueryDto.SORT_BY_NAME_CASE_INSENSITIVE_VALUE},
        {TaskQueryProperty.PRIORITY, TaskQueryDto.SORT_BY_PRIORITY_VALUE},
        {TaskQueryProperty.PROCESS_INSTANCE_ID, TaskQueryDto.SORT_BY_PROCESS_INSTANCE_ID_VALUE},
        {TaskQueryProperty.TASK_ID, TaskQueryDto.SORT_BY_ID_VALUE},
        {TaskQueryProperty.TENANT_ID, TaskQueryDto.SORT_BY_TENANT_ID_VALUE},
    };
    for (Object[] row : expected) {
      assertThat(TaskQueryDto.sortByValueForQueryProperty((org.camunda.bpm.engine.query.QueryProperty) row[0]))
          .as(row[0].toString()).isEqualTo(row[1]);
    }
  }

  @Test
  public void shouldRejectAnUnknownSortProperty() {
    assertThatThrownBy(() -> TaskQueryDto.sortByValueForQueryProperty(
        new org.camunda.bpm.engine.impl.QueryPropertyImpl("bogus")))
        .isInstanceOf(RestException.class)
        .hasMessageContaining("Unknown query property for task query");
  }

  // round trip: TaskQueryImpl -> DTO ------------------------------------------------------------

  @Test
  public void shouldCarryTheSuspensionStateOverIntoTheDto() {
    assertThat(TaskQueryDto.fromQuery(new TaskQueryImpl().active()).getActive()).isTrue();
    assertThat(TaskQueryDto.fromQuery(new TaskQueryImpl().active()).getSuspended()).isNull();
    assertThat(TaskQueryDto.fromQuery(new TaskQueryImpl().suspended()).getSuspended()).isTrue();
    assertThat(TaskQueryDto.fromQuery(new TaskQueryImpl().suspended()).getActive()).isNull();
    assertThat(TaskQueryDto.fromQuery(new TaskQueryImpl()).getActive()).isNull();
  }

  @Test
  public void shouldCarryDelegationStateAndInstanceIdsOver() {
    TaskQueryDto converted = TaskQueryDto.fromQuery(new TaskQueryImpl()
        .taskDelegationState(DelegationState.PENDING).processInstanceIdIn("p1", "p2"));

    assertThat(converted.getDelegationState()).isEqualTo("PENDING");
    assertThat(converted.getProcessInstanceIdIn()).containsExactly("p1", "p2");
    assertThat(TaskQueryDto.fromQuery(new TaskQueryImpl()).getDelegationState()).isNull();
  }
}
