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
package org.camunda.bpm.engine.impl.pvm.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.bpmn.behavior.CancelBoundaryEventActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.CancelEndEventActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.CompensationEventActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.EventSubProcessActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.ParallelMultiInstanceActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.ReceiveTaskActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.SequentialMultiInstanceActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.SubProcessActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParse;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.jobexecutor.AsyncContinuationJobHandler;
import org.camunda.bpm.engine.impl.persistence.entity.ActivityInstanceImpl;
import org.camunda.bpm.engine.impl.persistence.entity.EventSubscriptionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.JobDefinitionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.impl.pvm.PvmActivity;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.camunda.bpm.engine.impl.pvm.delegate.CompositeActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Behavioral tests for {@link LegacyBehavior}: every method is a pure function over
 * execution / scope trees, so the trees are built from mocks.
 */
public class LegacyBehaviorTest {

  // concurrent scopes ///////////////////////////////////////////////////////////////

  private static PvmExecutionImpl execution(boolean scope, boolean concurrent) {
    PvmExecutionImpl execution = mock(PvmExecutionImpl.class);
    when(execution.isScope()).thenReturn(scope);
    when(execution.isConcurrent()).thenReturn(concurrent);
    return execution;
  }

  @Test
  public void pruneConcurrentScopeMakesExecutionNonConcurrent() {
    PvmExecutionImpl execution = execution(true, true);

    LegacyBehavior.pruneConcurrentScope(execution);

    verify(execution).setConcurrent(false);
  }

  @Test
  public void pruneConcurrentScopeRejectsNonScope() {
    PvmExecutionImpl execution = execution(false, true);

    assertThatThrownBy(() -> LegacyBehavior.pruneConcurrentScope(execution))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Execution must be scope.");
    verify(execution, never()).setConcurrent(false);
  }

  @Test
  public void pruneConcurrentScopeRejectsNonConcurrent() {
    PvmExecutionImpl execution = execution(true, false);

    assertThatThrownBy(() -> LegacyBehavior.pruneConcurrentScope(execution))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Execution must be concurrent.");
  }

  @Test
  public void cancelConcurrentScopeInterruptsLeavesAndDestroys() {
    PvmExecutionImpl execution = execution(true, true);
    PvmActivity cancelled = mock(ActivityImpl.class);
    when(cancelled.toString()).thenReturn("act");

    LegacyBehavior.cancelConcurrentScope(execution, cancelled);

    InOrder order = inOrder(execution);
    order.verify(execution).interrupt("Scope act cancelled.");
    order.verify(execution).setActivity(cancelled);
    order.verify(execution).leaveActivityInstance();
    order.verify(execution).interrupt("Scope act cancelled.");
    order.verify(execution).destroy();
  }

  @Test
  public void cancelConcurrentScopeRejectsNonConcurrentScope() {
    PvmExecutionImpl execution = execution(true, false);

    assertThatThrownBy(() -> LegacyBehavior.cancelConcurrentScope(execution, mock(PvmActivity.class)))
        .isInstanceOf(ProcessEngineException.class);
    verify(execution, never()).destroy();
  }

  @Test
  public void destroyConcurrentScopeDestroysExecution() {
    PvmExecutionImpl execution = execution(true, true);

    LegacyBehavior.destroyConcurrentScope(execution);

    verify(execution).destroy();
  }

  @Test
  public void destroyConcurrentScopeRejectsNonConcurrentScope() {
    PvmExecutionImpl execution = execution(true, false);

    assertThatThrownBy(() -> LegacyBehavior.destroyConcurrentScope(execution))
        .isInstanceOf(ProcessEngineException.class);
    verify(execution, never()).destroy();
  }

  @Test
  public void isConcurrentScopeRequiresBothFlags() {
    assertThat(LegacyBehavior.isConcurrentScope(execution(true, true))).isTrue();
    assertThat(LegacyBehavior.isConcurrentScope(execution(true, false))).isFalse();
    assertThat(LegacyBehavior.isConcurrentScope(execution(false, true))).isFalse();
    assertThat(LegacyBehavior.isConcurrentScope(execution(false, false))).isFalse();
  }

  // legacy event subprocess ////////////////////////////////////////////////////////

  /**
   * Builds an ActivityExecution whose current activity is {@code activity} and whose
   * activity-execution mapping assigns {@code activityExecution} to the activity and
   * {@code flowScopeExecution} to its flow scope.
   */
  private static ActivityExecution mappedExecution(ActivityImpl activity, ScopeImpl flowScope,
      PvmExecutionImpl activityExecution, PvmExecutionImpl flowScopeExecution) {
    ActivityExecution execution = mock(ActivityExecution.class);
    when(execution.getActivity()).thenReturn(activity);
    when(activity.getFlowScope()).thenReturn(flowScope);
    Map<ScopeImpl, PvmExecutionImpl> mapping = new HashMap<>();
    mapping.put(activity, activityExecution);
    mapping.put(flowScope, flowScopeExecution);
    when(execution.createActivityExecutionMapping()).thenReturn(mapping);
    return execution;
  }

  @Test
  public void eventSubprocessCompleteEndsExecutionWhenScopeSharedWithFlowScope() {
    ActivityImpl activity = mock(ActivityImpl.class);
    when(activity.isScope()).thenReturn(true);
    PvmExecutionImpl shared = mock(PvmExecutionImpl.class);
    ActivityExecution execution = mappedExecution(activity, mock(ActivityImpl.class), shared, shared);

    assertThat(LegacyBehavior.eventSubprocessComplete(execution)).isTrue();
    verify(execution).end(false);
  }

  @Test
  public void eventSubprocessCompleteIsNoopWhenOwnScopeExecutionExists() {
    ActivityImpl activity = mock(ActivityImpl.class);
    when(activity.isScope()).thenReturn(true);
    ActivityExecution execution = mappedExecution(activity, mock(ActivityImpl.class),
        mock(PvmExecutionImpl.class), mock(PvmExecutionImpl.class));

    assertThat(LegacyBehavior.eventSubprocessComplete(execution)).isFalse();
    verify(execution, never()).end(false);
  }

