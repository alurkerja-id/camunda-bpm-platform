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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cmmn.entity.runtime.CaseExecutionEntity;
import org.camunda.bpm.engine.exception.NullValueException;
import org.camunda.bpm.engine.impl.event.EventType;
import org.camunda.bpm.engine.impl.core.variable.scope.VariableInstanceLifecycleListener;
import org.mockito.Mockito;
import org.junit.Test;

/** Behaviour of {@link ExecutionEntity} that works on the in-memory execution tree, without a database. */
public class ExecutionEntityBehaviorTest {

  private static ExecutionEntity execution(String id, String parentId, String processInstanceId) {
    ExecutionEntity e = new ExecutionEntity();
    e.setId(id);
    e.setParentId(parentId);
    e.setProcessInstanceId(processInstanceId);
    return e;
  }

  private static ExecutionEntity restoredTree(ExecutionEntity pi, ExecutionEntity... others) {
    List<ExecutionEntity> all = new ArrayList<>();
    all.add(pi);
    all.addAll(Arrays.asList(others));
    pi.restoreProcessInstance(all, null, null, null, null, null, null);
    return pi;
  }

  // restoring a process instance from loaded rows /////////////////////////////

  @Test
  public void shouldRestoreExecutionTreeFromFlatList() {
    ExecutionEntity pi = execution("pi", null, "pi");
    ExecutionEntity child = execution("c", "pi", "pi");
    ExecutionEntity grandChild = execution("g", "c", "pi");

    restoredTree(pi, child, grandChild);

    assertThat(pi.getExecutions()).containsExactly(child);
    assertThat(child.getExecutions()).containsExactly(grandChild);
    assertThat(grandChild.getExecutions()).isEmpty();
    assertThat(grandChild.getParent()).isSameAs(child);
    assertThat(grandChild.getProcessInstance()).isSameAs(pi);
    assertThat(pi.getProcessInstance()).isSameAs(pi);
  }

  @Test
  public void shouldRejectRestoreWithoutExecutions() {
    ExecutionEntity pi = execution("pi", null, "pi");

    assertThatThrownBy(() -> pi.restoreProcessInstance(Collections.emptyList(), null, null, null, null, null, null))
        .isInstanceOf(NullValueException.class)
        .hasMessageContaining("pi");
  }

