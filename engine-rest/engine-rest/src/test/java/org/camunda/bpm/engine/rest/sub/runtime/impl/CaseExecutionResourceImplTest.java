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
package org.camunda.bpm.engine.rest.sub.runtime.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.ws.rs.core.Response.Status;
import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.exception.NotAllowedException;
import org.camunda.bpm.engine.exception.NotFoundException;
import org.camunda.bpm.engine.exception.NotValidException;
import org.camunda.bpm.engine.rest.dto.runtime.CaseExecutionDto;
import org.camunda.bpm.engine.rest.dto.runtime.CaseExecutionTriggerDto;
import org.camunda.bpm.engine.rest.dto.runtime.TriggerVariableValueDto;
import org.camunda.bpm.engine.rest.dto.runtime.VariableNameDto;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.rest.exception.RestException;
import org.camunda.bpm.engine.runtime.CaseExecution;
import org.camunda.bpm.engine.runtime.CaseExecutionCommandBuilder;
import org.camunda.bpm.engine.runtime.CaseExecutionQuery;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.type.ValueTypeResolver;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link CaseExecutionResourceImpl} with a mocked {@link CaseService}: lookup,
 * the five state transitions and their exception translation, and variable bookkeeping.
 */
public class CaseExecutionResourceImplTest {

  protected static final String ID = "caseExecution1";

  protected CaseService caseService;
  protected CaseExecutionCommandBuilder builder;
  protected CaseExecutionQuery query;
  protected ProcessEngine engine;
  protected CaseExecutionResourceImpl resource;

  @Before
  public void setUp() {
    caseService = mock(CaseService.class);
    builder = mock(CaseExecutionCommandBuilder.class);
    query = mock(CaseExecutionQuery.class);
    engine = mock(ProcessEngine.class);
    when(engine.getCaseService()).thenReturn(caseService);
    ProcessEngineConfiguration configuration = mock(ProcessEngineConfiguration.class);
    when(configuration.getValueTypeResolver()).thenReturn(mock(ValueTypeResolver.class));
    when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
    when(caseService.withCaseExecution(ID)).thenReturn(builder);
    when(caseService.createCaseExecutionQuery()).thenReturn(query);
    when(query.caseExecutionId(ID)).thenReturn(query);

    resource = new CaseExecutionResourceImpl(engine, ID, new ObjectMapper());
  }

  protected Map<String, BiConsumer<CaseExecutionResourceImpl, CaseExecutionTriggerDto>> transitions() {
    Map<String, BiConsumer<CaseExecutionResourceImpl, CaseExecutionTriggerDto>> map = new LinkedHashMap<>();
    map.put("manualStart", CaseExecutionResourceImpl::manualStart);
    map.put("disable", CaseExecutionResourceImpl::disable);
    map.put("reenable", CaseExecutionResourceImpl::reenable);
    map.put("complete", CaseExecutionResourceImpl::complete);
    map.put("terminate", CaseExecutionResourceImpl::terminate);
    return map;
  }

  protected void verifyTransition(String name) {
    switch (name) {
      case "manualStart": verify(builder).manualStart(); break;
      case "disable": verify(builder).disable(); break;
      case "reenable": verify(builder).reenable(); break;
      case "complete": verify(builder).complete(); break;
      default: verify(builder).terminate(); break;
    }
  }

  protected void stubTransitionToThrow(String name, RuntimeException e) {
    switch (name) {
      case "manualStart": doThrow(e).when(builder).manualStart(); break;
      case "disable": doThrow(e).when(builder).disable(); break;
      case "reenable": doThrow(e).when(builder).reenable(); break;
      case "complete": doThrow(e).when(builder).complete(); break;
      default: doThrow(e).when(builder).terminate(); break;
    }
  }

  @Test
  public void shouldReturnTheCaseExecutionAsDto() {
    CaseExecution execution = mock(CaseExecution.class);
    when(execution.getId()).thenReturn(ID);
    when(execution.getActivityId()).thenReturn("PI_HumanTask_1");
    when(execution.isRequired()).thenReturn(true);
    when(query.singleResult()).thenReturn(execution);

    CaseExecutionDto dto = resource.getCaseExecution();

    assertThat(dto.getId()).isEqualTo(ID);
    assertThat(dto.getActivityId()).isEqualTo("PI_HumanTask_1");
    assertThat(dto.isRequired()).isTrue();
  }