  @Test
  public void eventSubprocessCompleteUsesFlowScopeForNonScopeActivity() {
    // non-scope activity: the check is performed for its flow scope vs. the flow scope's flow scope
    ActivityImpl activity = mock(ActivityImpl.class);
    when(activity.isScope()).thenReturn(false);
    ActivityImpl flowScope = mock(ActivityImpl.class);
    ScopeImpl grandParent = mock(ScopeImpl.class);
    when(flowScope.getFlowScope()).thenReturn(grandParent);
    PvmExecutionImpl shared = mock(PvmExecutionImpl.class);
    ActivityExecution execution = mappedExecution(activity, flowScope, mock(PvmExecutionImpl.class), mock(PvmExecutionImpl.class));
    Map<ScopeImpl, PvmExecutionImpl> mapping = new HashMap<>();
    mapping.put(flowScope, shared);
    mapping.put(grandParent, shared);
    when(execution.createActivityExecutionMapping()).thenReturn(mapping);

    assertThat(LegacyBehavior.eventSubprocessComplete(execution)).isTrue();
  }

  private static ActivityExecution endedExecution(ActivityImpl activity, boolean legacy) {
    ActivityExecution ended = mock(ActivityExecution.class);
    when(ended.getActivity()).thenReturn(activity);
    PvmExecutionImpl shared = mock(PvmExecutionImpl.class);
    ScopeImpl flowScope = mock(ScopeImpl.class);
    when(activity.isScope()).thenReturn(true);
    when(activity.getFlowScope()).thenReturn(flowScope);
    Map<ScopeImpl, PvmExecutionImpl> mapping = new HashMap<>();
    mapping.put(activity, shared);
    mapping.put(flowScope, legacy ? shared : mock(PvmExecutionImpl.class));
    when(ended.createActivityExecutionMapping()).thenReturn(mapping);
    return ended;
  }

  @Test
  public void concurrentChildEndedIsNoopIfNotLegacy() {
    ActivityExecution scopeExecution = mock(ActivityExecution.class);
    ActivityExecution ended = endedExecution(mock(ActivityImpl.class), false);

    assertThat(LegacyBehavior.eventSubprocessConcurrentChildExecutionEnded(scopeExecution, ended)).isFalse();
    verify(ended, never()).remove();
  }

  @Test
  public void concurrentChildEndedWithoutFlowScopeDoesNotNotify() {
    ActivityExecution scopeExecution = mock(ActivityExecution.class);
    ActivityImpl activity = mock(ActivityImpl.class);
    ActivityExecution ended = endedExecution(activity, true);
    // flow scope present for the legacy check, but notification looks at it again
    ScopeImpl flowScope = activity.getFlowScope();
    when(flowScope.getFlowScope()).thenReturn(null);

    assertThat(LegacyBehavior.eventSubprocessConcurrentChildExecutionEnded(scopeExecution, ended)).isTrue();
    verify(ended, never()).remove();
    verify(scopeExecution, never()).tryPruneLastConcurrentChild();
  }

  @Test
  public void concurrentChildEndedInProcessDefinitionRemovesAndPrunes() {
    ActivityExecution scopeExecution = mock(ActivityExecution.class);
    ActivityImpl activity = mock(ActivityImpl.class);
    ActivityExecution ended = endedExecution(activity, true);
    ScopeImpl flowScope = activity.getFlowScope();
    ProcessDefinitionImpl processDefinition = mock(ProcessDefinitionImpl.class);
    when(flowScope.getFlowScope()).thenReturn(processDefinition);
    when(activity.getProcessDefinition()).thenReturn(processDefinition);

    assertThat(LegacyBehavior.eventSubprocessConcurrentChildExecutionEnded(scopeExecution, ended)).isTrue();

    verify(ended).remove();
    verify(scopeExecution).tryPruneLastConcurrentChild();
    verify(scopeExecution).forceUpdate();
  }

  @Test
  public void concurrentChildEndedNotifiesCompositeGrandparentBehavior() {
    ActivityExecution scopeExecution = mock(ActivityExecution.class);
    ActivityImpl activity = mock(ActivityImpl.class);
    ActivityExecution ended = endedExecution(activity, true);
    ScopeImpl flowScope = activity.getFlowScope();
    ActivityImpl grandParent = mock(ActivityImpl.class);
    CompositeActivityBehavior composite = mock(CompositeActivityBehavior.class);
    when(grandParent.getActivityBehavior()).thenReturn(composite);
    when(flowScope.getFlowScope()).thenReturn(grandParent);
    when(activity.getProcessDefinition()).thenReturn(mock(ProcessDefinitionImpl.class));

    assertThat(LegacyBehavior.eventSubprocessConcurrentChildExecutionEnded(scopeExecution, ended)).isTrue();

    verify(composite).concurrentChildExecutionEnded(scopeExecution, ended);
    verify(ended, never()).remove();
  }

  @Test
  public void concurrentChildEndedIgnoresNonCompositeGrandparentBehavior() {
    ActivityExecution scopeExecution = mock(ActivityExecution.class);
    ActivityImpl activity = mock(ActivityImpl.class);
    ActivityExecution ended = endedExecution(activity, true);
    ScopeImpl flowScope = activity.getFlowScope();
    ActivityImpl grandParent = mock(ActivityImpl.class);
    when(grandParent.getActivityBehavior()).thenReturn(mock(UserTaskActivityBehavior.class));
    when(flowScope.getFlowScope()).thenReturn(grandParent);
    when(activity.getProcessDefinition()).thenReturn(mock(ProcessDefinitionImpl.class));

    assertThat(LegacyBehavior.eventSubprocessConcurrentChildExecutionEnded(scopeExecution, ended)).isTrue();

    verify(ended, never()).remove();
  }

