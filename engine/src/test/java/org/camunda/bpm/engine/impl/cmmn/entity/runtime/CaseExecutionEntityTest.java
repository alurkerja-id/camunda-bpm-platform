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
package org.camunda.bpm.engine.impl.cmmn.entity.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.multitenancy.TenantIdProvider;
import org.camunda.bpm.engine.impl.cfg.multitenancy.TenantIdProviderCaseInstanceContext;
import org.camunda.bpm.engine.impl.cmmn.execution.CmmnSentryPart;
import org.camunda.bpm.engine.impl.cmmn.entity.repository.CaseDefinitionEntity;
import org.camunda.bpm.engine.impl.cmmn.handler.ItemHandler;
import org.camunda.bpm.engine.impl.cmmn.model.CmmnActivity;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.entitymanager.DbEntityManager;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.history.event.HistoryEvent;
import org.camunda.bpm.engine.impl.history.event.HistoryEventTypes;
import org.camunda.bpm.engine.impl.history.handler.HistoryEventHandler;
import org.camunda.bpm.engine.impl.history.producer.CmmnHistoryEventProducer;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.deploy.cache.DeploymentCache;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionManager;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskManager;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceManager;
import org.camunda.bpm.model.cmmn.CmmnModelInstance;
import org.camunda.bpm.model.cmmn.instance.CmmnElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for the lazy loading, relation handling and persistence contract of
 * {@link CaseExecutionEntity}, with the command context and its managers mocked out.
 */
public class CaseExecutionEntityTest {

  protected ProcessEngineConfigurationImpl config;
  protected CommandContext commandContext;
  protected CaseExecutionManager caseExecutionManager;
  protected CaseSentryPartManager sentryPartManager;
  protected TaskManager taskManager;
  protected ExecutionManager executionManager;
  protected VariableInstanceManager variableInstanceManager;
  protected DbEntityManager dbEntityManager;
  protected DeploymentCache deploymentCache;

  @Before
  public void setUp() {
    config = mock(ProcessEngineConfigurationImpl.class);
    commandContext = mock(CommandContext.class);
    caseExecutionManager = mock(CaseExecutionManager.class);
    sentryPartManager = mock(CaseSentryPartManager.class);
    taskManager = mock(TaskManager.class);
    executionManager = mock(ExecutionManager.class);
    variableInstanceManager = mock(VariableInstanceManager.class);
    dbEntityManager = mock(DbEntityManager.class);
    deploymentCache = mock(DeploymentCache.class);

    when(commandContext.getCaseExecutionManager()).thenReturn(caseExecutionManager);
    when(commandContext.getCaseSentryPartManager()).thenReturn(sentryPartManager);
    when(commandContext.getTaskManager()).thenReturn(taskManager);
    when(commandContext.getExecutionManager()).thenReturn(executionManager);
    when(commandContext.getVariableInstanceManager()).thenReturn(variableInstanceManager);
    when(commandContext.getDbEntityManager()).thenReturn(dbEntityManager);
    when(config.getDeploymentCache()).thenReturn(deploymentCache);

    Context.setCommandContext(commandContext);
    Context.setProcessEngineConfiguration(config);
  }

  @After
  public void tearDown() {
    Context.removeCommandContext();
    Context.removeProcessEngineConfiguration();
  }

  protected CaseExecutionEntity entity(String id, String parentId) {
    CaseExecutionEntity entity = new CaseExecutionEntity();
    entity.setId(id);
    entity.parentId = parentId;
    return entity;
  }

  // case definition -------------------------------------------------------------------------

  @Test
  public void shouldLoadCaseDefinitionFromDeploymentCacheOnce() {
    CaseDefinitionEntity definition = mock(CaseDefinitionEntity.class);
    when(definition.getId()).thenReturn("def:1");
    when(deploymentCache.getCaseDefinitionById("def:1")).thenReturn(definition);
    CaseExecutionEntity entity = entity("e1", null);
    entity.caseDefinitionId = "def:1";

    assertThat(entity.getCaseDefinition()).isSameAs(definition);
    assertThat(entity.getCaseDefinition()).isSameAs(definition);

    verify(deploymentCache).getCaseDefinitionById("def:1");
    assertThat(entity.getCaseDefinitionId()).isEqualTo("def:1");
  }