  @Test
  public void shouldRespondNotFoundForAnUnknownCaseExecution() {
    when(query.singleResult()).thenReturn(null);

    InvalidRequestException e = (InvalidRequestException) catchThrowable(resource::getCaseExecution);

    assertThat(e.getStatus()).isEqualTo(Status.NOT_FOUND);
    assertThat(e.getMessage()).isEqualTo("Case execution with id " + ID + " does not exist.");
  }

  @Test
  public void shouldInvokeEachTransitionOnTheCommandBuilder() {
    for (Map.Entry<String, BiConsumer<CaseExecutionResourceImpl, CaseExecutionTriggerDto>> t : transitions().entrySet()) {
      builder = mock(CaseExecutionCommandBuilder.class);
      when(caseService.withCaseExecution(ID)).thenReturn(builder);

      t.getValue().accept(resource, new CaseExecutionTriggerDto());

      verifyTransition(t.getKey());
      verify(builder, never()).setVariable(any(), any(org.camunda.bpm.engine.variable.value.TypedValue.class));
    }
  }

  @Test
  public void shouldTranslateEngineExceptionsPerTransition() {
    Object[][] cases = {
        {new NotFoundException("nf"), Status.NOT_FOUND, true},
        {new NotValidException("nv"), Status.BAD_REQUEST, true},
        {new NotAllowedException("na"), Status.FORBIDDEN, true},
        {new ProcessEngineException("boom"), Status.INTERNAL_SERVER_ERROR, false}};

    for (String name : transitions().keySet()) {
      for (Object[] c : cases) {
        builder = mock(CaseExecutionCommandBuilder.class);
        when(caseService.withCaseExecution(ID)).thenReturn(builder);
        ProcessEngineException cause = (ProcessEngineException) c[0];
        stubTransitionToThrow(name, cause);

        RestException e = (RestException) catchThrowable(
            () -> transitions().get(name).accept(resource, new CaseExecutionTriggerDto()));

        assertThat(e.getStatus()).isEqualTo(c[1]);
        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getMessage()).isEqualTo("Cannot " + name + " case execution " + ID + ": " + cause.getMessage());
        assertThat(e instanceof InvalidRequestException).isEqualTo((Boolean) c[2]);
      }
    }
  }

  @Test
  public void shouldSetGlobalAndLocalVariablesAndSkipEmptyCollections() {
    TriggerVariableValueDto global = new TriggerVariableValueDto();
    global.setValue("a");
    TriggerVariableValueDto local = new TriggerVariableValueDto();
    local.setValue(2);
    local.setLocal(true);
    Map<String, TriggerVariableValueDto> variables = new LinkedHashMap<>();
    variables.put("g", global);
    variables.put("l", local);
    CaseExecutionTriggerDto dto = new CaseExecutionTriggerDto();
    dto.setVariables(variables);
    dto.setDeletions(Collections.emptyList());

    resource.complete(dto);

    verify(builder).setVariable("g", Variables.untypedValue("a"));
    verify(builder).setVariableLocal("l", Variables.untypedValue(2));
    verify(builder).complete();
    verify(builder, never()).removeVariable(any());
    verify(builder, never()).removeVariableLocal(any());
  }

  @Test
  public void shouldRemoveGlobalAndLocalVariables() {
    CaseExecutionTriggerDto dto = new CaseExecutionTriggerDto();
    dto.setVariables(Collections.emptyMap());
    dto.setDeletions(Arrays.asList(new VariableNameDto("x", false), new VariableNameDto("y", true)));

    resource.disable(dto);

    verify(builder).removeVariable("x");
    verify(builder).removeVariableLocal("y");
    verify(builder).disable();
    verify(builder, never()).setVariable(any(), any(org.camunda.bpm.engine.variable.value.TypedValue.class));
  }

  @Test
  public void shouldReportWhichVariableIsInvalid() {
    TriggerVariableValueDto bad = new TriggerVariableValueDto();
    bad.setType("NoSuchType");
    bad.setValue("v");
    CaseExecutionTriggerDto dto = new CaseExecutionTriggerDto();
    dto.setVariables(Collections.singletonMap("bad", bad));

    RestException e = (RestException) catchThrowable(() -> resource.manualStart(dto));

    assertThat(e.getStatus()).isEqualTo(Status.BAD_REQUEST);
    assertThat(e.getMessage()).startsWith("Cannot start manually case execution " + ID + " due to invalid variable bad: ");
    verify(builder, never()).manualStart();
  }

  @Test
  public void shouldExposeVariableSubResourcesForTheSameExecution() {
    assertThat(resource.getVariables()).isInstanceOf(CaseExecutionVariablesResource.class);
    assertThat(resource.getVariablesLocal()).isInstanceOf(LocalCaseExecutionVariablesResource.class);
  }
}