  @Test
  public void destroySecondNonScopeReportsLegacyBehaviorWithoutTouchingExecution() {
    ActivityImpl activity = mock(ActivityImpl.class);
    when(activity.isScope()).thenReturn(true);
    PvmExecutionImpl shared = execution(true, false);
    ScopeImpl flowScope = mock(ScopeImpl.class);
    when(shared.getActivity()).thenReturn(activity);
    when(activity.getFlowScope()).thenReturn(flowScope);
    Map<ScopeImpl, PvmExecutionImpl> mapping = new HashMap<>();
    mapping.put(activity, shared);
    mapping.put(flowScope, shared);
    when(shared.createActivityExecutionMapping()).thenReturn(mapping);

    assertThat(LegacyBehavior.destroySecondNonScope(shared)).isTrue();
    verify(shared, never()).destroy();
  }

  @Test
  public void destroySecondNonScopeRejectsNonScopeExecution() {
    PvmExecutionImpl execution = execution(false, false);

    assertThatThrownBy(() -> LegacyBehavior.destroySecondNonScope(execution))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Execution must be scope.");
  }

  @Test
  public void getScopeExecutionResolvesThroughFlowScope() {
    ScopeImpl scope = mock(ScopeImpl.class);
    ScopeImpl flowScope = mock(ScopeImpl.class);
    when(scope.getFlowScope()).thenReturn(flowScope);
    PvmExecutionImpl flowScopeExecution = mock(PvmExecutionImpl.class);
    Map<ScopeImpl, PvmExecutionImpl> mapping = new HashMap<>();
    mapping.put(flowScope, flowScopeExecution);

    assertThat(LegacyBehavior.getScopeExecution(scope, mapping)).isSameAs(flowScopeExecution);
  }

  // activity execution mapping /////////////////////////////////////////////////////

  private static ActivityImpl activityWithBehavior(Object behavior, ScopeImpl flowScope) {
    ActivityImpl activity = mock(ActivityImpl.class);
    when(activity.getActivityBehavior()).thenReturn((org.camunda.bpm.engine.impl.pvm.delegate.ActivityBehavior) behavior);
    when(activity.getFlowScope()).thenReturn(flowScope);
    return activity;
  }

  @Test
  public void mappingInSyncAssignsOneExecutionPerScope() {
    ProcessDefinitionImpl pd = mock(ProcessDefinitionImpl.class);
    ActivityImpl sub = activityWithBehavior(mock(UserTaskActivityBehavior.class), pd);
    ActivityImpl task = activityWithBehavior(mock(UserTaskActivityBehavior.class), sub);
    PvmExecutionImpl pi = mock(PvmExecutionImpl.class);
    PvmExecutionImpl subExec = mock(PvmExecutionImpl.class);
    PvmExecutionImpl taskExec = mock(PvmExecutionImpl.class);

    // lists are given leaf-first
    List<PvmExecutionImpl> executions = new ArrayList<>(Arrays.asList(taskExec, subExec, pi));
    List<ScopeImpl> scopes = new ArrayList<>(Arrays.asList(task, sub, pd));

    Map<ScopeImpl, PvmExecutionImpl> mapping = LegacyBehavior.createActivityExecutionMapping(executions, scopes);

    assertThat(mapping).hasSize(3);
    assertThat(mapping.get(pd)).isSameAs(pi);
    assertThat(mapping.get(sub)).isSameAs(subExec);
    assertThat(mapping.get(task)).isSameAs(taskExec);
  }

  @Test
  public void mappingSkipsScopesThatWereNoScopesBefore() {
    ProcessDefinitionImpl pd = mock(ProcessDefinitionImpl.class);
    // event subprocess is a scope now, but it used to be none -> shares the parent's execution
    ActivityImpl eventSub = activityWithBehavior(mock(EventSubProcessActivityBehavior.class), pd);
    ActivityImpl task = activityWithBehavior(mock(UserTaskActivityBehavior.class), eventSub);
    PvmExecutionImpl pi = mock(PvmExecutionImpl.class);
    PvmExecutionImpl taskExec = mock(PvmExecutionImpl.class);

    List<PvmExecutionImpl> executions = new ArrayList<>(Arrays.asList(taskExec, pi));
    List<ScopeImpl> scopes = new ArrayList<>(Arrays.asList(task, eventSub, pd));

    Map<ScopeImpl, PvmExecutionImpl> mapping = LegacyBehavior.createActivityExecutionMapping(executions, scopes);

    assertThat(mapping.get(eventSub)).isSameAs(pi);
    assertThat(mapping.get(task)).isSameAs(taskExec);
  }

  @Test
  public void mappingFailsIfMoreExecutionsAreMissingThanExplained() {
    ProcessDefinitionImpl pd = mock(ProcessDefinitionImpl.class);
    ActivityImpl sub = activityWithBehavior(mock(UserTaskActivityBehavior.class), pd);
    ActivityImpl task = activityWithBehavior(mock(UserTaskActivityBehavior.class), sub);
    PvmExecutionImpl pi = mock(PvmExecutionImpl.class);

    List<PvmExecutionImpl> executions = new ArrayList<>(Collections.singletonList(pi));
    List<ScopeImpl> scopes = new ArrayList<>(Arrays.asList(task, sub, pd));

    assertThatThrownBy(() -> LegacyBehavior.createActivityExecutionMapping(executions, scopes))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Cannot construct activity-execution mapping");
  }

  @Test
  public void mappingDropsMultiInstanceBodyForLegacyAsyncExecution() {
    ProcessDefinitionImpl pd = mock(ProcessDefinitionImpl.class);
    ActivityImpl miBody = activityWithBehavior(mock(SequentialMultiInstanceActivityBehavior.class), pd);
    ActivityImpl inner = activityWithBehavior(mock(UserTaskActivityBehavior.class), miBody);
    when(inner.getParentFlowScopeActivity()).thenReturn(miBody);
    PvmExecutionImpl pi = mock(PvmExecutionImpl.class);
    PvmExecutionImpl deepest = mock(PvmExecutionImpl.class);
    when(deepest.getActivity()).thenReturn(inner);
    when(deepest.getActivityInstanceId()).thenReturn(null);

    List<PvmExecutionImpl> executions = new ArrayList<>(Arrays.asList(deepest, pi));
    List<ScopeImpl> scopes = new ArrayList<>(Arrays.asList(miBody, pd));

    Map<ScopeImpl, PvmExecutionImpl> mapping = LegacyBehavior.createActivityExecutionMapping(executions, scopes);

    assertThat(mapping).containsOnlyKeys(pd);
    assertThat(mapping.get(pd)).isSameAs(pi);
  }

