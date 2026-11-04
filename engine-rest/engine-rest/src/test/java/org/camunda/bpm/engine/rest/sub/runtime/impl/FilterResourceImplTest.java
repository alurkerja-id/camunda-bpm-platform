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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Variant;
import org.camunda.bpm.engine.EntityTypes;
import org.camunda.bpm.engine.FilterService;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.exception.NotValidException;
import org.camunda.bpm.engine.exception.NullValueException;
import org.camunda.bpm.engine.filter.Filter;
import org.camunda.bpm.engine.rest.dto.CountResultDto;
import org.camunda.bpm.engine.rest.dto.runtime.FilterDto;
import org.camunda.bpm.engine.rest.dto.task.TaskDto;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.rest.hal.EmptyHalCollection;
import org.camunda.bpm.engine.rest.hal.EmptyHalResource;
import org.camunda.bpm.engine.rest.hal.Hal;
import org.camunda.bpm.engine.rest.hal.task.HalTask;
import org.camunda.bpm.engine.rest.helper.MockProvider;
import org.camunda.bpm.engine.task.Task;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the dispatching, error translation and variable bookkeeping of
 * {@link FilterResourceImpl}, using a mocked {@link FilterService}. The happy paths through a
 * running container are covered by FilterRestServiceInteractionTest.
 */
public class FilterResourceImplTest {

  protected static final String FILTER_ID = MockProvider.EXAMPLE_FILTER_ID;

  protected FilterService filterService;
  protected Filter dbFilter;
  protected FilterResourceImpl resource;

  @Before
  public void setUp() {
    filterService = mock(FilterService.class);
    dbFilter = MockProvider.createMockFilter(FILTER_ID);
    when(dbFilter.getResourceType()).thenReturn(EntityTypes.TASK);
    when(filterService.getFilter(FILTER_ID)).thenReturn(dbFilter);

    resource = new FilterResourceImpl("default", new ObjectMapper(), FILTER_ID, "/");
    resource.filterService = filterService;
  }

  protected Request accepting(MediaType type) {
    Request request = mock(Request.class);
    Variant variant = type == null ? null : Variant.mediaTypes(type).add().build().get(0);
    when(request.selectVariant(any())).thenReturn(variant);
    return request;
  }

  protected void assertRequestError(Runnable call, Status status, String message) {
    try {
      call.run();
    } catch (InvalidRequestException e) {
      assertThat(e.getStatus()).isEqualTo(status);
      assertThat(e.getMessage()).contains(message);
      return;
    }
    throw new AssertionError("expected InvalidRequestException with status " + status);
  }

  // getFilter / lookup -------------------------------------------------------------------------

  @Test
  public void shouldReturnTheFilterWithoutItemCountByDefault() {
    FilterDto dto = resource.getFilter(null);

    assertThat(dto.getId()).isEqualTo(FILTER_ID);
    assertThat(dto.getItemCount()).isNull();
    verify(filterService, never()).count(anyString());
  }

  @Test
  public void shouldNotCountWhenItemCountIsFalse() {
    assertThat(resource.getFilter(false).getItemCount()).isNull();
    verify(filterService, never()).count(anyString());
  }

  @Test
  public void shouldCountWhenItemCountIsRequested() {
    when(filterService.count(FILTER_ID)).thenReturn(13L);

    assertThat(resource.getFilter(true).getItemCount()).isEqualTo(13L);
  }

  @Test
  public void shouldCacheTheDatabaseFilter() {
    resource.getFilter(null);
    resource.getFilter(null);

    verify(filterService, org.mockito.Mockito.times(1)).getFilter(FILTER_ID);
  }

  @Test
  public void shouldAnswerNotFoundForAMissingFilter() {
    when(filterService.getFilter(FILTER_ID)).thenReturn(null);

    assertRequestError(() -> resource.getFilter(null), Status.NOT_FOUND,
        "Filter with id '" + FILTER_ID + "' does not exist.");
  }

  // delete / update ----------------------------------------------------------------------------

  @Test
  public void shouldDeleteTheFilter() {
    resource.deleteFilter();

    verify(filterService).deleteFilter(FILTER_ID);
  }

  @Test
  public void shouldAnswerNotFoundWhenDeletingAMissingFilter() {
    doThrow(new NullValueException("gone")).when(filterService).deleteFilter(FILTER_ID);

    assertRequestError(resource::deleteFilter, Status.NOT_FOUND, "does not exist");
  }