  @Test
  public void shouldClearCaseDefinitionIdWhenDefinitionIsRemoved() {
    CaseDefinitionEntity definition = mock(CaseDefinitionEntity.class);
    when(definition.getId()).thenReturn("def:1");
    CaseExecutionEntity entity = entity("e1", null);
    entity.setCaseDefinition(definition);
    assertThat(entity.getCaseDefinitionId()).isEqualTo("def:1");

    entity.setCaseDefinition(null);

    assertThat(entity.getCaseDefinitionId()).isNull();
  }

  @Test
  public void shouldExposeTenantIdOfCaseDefinition() {
    CaseDefinitionEntity definition = mock(CaseDefinitionEntity.class);
    when(definition.getTenantId()).thenReturn("tenant");
    CaseExecutionEntity entity = entity("e1", null);
    entity.setCaseDefinition(definition);

    assertThat(entity.getCaseDefinitionTenantId()).isEqualTo("tenant");
  }

  // parent and execution tree ---------------------------------------------------------------

  @Test
  public void shouldTrackParentIdWhenParentIsSetAndCleared() {
    CaseExecutionEntity parent = entity("p", null);
    CaseExecutionEntity child = entity("c", null);

    child.setParent(parent);
    assertThat(child.getParentId()).isEqualTo("p");
    assertThat(child.getParent()).isSameAs(parent);

    child.setParent(null);
    assertThat(child.getParentId()).isNull();
    assertThat(child.isCaseInstanceExecution()).isTrue();
  }

  @Test
  public void shouldLoadParentByIdWhenPrefetchIsDisabled() {
    CaseExecutionEntity parent = entity("p", null);
    when(config.isExecutionTreePrefetchEnabled()).thenReturn(false);
    when(caseExecutionManager.findCaseExecutionById("p")).thenReturn(parent);
    CaseExecutionEntity child = entity("c", "p");

    assertThat(child.getParent()).isSameAs(parent);
    assertThat(child.isCaseInstanceExecution()).isFalse();
  }

  @Test
  public void shouldWireWholeTreeWhenPrefetchIsEnabled() {
    CaseExecutionEntity root = entity("root", null);
    CaseExecutionEntity middle = entity("middle", "root");
    CaseExecutionEntity leaf = entity("leaf", "middle");
    root.caseInstanceId = "root";
    middle.caseInstanceId = "root";
    leaf.caseInstanceId = "root";
    when(config.isExecutionTreePrefetchEnabled()).thenReturn(true);
    when(caseExecutionManager.findChildCaseExecutionsByCaseInstanceId("root"))
        .thenReturn(Arrays.asList(root, middle, leaf));

    assertThat(leaf.getParent()).isSameAs(middle);

    assertThat(middle.parent).isSameAs(root);
    assertThat(leaf.caseInstance).isSameAs(root);
    assertThat(root.caseInstance).isSameAs(root);
    assertThat(root.caseExecutions).containsExactly(middle);
    assertThat(middle.caseExecutions).containsExactly(leaf);
    assertThat(leaf.caseExecutions).isEmpty();
  }

  // activity --------------------------------------------------------------------------------

  protected CmmnActivity activity(String id, String name, Object type, Object description) {
    CmmnActivity activity = mock(CmmnActivity.class);
    when(activity.getId()).thenReturn(id);
    when(activity.getName()).thenReturn(name);
    when(activity.getProperty(ItemHandler.PROPERTY_ACTIVITY_TYPE)).thenReturn(type);
    when(activity.getProperty(ItemHandler.PROPERTY_ACTIVITY_DESCRIPTION)).thenReturn(description);
    return activity;
  }

  @Test
  public void shouldCopyActivityAttributesWhenActivityIsSet() {
    CaseExecutionEntity entity = entity("e1", null);

    entity.setActivity(activity("act", "Name", "humanTask", "Description"));

    assertThat(entity.getActivityId()).isEqualTo("act");
    assertThat(entity.getActivityName()).isEqualTo("Name");
    assertThat(entity.getActivityType()).isEqualTo("humanTask");
    assertThat(entity.getActivityDescription()).isEqualTo("Description");
  }