  @Test
  public void shouldRejectRestoreOnNonProcessInstanceExecution() {
    ExecutionEntity child = execution("c", "pi", "pi");

    assertThatThrownBy(() -> child.restoreProcessInstance(Collections.singletonList(child), null, null, null, null, null, null))
        .isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldRejectRestoreWhenParentIsMissing() {
    ExecutionEntity pi = execution("pi", null, "pi");
    ExecutionEntity orphan = execution("o", "unknown", "pi");

    assertThatThrownBy(() -> restoredTree(pi, orphan))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  public void shouldAttachEventSubscriptionsToTheirExecutions() {
    ExecutionEntity pi = execution("pi", null, "pi");
    ExecutionEntity child = execution("c", "pi", "pi");
    EventSubscriptionEntity sub = new EventSubscriptionEntity(EventType.MESSAGE);
    sub.setExecutionId("c");

    pi.restoreProcessInstance(Arrays.asList(pi, child), Collections.singletonList(sub), null, null, null, null, null);

    assertThat(child.getEventSubscriptions()).containsExactly(sub);
    assertThat(pi.getEventSubscriptions()).isEmpty();
  }

  @Test
  public void shouldFailRestoreForEventSubscriptionOfUnknownExecution() {
    ExecutionEntity pi = execution("pi", null, "pi");
    EventSubscriptionEntity sub = new EventSubscriptionEntity(EventType.MESSAGE);
    sub.setExecutionId("ghost");

    assertThatThrownBy(() -> pi.restoreProcessInstance(Collections.singletonList(pi),
        Collections.singletonList(sub), null, null, null, null, null))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ghost");
  }

  @Test
  public void shouldLinkJobsToRestoredExecutions() {
    ExecutionEntity pi = execution("pi", null, "pi");
    ExecutionEntity child = execution("c", "pi", "pi");
    MessageEntity job = new MessageEntity();
    job.setExecutionId("c");

    child.jobs = new ArrayList<>();
    pi.restoreProcessInstance(Arrays.asList(pi, child), null, null, null,
        Collections.singletonList(job), null, null);

    assertThat(job.getExecution()).isSameAs(child);
  }

  // process definition / case links //////////////////////////////////////////

  @Test
  public void shouldClearProcessDefinitionIdAndKeyWhenDefinitionRemoved() {
    ExecutionEntity e = new ExecutionEntity();
    ProcessDefinitionEntity def = new ProcessDefinitionEntity();
    def.setId("def:1");
    def.setKey("def");

    e.setProcessDefinition(def);
    assertThat(e.getProcessDefinitionId()).isEqualTo("def:1");
    assertThat(e.getProcessDefinitionKey()).isEqualTo("def");

    e.setProcessDefinition(null);
    assertThat(e.getProcessDefinitionId()).isNull();
    assertThat(e.getProcessDefinitionKey()).isNull();
  }

  @Test
  public void shouldTakeCaseInstanceFromSuperCaseExecution() {
    ExecutionEntity e = new ExecutionEntity();
    CaseExecutionEntity superCase = new CaseExecutionEntity();
    superCase.setId("ce");
    CaseExecutionEntity caseInstance = new CaseExecutionEntity();
    caseInstance.setId("ci");
    superCase.setCaseInstance(caseInstance);

    e.setSuperCaseExecution(superCase);
    assertThat(e.getSuperCaseExecutionId()).isEqualTo("ce");
    assertThat(e.getCaseInstanceId()).isEqualTo("ci");

    e.setSuperCaseExecution(null);
    assertThat(e.getSuperCaseExecutionId()).isNull();
    assertThat(e.getCaseInstanceId()).isNull();
  }

  @Test
  public void shouldRememberProcessInstanceIdWhenProcessInstanceIsSet() {
    ExecutionEntity pi = execution("pi", null, "pi");
    ExecutionEntity e = new ExecutionEntity();

    e.setProcessInstance(pi);
    assertThat(e.getProcessInstanceId()).isEqualTo("pi");

    // clearing keeps the id, it only drops the cached reference
    e.setProcessInstance(null);
    assertThat(e.getProcessInstanceId()).isEqualTo("pi");
  }

  @Test
  public void shouldHaveNoBpmnModelWithoutProcessDefinition() {
    ExecutionEntity e = new ExecutionEntity();

    assertThat(e.getBpmnModelInstance()).isNull();
    assertThat(e.getBpmnModelElementInstance()).isNull();
  }

  // references, suspension, naming ///////////////////////////////////////////

  @Test
  public void shouldReportReferencedEntitiesThatAreSet() {
    ExecutionEntity e = new ExecutionEntity();
    e.setId("e");
    assertThat(e.getReferencedEntitiesIdAndClass()).isEmpty();

    e.setSuperExecutionId("se");
    e.setParentId("p");
    e.setProcessInstanceId("pi");
    e.setProcessDefinitionId("pd");

    Map<String, Class> refs = e.getReferencedEntitiesIdAndClass();
    assertThat(refs).containsEntry("se", ExecutionEntity.class)
        .containsEntry("p", ExecutionEntity.class)
        .containsEntry("pi", ExecutionEntity.class)
        .containsEntry("pd", ProcessDefinitionEntity.class);
  }

  @Test
  public void shouldDeriveSuspendedFlagFromSuspensionState() {
    ExecutionEntity e = new ExecutionEntity();
    assertThat(e.isSuspended()).isFalse();

    e.setSuspensionState(SuspensionState.SUSPENDED.getStateCode());
    assertThat(e.isSuspended()).isTrue();
    assertThat(e.getSuspensionState()).isEqualTo(SuspensionState.SUSPENDED.getStateCode());
  }

  @Test
  public void shouldDescribeProcessInstanceAndScopeExecutionsInToString() {
    ExecutionEntity pi = execution("pi", null, "pi");
    ExecutionEntity scope = execution("s", "pi", "pi");
    scope.setScope(true);
    ExecutionEntity concurrent = execution("c", "pi", "pi");
    concurrent.setConcurrent(true);
    concurrent.setScope(false);
    ExecutionEntity plain = execution("x", "pi", "pi");
    plain.setScope(false);

    assertThat(pi.toString()).isEqualTo("ProcessInstance[pi]");
    assertThat(scope.toString()).isEqualTo("ScopeExecution[s]");
    assertThat(concurrent.toString()).isEqualTo("ConcurrentExecution[c]");
    assertThat(plain.toString()).isEqualTo("Execution[x]");
  }

  @Test
  public void shouldComputeNextRevision() {
    ExecutionEntity e = new ExecutionEntity();
    e.setRevision(4);

    assertThat(e.getRevisionNext()).isEqualTo(5);
  }

  @Test
  public void shouldExposePersistentStateWithKeyFields() {
    ExecutionEntity e = execution("e", "p", "pi");
    e.setActivityId("act");
    e.setBusinessKey("bk");

    @SuppressWarnings("unchecked")
    Map<String, Object> state = (Map<String, Object>) e.getPersistentState();

    assertThat(state).containsEntry("activityId", "act")
        .containsEntry("businessKey", "bk")
        .containsEntry("parentId", "p");
  }

  // incidents, event subscriptions, observers ////////////////////////////////

  @Test
  public void shouldAddIncidentOnlyOnceAndFindItByCause() {
    ExecutionEntity e = execution("e", null, "e");
    e.incidents = new ArrayList<>();
    IncidentEntity incident = new IncidentEntity();
    incident.setCauseIncidentId("cause");
    incident.setId("i1");
    IncidentEntity noCause = new IncidentEntity();
    noCause.setId("i2");

    e.addIncident(incident);
    e.addIncident(incident);
    e.addIncident(noCause);

    assertThat(e.getIncidents()).containsExactly(incident, noCause);
    assertThat(e.getIncidentByCauseIncidentId("cause")).isSameAs(incident);
    assertThat(e.getIncidentByCauseIncidentId("other")).isNull();

    e.removeIncident(incident);
    assertThat(e.getIncidentByCauseIncidentId("cause")).isNull();
  }

  @Test
  public void shouldSelectCompensateSubscriptionsOfActivityOnly() {
    ExecutionEntity e = execution("e", null, "e");
    EventSubscriptionEntity compForA = new EventSubscriptionEntity(EventType.COMPENSATE);
    compForA.setActivityId("A");
    EventSubscriptionEntity compForB = new EventSubscriptionEntity(EventType.COMPENSATE);
    compForB.setActivityId("B");
    EventSubscriptionEntity messageForA = new EventSubscriptionEntity(EventType.MESSAGE);
    messageForA.setActivityId("A");
    e.eventSubscriptions = new ArrayList<>(Arrays.asList(compForA, compForB, messageForA));

    assertThat(e.getCompensateEventSubscriptions("A")).containsExactly(compForA);
    assertThat(e.getCompensateEventSubscriptions("none")).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldRegisterAndUnregisterObserversAndVariableListeners() {
    ExecutionEntity e = new ExecutionEntity();
    ExecutionObserver observer = Mockito.mock(ExecutionObserver.class);
    VariableInstanceLifecycleListener<VariableInstanceEntity> listener = Mockito.mock(VariableInstanceLifecycleListener.class);

    e.addExecutionObserver(observer);
    e.addVariableListener(listener);
    assertThat(e.executionObservers).containsExactly(observer);
    assertThat(e.registeredVariableListeners).containsExactly(listener);

    e.removeExecutionObserver(observer);
    e.removeVariableListener(listener);
    assertThat(e.executionObservers).isEmpty();
    assertThat(e.registeredVariableListeners).isEmpty();
  }
}