  // wasNoScope //////////////////////////////////////////////////////////////////////

  @Test
  public void wasNoScope72MatchesEventSubprocess() {
    ActivityImpl activity = activityWithBehavior(mock(EventSubProcessActivityBehavior.class), null);

    assertThat(LegacyBehavior.wasNoScope72(activity)).isTrue();
  }

  @Test
  public void wasNoScope72MatchesSubprocessInSequentialMultiInstanceOnly() {
    ActivityImpl inSequential = activityWithBehavior(mock(SubProcessActivityBehavior.class),
        activityWithBehavior(mock(SequentialMultiInstanceActivityBehavior.class), null));
    ActivityImpl inParallel = activityWithBehavior(mock(SubProcessActivityBehavior.class),
        activityWithBehavior(mock(ParallelMultiInstanceActivityBehavior.class), null));
    ActivityImpl withoutParent = activityWithBehavior(mock(SubProcessActivityBehavior.class), null);

    assertThat(LegacyBehavior.wasNoScope72(inSequential)).isTrue();
    assertThat(LegacyBehavior.wasNoScope72(inParallel)).isFalse();
    assertThat(LegacyBehavior.wasNoScope72(withoutParent)).isFalse();
  }

  @Test
  public void wasNoScope72MatchesReceiveTaskInAnyMultiInstance() {
    ActivityImpl inSequential = activityWithBehavior(mock(ReceiveTaskActivityBehavior.class),
        activityWithBehavior(mock(SequentialMultiInstanceActivityBehavior.class), null));
    ActivityImpl inParallel = activityWithBehavior(mock(ReceiveTaskActivityBehavior.class),
        activityWithBehavior(mock(ParallelMultiInstanceActivityBehavior.class), null));
    ActivityImpl standalone = activityWithBehavior(mock(ReceiveTaskActivityBehavior.class), null);

    assertThat(LegacyBehavior.wasNoScope72(inSequential)).isTrue();
    assertThat(LegacyBehavior.wasNoScope72(inParallel)).isTrue();
    assertThat(LegacyBehavior.wasNoScope72(standalone)).isFalse();
  }

  @Test
  public void wasNoScope72IsFalseForOrdinaryActivity() {
    assertThat(LegacyBehavior.wasNoScope72(activityWithBehavior(mock(UserTaskActivityBehavior.class), null))).isFalse();
  }

  @Test
  public void wasNoScope73MatchesCompensationEventAndCancelEnd() {
    assertThat(LegacyBehavior.wasNoScope73(
        activityWithBehavior(mock(CompensationEventActivityBehavior.class), null), null)).isTrue();
    assertThat(LegacyBehavior.wasNoScope73(
        activityWithBehavior(mock(CancelEndEventActivityBehavior.class), null), null)).isTrue();
    assertThat(LegacyBehavior.wasNoScope73(
        activityWithBehavior(mock(UserTaskActivityBehavior.class), null), null)).isFalse();
  }

  @Test
  public void multiInstanceWithoutCandidateExecutionCountsAsInCompensation() {
    ActivityImpl mi = activityWithBehavior(mock(ParallelMultiInstanceActivityBehavior.class), null);

    assertThat(LegacyBehavior.isMultiInstanceInCompensation(mi, null)).isTrue();
    assertThat(LegacyBehavior.wasNoScope(mi, null)).isTrue();
  }

  @Test
  public void multiInstanceWithCandidateIsInCompensationOnlyWithThrowingAncestor() {
    ActivityImpl mi = activityWithBehavior(mock(ParallelMultiInstanceActivityBehavior.class), null);

    PvmExecutionImpl plain = mock(PvmExecutionImpl.class);
    assertThat(LegacyBehavior.isMultiInstanceInCompensation(mi, plain)).isFalse();

    ActivityImpl throwing = mock(ActivityImpl.class);
    when(throwing.getProperty(BpmnParse.PROPERTYNAME_THROWS_COMPENSATION)).thenReturn(Boolean.TRUE);
    PvmExecutionImpl throwingParent = mock(PvmExecutionImpl.class);
    when(throwingParent.getActivity()).thenReturn(throwing);
    PvmExecutionImpl child = mock(PvmExecutionImpl.class);
    when(child.getParent()).thenReturn(throwingParent);

    assertThat(LegacyBehavior.isMultiInstanceInCompensation(mi, child)).isTrue();
  }

  @Test
  public void nonMultiInstanceActivityIsNeverInCompensation() {
    ActivityImpl task = activityWithBehavior(mock(UserTaskActivityBehavior.class), null);

    assertThat(LegacyBehavior.isMultiInstanceInCompensation(task, null)).isFalse();
  }

  // legacy async at multi instance //////////////////////////////////////////////////

  @Test
  public void legacyAsyncAtMultiInstanceNeedsActivityMissingInstanceIdAndMiParent() {
    ActivityImpl miBody = activityWithBehavior(mock(SequentialMultiInstanceActivityBehavior.class), null);
    ActivityImpl inner = mock(ActivityImpl.class);
    when(inner.getParentFlowScopeActivity()).thenReturn(miBody);

    PvmExecutionImpl async = mock(PvmExecutionImpl.class);
    when(async.getActivity()).thenReturn(inner);
    assertThat(LegacyBehavior.isLegacyAsyncAtMultiInstance(async)).isTrue();

    PvmExecutionImpl withInstance = mock(PvmExecutionImpl.class);
    when(withInstance.getActivity()).thenReturn(inner);
    when(withInstance.getActivityInstanceId()).thenReturn("ai");
    assertThat(LegacyBehavior.isLegacyAsyncAtMultiInstance(withInstance)).isFalse();

    ActivityImpl noParent = mock(ActivityImpl.class);
    PvmExecutionImpl topLevel = mock(PvmExecutionImpl.class);
    when(topLevel.getActivity()).thenReturn(noParent);
    assertThat(LegacyBehavior.isLegacyAsyncAtMultiInstance(topLevel)).isFalse();

    ActivityImpl plainParent = activityWithBehavior(mock(UserTaskActivityBehavior.class), null);
    ActivityImpl underPlain = mock(ActivityImpl.class);
    when(underPlain.getParentFlowScopeActivity()).thenReturn(plainParent);
    PvmExecutionImpl underPlainExecution = mock(PvmExecutionImpl.class);
    when(underPlainExecution.getActivity()).thenReturn(underPlain);
    assertThat(LegacyBehavior.isLegacyAsyncAtMultiInstance(underPlainExecution)).isFalse();

    assertThat(LegacyBehavior.isLegacyAsyncAtMultiInstance(mock(PvmExecutionImpl.class))).isFalse();
  }