  @Test
  public void shouldIgnoreActivityPropertiesThatAreNotStrings() {
    CaseExecutionEntity entity = entity("e1", null);

    entity.setActivity(activity("act", "Name", 42, null));

    assertThat(entity.getActivityType()).isNull();
    assertThat(entity.getActivityDescription()).isNull();
    assertThat(entity.getActivityProperty(null, "anything")).isNull();
  }

  @Test
  public void shouldClearActivityAttributesWhenActivityIsRemoved() {
    CaseExecutionEntity entity = entity("e1", null);
    entity.setActivity(activity("act", "Name", "humanTask", "Description"));

    entity.setActivity(null);

    assertThat(entity.getActivityId()).isNull();
    assertThat(entity.getActivityName()).isNull();
    assertThat(entity.getActivityType()).isNull();
    assertThat(entity.getActivityDescription()).isNull();
  }

  @Test
  public void shouldLookUpActivityInCaseDefinitionWhenOnlyIdIsKnown() {
    CmmnActivity activity = activity("act", "Name", "humanTask", null);
    CaseDefinitionEntity definition = mock(CaseDefinitionEntity.class);
    when(definition.findActivity("act")).thenReturn(activity);
    CaseExecutionEntity entity = entity("e1", null);
    entity.setCaseDefinition(definition);
    entity.activityId = "act";

    assertThat(entity.getActivity()).isSameAs(activity);
    assertThat(entity.getActivityName()).isEqualTo("Name");
  }

  // children, task, case instance -----------------------------------------------------------

  @Test
  public void shouldLoadChildExecutionsOnceAndReturnACopy() {
    CaseExecutionEntity child = entity("c", "e1");
    List<CaseExecutionEntity> loaded = new ArrayList<>(Collections.singletonList(child));
    when(caseExecutionManager.findChildCaseExecutionsByParentCaseExecutionId("e1")).thenReturn(loaded);
    CaseExecutionEntity entity = entity("e1", null);

    List<CaseExecutionEntity> first = entity.getCaseExecutions();
    first.clear();

    assertThat(entity.getCaseExecutions()).containsExactly(child);
    verify(caseExecutionManager).findChildCaseExecutionsByParentCaseExecutionId("e1");
  }

  @Test
  public void shouldLoadTaskByCaseExecutionIdOnce() {
    TaskEntity task = new TaskEntity();
    when(taskManager.findTaskByCaseExecutionId("e1")).thenReturn(task);
    CaseExecutionEntity entity = entity("e1", null);

    assertThat(entity.getTask()).isSameAs(task);
    assertThat(entity.getTask()).isSameAs(task);

    verify(taskManager).findTaskByCaseExecutionId("e1");
  }

  @Test
  public void shouldLoadCaseInstanceByIdAndTakeItsIdWhenSet() {
    CaseExecutionEntity instance = entity("ci", null);
    when(caseExecutionManager.findCaseExecutionById("ci")).thenReturn(instance);
    CaseExecutionEntity entity = entity("e1", "ci");
    entity.caseInstanceId = "ci";

    assertThat(entity.getCaseInstance()).isSameAs(instance);

    CaseExecutionEntity other = entity("e2", "ci");
    other.setCaseInstance(instance);
    assertThat(other.getCaseInstanceId()).isEqualTo("ci");
    other.setCaseInstance(null);
    assertThat(other.getCaseInstanceId()).isEqualTo("ci");
  }

  // creation --------------------------------------------------------------------------------

  @Test
  public void shouldNotProvideTenantIdWhenNoProviderIsConfigured() {
    CaseExecutionEntity entity = entity("e1", null);

    entity.provideTenantId(new HashMap<String, Object>());

    assertThat(entity.getTenantId()).isNull();
  }