  @Test
  public void shouldSaveTheUpdatedFilter() {
    FilterDto dto = mock(FilterDto.class);

    resource.updateFilter(dto);

    verify(dto).updateFilter(eq(dbFilter), any());
    verify(filterService).saveFilter(dbFilter);
  }

  @Test
  public void shouldAnswerBadRequestForAnInvalidUpdate() {
    FilterDto dto = mock(FilterDto.class);
    doThrow(new NotValidException("bad")).when(dto).updateFilter(eq(dbFilter), any());

    assertRequestError(() -> resource.updateFilter(dto), Status.BAD_REQUEST,
        "Unable to update filter with invalid content");
    verify(filterService, never()).saveFilter(any());
  }

  // single result ------------------------------------------------------------------------------

  @Test
  public void shouldRefuseSingleResultWithoutAcceptableMediaType() {
    assertRequestError(() -> resource.executeSingleResult(accepting(null)),
        Status.NOT_ACCEPTABLE, "No acceptable content-type found");
    assertRequestError(() -> resource.querySingleResult(accepting(null), null),
        Status.NOT_ACCEPTABLE, "No acceptable content-type found");
  }

  @Test
  public void shouldRefuseSingleResultForAnUnsupportedMediaType() {
    assertRequestError(() -> resource.executeSingleResult(accepting(MediaType.TEXT_PLAIN_TYPE)),
        Status.NOT_ACCEPTABLE, "No acceptable content-type found");
    assertRequestError(() -> resource.querySingleResult(accepting(MediaType.TEXT_PLAIN_TYPE), null),
        Status.NOT_ACCEPTABLE, "No acceptable content-type found");
  }

  @Test
  public void shouldReturnNullJsonSingleResultWhenNothingMatches() {
    when(filterService.singleResult(FILTER_ID, null)).thenReturn(null);

    assertThat(resource.executeSingleResult(accepting(MediaType.APPLICATION_JSON_TYPE))).isNull();
    assertThat(resource.querySingleResult(accepting(MediaType.APPLICATION_JSON_TYPE), "{}")).isNull();
  }

  @Test
  public void shouldConvertAJsonSingleResultToATaskDto() {
    Task task = MockProvider.createMockTask();
    when(filterService.singleResult(FILTER_ID, null)).thenReturn(task);

    Object result = resource.executeSingleResult(accepting(MediaType.APPLICATION_JSON_TYPE));

    assertThat(result).isInstanceOf(TaskDto.class);
    assertThat(((TaskDto) result).getId()).isEqualTo(task.getId());
  }

  @Test
  public void shouldReturnEmptyHalResourceWhenNothingMatches() {
    when(filterService.singleResult(FILTER_ID, null)).thenReturn(null);

    assertThat(resource.executeSingleResult(accepting(Hal.APPLICATION_HAL_JSON_TYPE)))
        .isSameAs(EmptyHalResource.INSTANCE);
    assertThat(resource.querySingleResult(accepting(Hal.APPLICATION_HAL_JSON_TYPE), null))
        .isSameAs(EmptyHalResource.INSTANCE);
  }

  @Test
  public void shouldRefuseAnEntityTypeFiltersCannotConvert() {
    when(filterService.singleResult(FILTER_ID, null)).thenReturn("not a task");

    assertRequestError(() -> resource.executeSingleResult(accepting(MediaType.APPLICATION_JSON_TYPE)),
        Status.BAD_REQUEST, "Entities of class 'java.lang.String' are currently not supported by filters.");
    assertRequestError(() -> resource.executeSingleResult(accepting(Hal.APPLICATION_HAL_JSON_TYPE)),
        Status.BAD_REQUEST, "Entities of class 'java.lang.String' are currently not supported by filters.");
  }

  @Test
  public void shouldTranslateSingleResultFailures() {
    doThrow(new NullValueException("gone")).when(filterService).singleResult(FILTER_ID, null);
    assertRequestError(() -> resource.queryJsonSingleResult(null), Status.NOT_FOUND, "does not exist");

    doThrow(new NotValidException("bad")).when(filterService).singleResult(FILTER_ID, null);
    assertRequestError(() -> resource.queryJsonSingleResult(null), Status.BAD_REQUEST,
        "Filter cannot be extended by an invalid query");

    doThrow(new ProcessEngineException("many")).when(filterService).singleResult(FILTER_ID, null);
    assertRequestError(() -> resource.queryJsonSingleResult(null), Status.BAD_REQUEST,
        "Filter does not returns a valid single result");
  }