  // propagating execution on end ///////////////////////////////////////////////////

  @Test
  public void nonScopePropagatingExecutionIsReturnedAsIs() {
    PvmExecutionImpl execution = execution(false, false);

    assertThat(LegacyBehavior.determinePropagatingExecutionOnEnd(execution, new HashMap<>())).isSameAs(execution);
    verify(execution, never()).remove();
  }

  @Test
  public void mappedScopeExecutionIsReturnedAsIs() {
    PvmExecutionImpl execution = execution(true, false);
    Map<ScopeImpl, PvmExecutionImpl> mapping = new HashMap<>();
    mapping.put(mock(ScopeImpl.class), execution);

    assertThat(LegacyBehavior.determinePropagatingExecutionOnEnd(execution, mapping)).isSameAs(execution);
    verify(execution, never()).remove();
  }

  @Test
  public void superfluousScopeExecutionIsRemovedAndParentTakesItsActivity() {
    PvmExecutionImpl superfluous = execution(true, false);
    PvmExecutionImpl parent = mock(PvmExecutionImpl.class);
    ActivityImpl activity = mock(ActivityImpl.class);
    when(superfluous.getParent()).thenReturn(parent);
    when(superfluous.getActivity()).thenReturn(activity);

    PvmExecutionImpl result = LegacyBehavior.determinePropagatingExecutionOnEnd(superfluous, new HashMap<>());

    assertThat(result).isSameAs(parent);
    verify(superfluous).remove();
    verify(parent).setActivity(activity);
  }

  // legacy event subscription on parent //////////////////////////////////////////////

  private static EventSubscriptionEntity subscription(String type, String name, String activityId) {
    EventSubscriptionEntity subscription = mock(EventSubscriptionEntity.class);
    when(subscription.getEventType()).thenReturn(type);
    when(subscription.getEventName()).thenReturn(name);
    when(subscription.getActivityId()).thenReturn(activityId);
    return subscription;
  }

  private static ExecutionEntity executionAtReceiveTask(Object flowScopeBehavior, List<EventSubscriptionEntity> parentSubscriptions) {
    ExecutionEntity execution = mock(ExecutionEntity.class);
    ExecutionEntity parent = mock(ExecutionEntity.class);
    when(parent.getEventSubscriptions()).thenReturn(parentSubscriptions);
    when(execution.getParent()).thenReturn(parent);
    ActivityImpl flowScope = activityWithBehavior(flowScopeBehavior, null);
    ActivityImpl receiveTask = activityWithBehavior(mock(ReceiveTaskActivityBehavior.class), flowScope);
    when(execution.getActivity()).thenReturn(receiveTask);
    return execution;
  }

  @Test
  public void removeLegacySubscriptionIgnoresExecutionWithoutActivity() {
    ExecutionEntity execution = mock(ExecutionEntity.class);

    LegacyBehavior.removeLegacySubscriptionOnParent(execution, subscription("message", "m", "a"));

    verify(execution, never()).getParent();
  }

  @Test
  public void removeLegacySubscriptionDeletesOnlyMatchingParentSubscriptions() {
    EventSubscriptionEntity same = subscription("message", "m", "a");
    EventSubscriptionEntity otherName = subscription("message", "other", "a");
    EventSubscriptionEntity otherType = subscription("signal", "m", "a");
    EventSubscriptionEntity otherActivity = subscription("message", "m", "b");
    EventSubscriptionEntity nulls = subscription(null, null, null);
    ExecutionEntity execution = executionAtReceiveTask(mock(ParallelMultiInstanceActivityBehavior.class),
        Arrays.asList(same, otherName, otherType, otherActivity, nulls));

    LegacyBehavior.removeLegacySubscriptionOnParent(execution, subscription("message", "m", "a"));

    verify(same).delete();
    verify(otherName, never()).delete();
    verify(otherType, never()).delete();
    verify(otherActivity, never()).delete();
    verify(nulls, never()).delete();
  }

  @Test
  public void removeLegacySubscriptionTreatsNullFieldsAsEqual() {
    EventSubscriptionEntity nulls = subscription(null, null, null);
    ExecutionEntity execution = executionAtReceiveTask(mock(SequentialMultiInstanceActivityBehavior.class),
        Collections.singletonList(nulls));

    LegacyBehavior.removeLegacySubscriptionOnParent(execution, subscription(null, null, null));

    verify(nulls).delete();
  }

  @Test
  public void removeLegacySubscriptionRequiresMultiInstanceFlowScope() {
    EventSubscriptionEntity same = subscription("message", "m", "a");
    ExecutionEntity execution = executionAtReceiveTask(mock(UserTaskActivityBehavior.class),
        Collections.singletonList(same));

    LegacyBehavior.removeLegacySubscriptionOnParent(execution, subscription("message", "m", "a"));

    verify(same, never()).delete();
  }

  @Test
  public void removeLegacySubscriptionRequiresReceiveTask() {
    EventSubscriptionEntity same = subscription("message", "m", "a");
    ExecutionEntity execution = mock(ExecutionEntity.class);
    ActivityImpl flowScope = activityWithBehavior(mock(SequentialMultiInstanceActivityBehavior.class), null);
    ActivityImpl userTask = activityWithBehavior(mock(UserTaskActivityBehavior.class), flowScope);
    when(execution.getActivity()).thenReturn(userTask);

    LegacyBehavior.removeLegacySubscriptionOnParent(execution, subscription("message", "m", "a"));

    verify(same, never()).delete();
  }