  protected TenantIdProviderCaseInstanceContext provideTenant(CaseExecutionEntity entity) {
    TenantIdProvider provider = mock(TenantIdProvider.class);
    when(provider.provideTenantIdForCaseInstance(any())).thenReturn("tenant-1");
    when(config.getTenantIdProvider()).thenReturn(provider);
    CaseDefinitionEntity definition = mock(CaseDefinitionEntity.class);
    entity.setCaseDefinition(definition);
    Map<String, Object> variables = new HashMap<>();
    variables.put("v", 1);

    entity.provideTenantId(variables);

    ArgumentCaptor<TenantIdProviderCaseInstanceContext> captor = ArgumentCaptor.forClass(TenantIdProviderCaseInstanceContext.class);
    verify(provider).provideTenantIdForCaseInstance(captor.capture());
    assertThat(entity.getTenantId()).isEqualTo("tenant-1");
    assertThat(captor.getValue().getCaseDefinition()).isSameAs(definition);
    assertThat(captor.getValue().getVariables()).containsEntry("v", 1);
    return captor.getValue();
  }

  @Test
  public void shouldProvideTenantIdForStandaloneCaseInstance() {
    TenantIdProviderCaseInstanceContext ctx = provideTenant(entity("e1", null));

    assertThat(ctx.getSuperExecution()).isNull();
    assertThat(ctx.getSuperCaseExecution()).isNull();
  }

  @Test
  public void shouldProvideTenantIdWithSuperExecutionContext() {
    ExecutionEntity superExecution = mock(ExecutionEntity.class);
    when(executionManager.findExecutionById("sx")).thenReturn(superExecution);
    CaseExecutionEntity entity = entity("e1", null);
    entity.superExecutionId = "sx";

    TenantIdProviderCaseInstanceContext ctx = provideTenant(entity);

    assertThat(ctx.getSuperExecution()).isSameAs(superExecution);
  }

  @Test
  public void shouldProvideTenantIdWithSuperCaseExecutionContext() {
    CaseExecutionEntity superCase = entity("sc", null);
    when(caseExecutionManager.findCaseExecutionById("sc")).thenReturn(superCase);
    CaseExecutionEntity entity = entity("e1", null);
    entity.superCaseExecutionId = "sc";

    TenantIdProviderCaseInstanceContext ctx = provideTenant(entity);

    assertThat(ctx.getSuperCaseExecution()).isSameAs(superCase);
  }

  @Test
  public void shouldCreateChildExecutionInheritingDefinitionInstanceAndTenant() {
    CaseDefinitionEntity definition = mock(CaseDefinitionEntity.class);
    CaseExecutionEntity parent = entity("p", null);
    parent.caseExecutions = new ArrayList<>();
    parent.setCaseInstance(parent);
    parent.setCaseDefinition(definition);
    parent.setTenantId("tenant");
    CmmnActivity activity = activity("act", "Name", "humanTask", null);

    CaseExecutionEntity child = parent.createCaseExecution(activity);

    verify(caseExecutionManager).insertCaseExecution(child);
    assertThat(child.getActivityId()).isEqualTo("act");
    assertThat(child.getParent()).isSameAs(parent);
    assertThat(child.getCaseInstance()).isSameAs(parent);
    assertThat(child.getCaseDefinition()).isSameAs(definition);
    assertThat(child.getTenantId()).isEqualTo("tenant");
    assertThat(parent.getCaseExecutions()).containsExactly(child);
  }

  @Test
  public void shouldLeaveTenantUnsetOnChildWhenParentHasNone() {
    CaseExecutionEntity parent = entity("p", null);
    parent.caseExecutions = new ArrayList<>();
    parent.setCaseInstance(parent);
    parent.setCaseDefinition(mock(CaseDefinitionEntity.class));

    assertThat(parent.createCaseExecution(activity("act", "n", null, null)).getTenantId()).isNull();
  }

  // super execution / sub process instance --------------------------------------------------

  @Test
  public void shouldLinkAndUnlinkSuperExecution() {
    ExecutionEntity first = mock(ExecutionEntity.class);
    when(first.getId()).thenReturn("x1");
    ExecutionEntity second = mock(ExecutionEntity.class);
    when(second.getId()).thenReturn("x2");
    CaseExecutionEntity entity = entity("e1", null);

    entity.setSuperExecution(first);
    assertThat(entity.getSuperExecutionId()).isEqualTo("x1");
    verify(first).setSubCaseInstance(entity);

    entity.setSuperExecution(second);
    verify(first).setSubCaseInstance(null);
    verify(second).setSubCaseInstance(entity);
    assertThat(entity.getSuperExecution()).isSameAs(second);

    entity.setSuperExecution(null);
    verify(second).setSubCaseInstance(null);
    assertThat(entity.getSuperExecutionId()).isNull();
  }