  // list ---------------------------------------------------------------------------------------

  @Test
  public void shouldRefuseListWithoutAcceptableMediaType() {
    assertRequestError(() -> resource.executeList(accepting(null), null, null),
        Status.NOT_ACCEPTABLE, "No acceptable content-type found");
    assertRequestError(() -> resource.queryList(accepting(null), null, null, null),
        Status.NOT_ACCEPTABLE, "No acceptable content-type found");
    assertRequestError(() -> resource.executeList(accepting(MediaType.TEXT_PLAIN_TYPE), null, null),
        Status.NOT_ACCEPTABLE, "No acceptable content-type found");
    assertRequestError(() -> resource.queryList(accepting(MediaType.TEXT_PLAIN_TYPE), null, null, null),
        Status.NOT_ACCEPTABLE, "No acceptable content-type found");
  }

  @Test
  public void shouldListWithoutPagingWhenNeitherBoundIsGiven() {
    Task task = MockProvider.createMockTask();
    doReturnList(filterService, Collections.singletonList(task));

    Object result = resource.executeList(accepting(MediaType.APPLICATION_JSON_TYPE), null, null);

    assertThat((List<?>) result).hasSize(1);
    verify(filterService).list(FILTER_ID, null);
    verify(filterService, never()).listPage(anyString(), any(), anyInt(), anyInt());
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected void doReturnList(FilterService service, List<?> entities) {
    when(service.list(eq(FILTER_ID), any())).thenReturn((List) entities);
  }

  @Test
  public void shouldDefaultTheFirstResultToZeroWhenOnlyMaxResultsIsGiven() {
    resource.queryJsonList(null, null, 5);

    verify(filterService).listPage(FILTER_ID, null, 0, 5);
  }

  @Test
  public void shouldDefaultMaxResultsToUnboundedWhenOnlyFirstResultIsGiven() {
    resource.queryJsonList(null, 3, null);

    verify(filterService).listPage(FILTER_ID, null, 3, Integer.MAX_VALUE);
  }

  @Test
  public void shouldReturnAnEmptyJsonListForNoEntities() {
    assertThat(resource.queryJsonList(null, 1, 1)).isEmpty();
  }

  @Test
  public void shouldTranslateListFailures() {
    doThrow(new NullValueException("gone")).when(filterService).list(FILTER_ID, null);
    assertRequestError(() -> resource.queryJsonList(null, null, null), Status.NOT_FOUND, "does not exist");

    doThrow(new NotValidException("bad")).when(filterService).list(FILTER_ID, null);
    assertRequestError(() -> resource.queryJsonList(null, null, null), Status.BAD_REQUEST,
        "Filter cannot be extended by an invalid query");
  }

  @Test
  public void shouldReturnAnEmptyHalCollectionCarryingTheCount() {
    when(filterService.count(FILTER_ID, null)).thenReturn(7L);

    Object result = resource.executeList(accepting(Hal.APPLICATION_HAL_JSON_TYPE), null, null);

    assertThat(result).isInstanceOf(EmptyHalCollection.class);
    assertThat(((EmptyHalCollection) result).getCount()).isEqualTo(7L);
    Object queried = resource.queryList(accepting(Hal.APPLICATION_HAL_JSON_TYPE), null, null, null);
    assertThat(((EmptyHalCollection) queried).getCount()).isEqualTo(7L);
  }

  @Test
  public void shouldRefuseAHalCollectionOfUnsupportedEntities() {
    doReturnList(filterService, Collections.singletonList("not a task"));

    assertRequestError(() -> resource.queryHalList(null, null, null), Status.BAD_REQUEST,
        "Entities of class 'java.lang.String' are currently not supported by filters.");
  }

  // count --------------------------------------------------------------------------------------

  @Test
  public void shouldCountThroughTheFilter() {
    when(filterService.count(FILTER_ID, null)).thenReturn(4L);

    CountResultDto dto = resource.executeCount();

    assertThat(dto.getCount()).isEqualTo(4L);
    assertThat(resource.queryCount("  ").getCount()).isEqualTo(4L);
  }

  @Test
  public void shouldTranslateCountFailures() {
    doThrow(new NullValueException("gone")).when(filterService).count(FILTER_ID, null);
    assertRequestError(resource::executeCount, Status.NOT_FOUND, "does not exist");

    doThrow(new NotValidException("bad")).when(filterService).count(FILTER_ID, null);
    assertRequestError(resource::executeCount, Status.BAD_REQUEST,
        "Filter cannot be extended by an invalid query");
  }

  // extending query ----------------------------------------------------------------------------

  @Test
  public void shouldTreatBlankAndEmptyObjectBodiesAsNoExtendingQuery() {
    assertThat(resource.isEmptyJson(null)).isTrue();
    assertThat(resource.isEmptyJson("   ")).isTrue();
    assertThat(resource.isEmptyJson(" { } ")).isTrue();
    assertThat(resource.isEmptyJson("{\"name\":\"x\"}")).isFalse();
  }

  @Test
  public void shouldRefuseExtendingQueriesForOtherResourceTypes() {
    when(dbFilter.getResourceType()).thenReturn(EntityTypes.PROCESS_INSTANCE);

    assertRequestError(() -> resource.convertQuery("{\"name\":\"x\"}"), Status.BAD_REQUEST,
        "Queries for resource type 'ProcessInstance' are currently not supported by filters.");
  }

  @Test
  public void shouldRefuseAnUnparsableExtendingQuery() {
    assertRequestError(() -> resource.convertQuery("{not json"), Status.BAD_REQUEST,
        "Invalid query for resource type 'Task'");
  }

  // filter variables ---------------------------------------------------------------------------

  protected Map<String, Object> variableDefinition(String name) {
    Map<String, Object> definition = new HashMap<>();
    definition.put("name", name);
    return definition;
  }

  @Test
  public void shouldHaveNoVariableNamesWithoutProperties() {
    when(dbFilter.getProperties()).thenReturn(null);

    assertThat(resource.getFilterVariableNames()).isNull();
  }

  @Test
  public void shouldHaveNoVariableNamesWhenTheVariablesPropertyIsMissingOrEmpty() {
    when(dbFilter.getProperties()).thenReturn(new HashMap<>());
    assertThat(resource.getFilterVariableNames()).isNull();

    Map<String, Object> properties = new HashMap<>();
    properties.put("variables", Collections.emptyList());
    when(dbFilter.getProperties()).thenReturn(properties);
    assertThat(resource.getFilterVariableNames()).isNull();
  }

  @Test
  public void shouldCollectTheDeclaredVariableNames() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("variables", Arrays.asList(variableDefinition("a"), variableDefinition("b")));
    when(dbFilter.getProperties()).thenReturn(properties);

    assertThat(resource.getFilterVariableNames()).containsExactly("a", "b");
  }