  @Test
  public void removeLegacySubscriptionToleratesActivityWithoutFlowScope() {
    ExecutionEntity execution = mock(ExecutionEntity.class);
    ActivityImpl receiveTask = activityWithBehavior(mock(ReceiveTaskActivityBehavior.class), null);
    when(execution.getActivity()).thenReturn(receiveTask);

    LegacyBehavior.removeLegacySubscriptionOnParent(execution, subscription("message", "m", "a"));

    verify(execution, never()).getParent();
  }

  // removeLegacyNonScopesFromMapping ///////////////////////////////////////////////

  @Test
  public void legacyNonScopesAreRemovedFromMappingKeepingTopMostAndProcessDefinition() {
    ProcessDefinitionImpl pd = mock(ProcessDefinitionImpl.class);
    ActivityImpl outer = mock(ActivityImpl.class);
    ActivityImpl inner = mock(ActivityImpl.class);
    ActivityImpl lonely = mock(ActivityImpl.class);
    when(pd.getProcessDefinition()).thenReturn(pd);
    when(outer.getProcessDefinition()).thenReturn(pd);
    when(inner.getProcessDefinition()).thenReturn(pd);
    when(lonely.getProcessDefinition()).thenReturn(pd);
    when(outer.isAncestorFlowScopeOf(inner)).thenReturn(true);
    when(inner.isAncestorFlowScopeOf(outer)).thenReturn(false);

    PvmExecutionImpl shared = mock(PvmExecutionImpl.class);
    PvmExecutionImpl own = mock(PvmExecutionImpl.class);
    PvmExecutionImpl piExecution = mock(PvmExecutionImpl.class);
    Map<ScopeImpl, PvmExecutionImpl> mapping = new HashMap<>();
    mapping.put(pd, piExecution);
    mapping.put(outer, shared);
    mapping.put(inner, shared);
    mapping.put(lonely, own);

    LegacyBehavior.removeLegacyNonScopesFromMapping(mapping);

    assertThat(mapping).containsOnlyKeys(pd, outer, lonely);
  }

  @Test
  public void processDefinitionSharingItsExecutionStaysInMapping() {
    ProcessDefinitionImpl pd = mock(ProcessDefinitionImpl.class);
    ActivityImpl child = mock(ActivityImpl.class);
    when(pd.getProcessDefinition()).thenReturn(pd);
    when(child.getProcessDefinition()).thenReturn(pd);
    when(pd.isAncestorFlowScopeOf(child)).thenReturn(false);
    when(child.isAncestorFlowScopeOf(pd)).thenReturn(true);

    PvmExecutionImpl shared = mock(PvmExecutionImpl.class);
    Map<ScopeImpl, PvmExecutionImpl> mapping = new HashMap<>();
    mapping.put(pd, shared);
    mapping.put(child, shared);

    LegacyBehavior.removeLegacyNonScopesFromMapping(mapping);

    assertThat(mapping).containsKey(pd);
  }

  // activity instance repair //////////////////////////////////////////////////////

  @Test
  public void repairParentRelationshipsMovesSelfReferencingInstancesToProcessInstance() {
    ActivityInstanceImpl selfReferencing = new ActivityInstanceImpl();
    selfReferencing.setId("a");
    selfReferencing.setParentActivityInstanceId("a");
    ActivityInstanceImpl regular = new ActivityInstanceImpl();
    regular.setId("b");
    regular.setParentActivityInstanceId("a");
    ActivityInstanceImpl bothNull = new ActivityInstanceImpl();

    LegacyBehavior.repairParentRelationships(Arrays.asList(selfReferencing, regular, bothNull), "pi");

    assertThat(selfReferencing.getParentActivityInstanceId()).isEqualTo("pi");
    assertThat(regular.getParentActivityInstanceId()).isEqualTo("a");
    assertThat(bothNull.getParentActivityInstanceId()).isEqualTo("pi");
  }

  // job definition migration ///////////////////////////////////////////////////////

  private static JobDefinitionEntity jobDefinition(String activityId, String jobType) {
    JobDefinitionEntity jobDefinition = mock(JobDefinitionEntity.class);
    when(jobDefinition.getActivityId()).thenReturn(activityId);
    when(jobDefinition.getJobType()).thenReturn(jobType);
    return jobDefinition;
  }

  private static ActivityImpl wrappedActivity(ProcessDefinitionImpl pd, boolean asyncBefore, boolean asyncAfter) {
    ActivityImpl miBody = activityWithBehavior(mock(SequentialMultiInstanceActivityBehavior.class), pd);
    when(miBody.getId()).thenReturn("task#multiInstanceBody");
    when(miBody.getProcessDefinition()).thenReturn(pd);
    ActivityImpl inner = activityWithBehavior(mock(UserTaskActivityBehavior.class), miBody);
    when(inner.getProcessDefinition()).thenReturn(pd);
    when(inner.isAsyncBefore()).thenReturn(asyncBefore);
    when(inner.isAsyncAfter()).thenReturn(asyncAfter);
    return inner;
  }

  @Test
  public void asyncJobDefinitionOfWrappedActivityIsMovedToMultiInstanceBody() {
    ProcessDefinitionEntity pd = mock(ProcessDefinitionEntity.class);
    ActivityImpl inner = wrappedActivity(pd, false, false);
    when(pd.findActivity("task")).thenReturn(inner);
    JobDefinitionEntity definition = jobDefinition("task", AsyncContinuationJobHandler.TYPE);

    LegacyBehavior.migrateMultiInstanceJobDefinitions(pd, Collections.singletonList(definition));

    verify(definition).setActivityId("task#multiInstanceBody");
  }