  @Test
  public void shouldLoadSuperExecutionById() {
    ExecutionEntity superExecution = mock(ExecutionEntity.class);
    when(executionManager.findExecutionById("sx")).thenReturn(superExecution);
    CaseExecutionEntity entity = entity("e1", null);
    entity.setSuperExecutionId("sx");

    assertThat(entity.getSuperExecution()).isSameAs(superExecution);
  }

  @Test
  public void shouldLoadSubProcessInstanceBySuperCaseExecutionOnce() {
    ExecutionEntity sub = mock(ExecutionEntity.class);
    when(executionManager.findSubProcessInstanceBySuperCaseExecutionId("e1")).thenReturn(sub);
    CaseExecutionEntity entity = entity("e1", null);

    assertThat(entity.getSubProcessInstance()).isSameAs(sub);
    assertThat(entity.getSubProcessInstance()).isSameAs(sub);

    verify(executionManager).findSubProcessInstanceBySuperCaseExecutionId("e1");
  }

  protected void noHistory() {
    HistoryLevel level = mock(HistoryLevel.class);
    when(config.getHistoryLevel()).thenReturn(level);
  }

  @Test
  public void shouldCreateSubProcessInstanceWithDefinitionTenant() {
    noHistory();
    ProcessDefinitionEntity definition = mock(ProcessDefinitionEntity.class);
    ExecutionEntity created = mock(ExecutionEntity.class);
    when(definition.getTenantId()).thenReturn("def-tenant");
    when(definition.createProcessInstance("bk", "ci")).thenReturn(created);
    CaseExecutionEntity entity = entity("e1", null);
    entity.caseInstanceId = "ci";
    entity.setTenantId("case-tenant");

    ExecutionEntity result = entity.createSubProcessInstance(definition, "bk");

    assertThat(result).isSameAs(created);
    verify(created).setTenantId("def-tenant");
    verify(created).setSuperCaseExecution(entity);
    assertThat(entity.getSubProcessInstance()).isSameAs(created);
  }

  @Test
  public void shouldCreateSubProcessInstanceInheritingCaseTenantWhenDefinitionHasNone() {
    noHistory();
    ProcessDefinitionEntity definition = mock(ProcessDefinitionEntity.class);
    ExecutionEntity created = mock(ExecutionEntity.class);
    when(definition.createProcessInstance(null, "ci")).thenReturn(created);
    CaseExecutionEntity entity = entity("e1", null);
    entity.caseInstanceId = "ci";
    entity.setTenantId("case-tenant");

    entity.createSubProcessInstance(definition);

    verify(created).setTenantId("case-tenant");
  }

  // sub / super case instance ---------------------------------------------------------------

  @Test
  public void shouldLoadSubCaseInstanceBySuperCaseExecutionOnce() {
    CaseExecutionEntity sub = entity("sub", null);
    when(caseExecutionManager.findSubCaseInstanceBySuperCaseExecutionId("e1")).thenReturn(sub);
    CaseExecutionEntity entity = entity("e1", null);

    assertThat(entity.getSubCaseInstance()).isSameAs(sub);
    assertThat(entity.getSubCaseInstance()).isSameAs(sub);

    verify(caseExecutionManager).findSubCaseInstanceBySuperCaseExecutionId("e1");
  }

  @Test
  public void shouldCreateSubCaseInstanceWithDefinitionTenant() {
    noHistory();
    CaseDefinitionEntity definition = mock(CaseDefinitionEntity.class);
    CaseExecutionEntity created = entity("sub", null);
    when(definition.getTenantId()).thenReturn("def-tenant");
    when(definition.createCaseInstance("bk")).thenReturn(created);
    CaseExecutionEntity entity = entity("e1", null);
    entity.setTenantId("case-tenant");

    CaseExecutionEntity result = entity.createSubCaseInstance(definition, "bk");

    assertThat(result).isSameAs(created);
    assertThat(created.getTenantId()).isEqualTo("def-tenant");
    assertThat(created.getSuperCaseExecutionId()).isEqualTo("e1");
    assertThat(entity.getSubCaseInstance()).isSameAs(created);
  }