  @Test
  public void shouldAnswerServerErrorForMalformedVariableDefinitions() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("variables", "not a list");
    when(dbFilter.getProperties()).thenReturn(properties);

    assertRequestError(resource::getFilterVariableNames, Status.INTERNAL_SERVER_ERROR,
        "Filter property 'variables' has to be a list of variable definitions");
  }

  @Test
  public void shouldCollectVariableScopeIdsInVisibilityOrderWithoutNulls() {
    HalTask first = mock(HalTask.class);
    when(first.getId()).thenReturn("task1");
    when(first.getExecutionId()).thenReturn("exec1");
    when(first.getProcessInstanceId()).thenReturn("proc1");
    HalTask second = mock(HalTask.class);
    when(second.getId()).thenReturn("task2");
    when(second.getProcessInstanceId()).thenReturn("proc1");
    when(second.getCaseInstanceId()).thenReturn("case2");

    assertThat(resource.getVariableScopeIds(first, second))
        .containsExactly("task1", "exec1", "proc1", "task2", "case2");
    assertThat(resource.getVariableScopeIds()).isEmpty();
    assertThat(resource.getVariableScopeIds((HalTask[]) null)).isEmpty();
  }

  @Test
  public void shouldSkipVariableLookupWithoutTasksOrDeclaredVariables() {
    assertThat(resource.getVariableInstancesForTasks()).isNull();
    assertThat(resource.getVariableInstancesForTasks((HalTask[]) null)).isNull();

    HalTask task = mock(HalTask.class);
    when(dbFilter.getProperties()).thenReturn(null);
    assertThat(resource.getVariableInstancesForTasks(task)).isNull();
  }

  @Test
  public void shouldKnowWhichEntitiesAreOfAClass() {
    assertThat(resource.isEntityOfClass(MockProvider.createMockTask(), Task.class)).isTrue();
    assertThat(resource.isEntityOfClass("x", Task.class)).isFalse();
  }
}