  @Test
  public void jobDefinitionsThatNeedNoMigrationAreUntouched() {
    ProcessDefinitionEntity pd = mock(ProcessDefinitionEntity.class);

    // activity is itself async
    ActivityImpl asyncBefore = wrappedActivity(pd, true, false);
    ActivityImpl asyncAfter = wrappedActivity(pd, false, true);
    // not a multi instance child
    ActivityImpl topLevel = mock(ActivityImpl.class);
    when(topLevel.getFlowScope()).thenReturn(pd);
    when(topLevel.getProcessDefinition()).thenReturn(pd);
    // flow scope is an ordinary activity
    ActivityImpl underPlain = mock(ActivityImpl.class);
    ActivityImpl plainFlowScope = activityWithBehavior(mock(UserTaskActivityBehavior.class), pd);
    when(underPlain.getFlowScope()).thenReturn(plainFlowScope);
    when(underPlain.getProcessDefinition()).thenReturn(pd);
    // not an async-continuation job
    ActivityImpl wrapped = wrappedActivity(pd, false, false);

    when(pd.findActivity("asyncBefore")).thenReturn(asyncBefore);
    when(pd.findActivity("asyncAfter")).thenReturn(asyncAfter);
    when(pd.findActivity("top")).thenReturn(topLevel);
    when(pd.findActivity("plain")).thenReturn(underPlain);
    when(pd.findActivity("timer")).thenReturn(wrapped);

    List<JobDefinitionEntity> definitions = Arrays.asList(
        jobDefinition("asyncBefore", AsyncContinuationJobHandler.TYPE),
        jobDefinition("asyncAfter", AsyncContinuationJobHandler.TYPE),
        jobDefinition("top", AsyncContinuationJobHandler.TYPE),
        jobDefinition("plain", AsyncContinuationJobHandler.TYPE),
        jobDefinition("timer", "timer-transition"),
        jobDefinition(null, AsyncContinuationJobHandler.TYPE));

    LegacyBehavior.migrateMultiInstanceJobDefinitions(pd, definitions);

    for (JobDefinitionEntity definition : definitions) {
      verify(definition, never()).setActivityId(any());
    }
  }

  @Test
  public void repairMultiInstanceAsyncJobMovesExecutionToMultiInstanceBody() {
    ProcessDefinitionImpl pd = mock(ProcessDefinitionImpl.class);
    ActivityImpl inner = wrappedActivity(pd, false, false);
    ExecutionEntity execution = mock(ExecutionEntity.class);
    when(execution.getActivity()).thenReturn(inner);

    LegacyBehavior.repairMultiInstanceAsyncJob(execution);

    verify(execution).setActivity((ActivityImpl) inner.getFlowScope());
  }

  @Test
  public void repairMultiInstanceAsyncJobKeepsAsyncOrUnwrappedActivities() {
    ProcessDefinitionImpl pd = mock(ProcessDefinitionImpl.class);

    ExecutionEntity async = mock(ExecutionEntity.class);
    ActivityImpl asyncInner = wrappedActivity(pd, true, false);
    when(async.getActivity()).thenReturn(asyncInner);
    LegacyBehavior.repairMultiInstanceAsyncJob(async);
    verify(async, never()).setActivity(any(ActivityImpl.class));

    ActivityImpl topLevel = mock(ActivityImpl.class);
    when(topLevel.getFlowScope()).thenReturn(pd);
    when(topLevel.getProcessDefinition()).thenReturn(pd);
    ExecutionEntity unwrapped = mock(ExecutionEntity.class);
    when(unwrapped.getActivity()).thenReturn(topLevel);
    LegacyBehavior.repairMultiInstanceAsyncJob(unwrapped);
    verify(unwrapped, never()).setActivity(any(ActivityImpl.class));
  }

  // cancel boundary event //////////////////////////////////////////////////////////

  @Test
  public void signalCancelBoundaryEventMatchesCompensationDoneSignalOnly() {
    assertThat(LegacyBehavior.signalCancelBoundaryEvent("compensationDone")).isTrue();
    assertThat(LegacyBehavior.signalCancelBoundaryEvent("other")).isFalse();
    assertThat(LegacyBehavior.signalCancelBoundaryEvent(null)).isFalse();
  }

  @Test
  public void parseCancelBoundaryEventMarksActivityAsCompensationThrowing() {
    ActivityImpl activity = mock(ActivityImpl.class);

    LegacyBehavior.parseCancelBoundaryEvent(activity);

    verify(activity).setProperty(BpmnParse.PROPERTYNAME_THROWS_COMPENSATION, true);
  }

  // compensation throwing //////////////////////////////////////////////////////////

  private static PvmExecutionImpl throwingExecution(ActivityImpl activity) {
    when(activity.getProperty(BpmnParse.PROPERTYNAME_THROWS_COMPENSATION)).thenReturn(Boolean.TRUE);
    PvmExecutionImpl execution = mock(PvmExecutionImpl.class);
    when(execution.getActivity()).thenReturn(activity);
    return execution;
  }

  @Test
  public void invalidIntermediaryActivityIdNeedsNonLeafWhichIsNotCompensationThrowing() {
    PvmExecutionImpl leaf = mock(PvmExecutionImpl.class);
    Mockito.doReturn(Collections.emptyList()).when(leaf).getNonEventScopeExecutions();
    assertThat(LegacyBehavior.hasInvalidIntermediaryActivityId(leaf)).isFalse();

    PvmExecutionImpl child = mock(PvmExecutionImpl.class);
    PvmExecutionImpl nonLeaf = mock(PvmExecutionImpl.class);
    Mockito.doReturn(Collections.singletonList(child)).when(nonLeaf).getNonEventScopeExecutions();
    assertThat(LegacyBehavior.hasInvalidIntermediaryActivityId(nonLeaf)).isTrue();

    PvmExecutionImpl throwingNonLeaf = throwingExecution(mock(ActivityImpl.class));
    Mockito.doReturn(Collections.singletonList(child)).when(throwingNonLeaf).getNonEventScopeExecutions();
    assertThat(LegacyBehavior.hasInvalidIntermediaryActivityId(throwingNonLeaf)).isFalse();
  }

  @Test
  public void notCompensationThrowingExecutionIsNeverFlagged() {
    PvmExecutionImpl execution = mock(PvmExecutionImpl.class);
    when(execution.getActivity()).thenReturn(mock(ActivityImpl.class));

    assertThat(LegacyBehavior.isCompensationThrowing(execution, new HashMap<>())).isFalse();
  }