  @Test
  public void shouldCreateSubCaseInstanceInheritingCaseTenantWhenDefinitionHasNone() {
    noHistory();
    CaseDefinitionEntity definition = mock(CaseDefinitionEntity.class);
    CaseExecutionEntity created = entity("sub", null);
    when(definition.createCaseInstance(null)).thenReturn(created);
    CaseExecutionEntity entity = entity("e1", null);
    entity.setTenantId("case-tenant");

    entity.createSubCaseInstance(definition);

    assertThat(created.getTenantId()).isEqualTo("case-tenant");
  }

  @Test
  public void shouldFireHistoricUpdateOnlyWhenHistoryLevelProducesTheEvent() {
    HistoryLevel level = mock(HistoryLevel.class);
    CmmnHistoryEventProducer producer = mock(CmmnHistoryEventProducer.class);
    HistoryEventHandler handler = mock(HistoryEventHandler.class);
    HistoryEvent event = mock(HistoryEvent.class);
    CaseExecutionEntity entity = entity("e1", null);
    when(config.getHistoryLevel()).thenReturn(level);
    when(config.getCmmnHistoryEventProducer()).thenReturn(producer);
    when(config.getHistoryEventHandler()).thenReturn(handler);
    when(producer.createCaseActivityInstanceUpdateEvt(entity)).thenReturn(event);

    entity.fireHistoricCaseActivityInstanceUpdate();
    verify(handler, never()).handleEvent(any());

    when(level.isHistoryEventProduced(HistoryEventTypes.CASE_ACTIVITY_INSTANCE_UPDATE, entity)).thenReturn(true);
    entity.fireHistoricCaseActivityInstanceUpdate();
    verify(handler).handleEvent(event);
  }

  @Test
  public void shouldLinkSuperCaseExecution() {
    CaseExecutionEntity superCase = entity("sc", null);
    CaseExecutionEntity entity = entity("e1", null);

    entity.setSuperCaseExecution(superCase);
    assertThat(entity.getSuperCaseExecutionId()).isEqualTo("sc");
    assertThat(entity.getSuperCaseExecution()).isSameAs(superCase);

    entity.setSuperCaseExecution(null);
    assertThat(entity.getSuperCaseExecutionId()).isNull();
  }

  @Test
  public void shouldLoadSuperCaseExecutionById() {
    CaseExecutionEntity superCase = entity("sc", null);
    when(caseExecutionManager.findCaseExecutionById("sc")).thenReturn(superCase);
    CaseExecutionEntity entity = entity("e1", null);
    entity.setSuperCaseExecutionId("sc");

    assertThat(entity.getSuperCaseExecution()).isSameAs(superCase);
  }

  // sentries --------------------------------------------------------------------------------

  protected CaseSentryPartEntity part(String sentryId) {
    CaseSentryPartEntity part = new CaseSentryPartEntity();
    part.setSentryId(sentryId);
    return part;
  }

  @Test
  public void shouldGroupLoadedSentryPartsBySentryId() {
    CaseSentryPartEntity a1 = part("a");
    CaseSentryPartEntity a2 = part("a");
    CaseSentryPartEntity b = part("b");
    when(sentryPartManager.findCaseSentryPartsByCaseExecutionId("e1")).thenReturn(new ArrayList<>(Arrays.asList(a1, a2, b)));
    CaseExecutionEntity entity = entity("e1", null);

    assertThat(entity.getCaseSentryParts()).containsExactly(a1, a2, b);
    assertThat(entity.getSentries()).containsOnlyKeys("a", "b");
    assertThat(entity.findSentry("a")).containsExactly(a1, a2);
    assertThat(entity.findSentry("missing")).isNull();
    verify(sentryPartManager).findCaseSentryPartsByCaseExecutionId("e1");
  }

  @Test
  public void shouldAddSentryPartToListAndIndex() {
    when(sentryPartManager.findCaseSentryPartsByCaseExecutionId("e1")).thenReturn(new ArrayList<>());
    CaseExecutionEntity entity = entity("e1", null);
    CaseSentryPartEntity added = part("s");

    entity.addSentryPart(added);

    assertThat(entity.getCaseSentryParts()).containsExactly(added);
    assertThat(entity.findSentry("s")).containsExactly((CmmnSentryPart) added);
  }

  @Test
  public void shouldInsertNewSentryPartThroughManager() {
    CaseSentryPartEntity created = entity("e1", null).newSentryPart();

    verify(sentryPartManager).insertCaseSentryPart(created);
  }

  // variables -------------------------------------------------------------------------------

  @Test
  public void shouldProvideVariablesFromVariableInstanceManager() {
    List<VariableInstanceEntity> all = Collections.singletonList(new VariableInstanceEntity());
    List<String> names = Collections.singletonList("x");
    when(variableInstanceManager.findVariableInstancesByCaseExecutionId("e1")).thenReturn(all);
    when(variableInstanceManager.findVariableInstancesByCaseExecutionIdAndVariableNames("e1", names)).thenReturn(all);
    CaseExecutionEntity entity = entity("e1", null);

    assertThat(entity.provideVariables()).isSameAs(all);
    assertThat(entity.provideVariables(names)).isSameAs(all);
  }

  @Test
  public void shouldUseEntityVariableStoreFactoryAndFiveLifecycleListeners() {
    CaseExecutionEntity entity = entity("e1", null);

    assertThat(entity.getVariableStore()).isNotNull();
    assertThat(entity.getVariableInstanceFactory()).isNotNull();
    assertThat(entity.getVariableInstanceLifecycleListeners()).hasSize(5);
  }

  // toString, remove, persistence -----------------------------------------------------------

  @Test
  public void shouldDescribeCaseInstanceAndCaseExecutionDifferently() {
    assertThat(entity("e1", null).toString()).isEqualTo("CaseInstance[e1]");
    assertThat(entity("e2", "e1").toString()).isEqualTo("CaseExecution[e2]");
  }

  @Test
  public void shouldDeleteSentryPartsAndExecutionOnRemove() {
    CaseSentryPartEntity p1 = part("a");
    CaseSentryPartEntity p2 = part("b");
    when(sentryPartManager.findCaseSentryPartsByCaseExecutionId("e1")).thenReturn(new ArrayList<>(Arrays.asList(p1, p2)));
    when(variableInstanceManager.findVariableInstancesByCaseExecutionId("e1")).thenReturn(Collections.emptyList());
    CaseExecutionEntity parent = entity("p", null);
    parent.caseExecutions = new ArrayList<>();
    CaseExecutionEntity entity = entity("e1", "p");
    entity.setParent(parent);
    parent.caseExecutions.add(entity);

    entity.remove();

    verify(sentryPartManager).deleteSentryPart(p1);
    verify(sentryPartManager).deleteSentryPart(p2);
    verify(caseExecutionManager).deleteCaseExecution(entity);
    assertThat(parent.caseExecutions).isEmpty();
  }

  @Test
  public void shouldIncrementRevision() {
    CaseExecutionEntity entity = entity("e1", null);

    assertThat(entity.getRevision()).isEqualTo(1);
    assertThat(entity.getRevisionNext()).isEqualTo(2);
    entity.setRevision(5);
    assertThat(entity.getRevision()).isEqualTo(5);
    assertThat(entity.getRevisionNext()).isEqualTo(6);
  }

  @Test
  public void shouldForceUpdateThroughDbEntityManager() {
    CaseExecutionEntity entity = entity("e1", null);

    entity.forceUpdate();

    verify(dbEntityManager).forceUpdate(entity);
  }

  @Test
  public void shouldReferenceParentAndSuperCaseExecution() {
    CaseExecutionEntity entity = entity("e1", "p");
    entity.superCaseExecutionId = "sc";
    entity.caseDefinitionId = "def:1";

    assertThat(entity.getReferencedEntityIds()).containsExactlyInAnyOrder("p", "sc");
    assertThat(entity.getReferencedEntitiesIdAndClass()).hasSize(3)
        .containsEntry("p", CaseExecutionEntity.class)
        .containsEntry("sc", CaseExecutionEntity.class)
        .containsEntry("def:1", org.camunda.bpm.engine.impl.cmmn.model.CmmnCaseDefinition.class);
  }