  @Test
  public void scopeThrowingActivityIsFlaggedIfItSharesExecutionWithFlowScope() {
    ActivityImpl activity = mock(ActivityImpl.class);
    ScopeImpl flowScope = mock(ScopeImpl.class);
    when(activity.isScope()).thenReturn(true);
    when(activity.getFlowScope()).thenReturn(flowScope);
    PvmExecutionImpl execution = throwingExecution(activity);
    PvmExecutionImpl shared = mock(PvmExecutionImpl.class);

    Map<ScopeImpl, PvmExecutionImpl> sharedMapping = new HashMap<>();
    sharedMapping.put(activity, shared);
    sharedMapping.put(flowScope, shared);
    assertThat(LegacyBehavior.isCompensationThrowing(execution, sharedMapping)).isTrue();

    Map<ScopeImpl, PvmExecutionImpl> dedicatedMapping = new HashMap<>();
    dedicatedMapping.put(activity, shared);
    dedicatedMapping.put(flowScope, mock(PvmExecutionImpl.class));
    assertThat(LegacyBehavior.isCompensationThrowing(execution, dedicatedMapping)).isFalse();
  }

  @Test
  public void nonScopeThrowingActivityIsFlaggedOnlyAtCancelBoundaryEvent() {
    ActivityImpl atCancelBoundary = mock(ActivityImpl.class);
    when(atCancelBoundary.isScope()).thenReturn(false);
    when(atCancelBoundary.getActivityBehavior()).thenReturn(mock(CancelBoundaryEventActivityBehavior.class));
    assertThat(LegacyBehavior.isCompensationThrowing(throwingExecution(atCancelBoundary), new HashMap<>())).isTrue();

    ActivityImpl elsewhere = mock(ActivityImpl.class);
    when(elsewhere.isScope()).thenReturn(false);
    when(elsewhere.getActivityBehavior()).thenReturn(mock(UserTaskActivityBehavior.class));
    assertThat(LegacyBehavior.isCompensationThrowing(throwingExecution(elsewhere), new HashMap<>())).isFalse();
  }

  @Test
  public void isCompensationThrowingBuildsTheMappingFromTheExecution() {
    ActivityImpl activity = mock(ActivityImpl.class);
    when(activity.isScope()).thenReturn(false);
    when(activity.getActivityBehavior()).thenReturn(mock(CancelBoundaryEventActivityBehavior.class));
    PvmExecutionImpl execution = throwingExecution(activity);
    when(execution.createActivityExecutionMapping()).thenReturn(new HashMap<>());

    assertThat(LegacyBehavior.isCompensationThrowing(execution)).isTrue();
    verify(execution).createActivityExecutionMapping();
  }

  @Test
  public void compensationThrowingAncestorIsFoundWalkingUp() {
    PvmExecutionImpl top = throwingExecution(mock(ActivityImpl.class));
    PvmExecutionImpl middle = mock(PvmExecutionImpl.class);
    when(middle.getParent()).thenReturn(top);
    PvmExecutionImpl leaf = mock(PvmExecutionImpl.class);
    when(leaf.getParent()).thenReturn(middle);

    assertThat(LegacyBehavior.findCompensationThrowingAncestorExecution(leaf)).isSameAs(top);
  }

  @Test
  public void noCompensationThrowingAncestorYieldsNull() {
    PvmExecutionImpl root = mock(PvmExecutionImpl.class);
    PvmExecutionImpl leaf = mock(PvmExecutionImpl.class);
    when(leaf.getParent()).thenReturn(root);

    assertThat(LegacyBehavior.findCompensationThrowingAncestorExecution(leaf)).isNull();
  }

  // topmost scope ////////////////////////////////////////////////////////////////////

  @Test
  public void topMostScopeIsTheAncestorOfTheOthers() {
    ScopeImpl outer = mock(ScopeImpl.class);
    ScopeImpl inner = mock(ScopeImpl.class);
    when(outer.isAncestorFlowScopeOf(inner)).thenReturn(true);

    assertThat(LegacyBehavior.getTopMostScope(Arrays.asList(inner, outer))).isSameAs(outer);
    assertThat(LegacyBehavior.getTopMostScope(Arrays.asList(outer, inner))).isSameAs(outer);
    assertThat(LegacyBehavior.getTopMostScope(Collections.emptyList())).isNull();
  }

  // historic variables ///////////////////////////////////////////////////////////////

  @Test
  public void createMissingHistoricVariablesToleratesMissingVariables() {
    ExecutionEntity none = mock(ExecutionEntity.class);
    when(none.getVariablesInternal()).thenReturn(null);
    ExecutionEntity empty = mock(ExecutionEntity.class);
    when(empty.getVariablesInternal()).thenReturn(Collections.emptyList());

    LegacyBehavior.createMissingHistoricVariables(none);
    LegacyBehavior.createMissingHistoricVariables(empty);

    verify(none).getVariablesInternal();
    verify(empty).getVariablesInternal();
  }

  @Test
  public void createMissingHistoricVariablesOnlyConsidersVariablesCreatedBefore713() {
    VariableInstanceEntity legacy = mock(VariableInstanceEntity.class);
    when(legacy.wasCreatedBefore713()).thenReturn(true);
    VariableInstanceEntity current = mock(VariableInstanceEntity.class);
    when(current.wasCreatedBefore713()).thenReturn(false);
    ExecutionEntity execution = mock(ExecutionEntity.class);
    when(execution.getVariablesInternal()).thenReturn(Arrays.asList(legacy, current));
    when(legacy.getExecution()).thenReturn(execution);

    ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
    when(configuration.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_NONE);

    try (MockedStatic<Context> context = Mockito.mockStatic(Context.class)) {
      context.when(Context::getProcessEngineConfiguration).thenReturn(configuration);

      LegacyBehavior.createMissingHistoricVariables(execution);

      // history level NONE produces no events, but the listener consulted the level for the legacy variable only
      verify(legacy).getExecution();
      verify(current, never()).getExecution();
      context.verify(Context::getProcessEngineConfiguration, Mockito.times(1));
    }
  }
}