  @Test
  public void shouldReferenceNothingForRootWithoutDefinition() {
    CaseExecutionEntity entity = entity("e1", null);

    assertThat(entity.getReferencedEntityIds()).isEmpty();
    assertThat(entity.getReferencedEntitiesIdAndClass()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReportPersistentState() {
    CaseExecutionEntity entity = entity("e1", "p");
    entity.caseDefinitionId = "def:1";
    entity.activityId = "act";
    entity.superExecutionId = "sx";
    entity.setBusinessKey("bk");

    Map<String, Object> state = (Map<String, Object>) entity.getPersistentState();

    assertThat(state).containsEntry("caseDefinitionId", "def:1").containsEntry("businessKey", "bk")
        .containsEntry("activityId", "act").containsEntry("parentId", "p")
        .containsEntry("superExecutionId", "sx")
        .containsKeys("currentState", "previousState");
  }

  // model access ----------------------------------------------------------------------------

  @Test
  public void shouldHaveNoModelWithoutCaseDefinitionId() {
    CaseExecutionEntity entity = entity("e1", null);

    assertThat(entity.getCmmnModelInstance()).isNull();
    assertThat(entity.getCmmnModelElementInstance()).isNull();
  }

  @Test
  public void shouldResolveModelElementOfActivity() {
    CmmnModelInstance model = mock(CmmnModelInstance.class);
    CmmnElement element = mock(CmmnElement.class);
    when(deploymentCache.findCmmnModelInstanceForCaseDefinition("def:1")).thenReturn(model);
    when(model.getModelElementById("act")).thenReturn(element);
    CaseExecutionEntity entity = entity("e1", null);
    entity.caseDefinitionId = "def:1";
    entity.activityId = "act";

    assertThat(entity.getCmmnModelInstance()).isSameAs(model);
    assertThat(entity.getCmmnModelElementInstance()).isSameAs(element);
  }

  @Test
  public void shouldFailWhenModelElementIsNotACmmnElement() {
    CmmnModelInstance model = mock(CmmnModelInstance.class);
    ModelElementInstance notCmmn = mock(ModelElementInstance.class);
    ModelElementType type = mock(ModelElementType.class);
    when(type.getTypeName()).thenReturn("foo");
    when(type.getTypeNamespace()).thenReturn("urn:ns");
    when(notCmmn.getElementType()).thenReturn(type);
    when(deploymentCache.findCmmnModelInstanceForCaseDefinition("def:1")).thenReturn(model);
    when(model.getModelElementById("act")).thenReturn(notCmmn);
    CaseExecutionEntity entity = entity("e1", null);
    entity.caseDefinitionId = "def:1";
    entity.activityId = "act";

    assertThatThrownBy(entity::getCmmnModelElementInstance)
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Cannot cast")
        .hasMessageContaining("foo")
        .hasMessageContaining("urn:ns")
        .hasCauseInstanceOf(ClassCastException.class);
  }

  // engine access and operations ------------------------------------------------------------

  @Test
  public void shouldExposeProcessEngineOfConfiguration() {
    ProcessEngineImpl engine = mock(ProcessEngineImpl.class);
    when(config.getProcessEngine()).thenReturn(engine);
    CaseExecutionEntity entity = entity("e1", null);

    assertThat(entity.getProcessEngine()).isSameAs(engine);
    assertThat(entity.getProcessEngineServices()).isSameAs(engine);
  }

  @Test
  public void shouldPerformOperationsThroughCommandContext() {
    org.camunda.bpm.engine.impl.cmmn.operation.CmmnAtomicOperation operation =
        mock(org.camunda.bpm.engine.impl.cmmn.operation.CmmnAtomicOperation.class);
    CaseExecutionEntity entity = entity("e1", null);

    entity.performOperation(operation);
    entity.performOperationSync(operation);

    verify(commandContext, org.mockito.Mockito.times(2)).performOperation(eq(operation), eq(entity));
  }
}
