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

import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.pvm.PvmException;
import org.camunda.bpm.engine.impl.pvm.PvmExecution;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ActivityStartBehavior;
import org.camunda.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.junit.Before;
import org.junit.Test;

/** Behaviour of {@link PvmExecutionImpl} that works on a plain in-memory execution tree. */
public class PvmExecutionImplBehaviorTest {

  private ProcessDefinitionImpl definition;
  private ActivityImpl start;
  private ActivityImpl task;

  @Before
  public void buildDefinition() {
    definition = new ProcessDefinitionImpl("proc:1");
    start = definition.createActivity("start");
    task = definition.createActivity("task");
    start.createOutgoingTransition("flow").setDestination(task);
  }

  private static ExecutionEntity execution(String id, String parentId, String processInstanceId) {
    ExecutionEntity e = new ExecutionEntity();
    e.setId(id);
    e.setParentId(parentId);
    e.setProcessInstanceId(processInstanceId);
    return e;
  }

  private static ExecutionEntity tree(ExecutionEntity pi, ExecutionEntity... others) {
    List<ExecutionEntity> all = new java.util.ArrayList<>();
    all.add(pi);
    all.addAll(Arrays.asList(others));
    pi.restoreProcessInstance(all, null, null, null, null, null, null);
    return pi;
  }

  // lifecycle guards /////////////////////////////////////////////////////////

  @Test
  public void shouldNotPropagateEndOfExecutionThatHasNotEnded() {
    ExecutionEntity e = execution("e", null, "e");

    assertThatThrownBy(e::propagateEnd)
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("must have ended");
  }

  @Test
  public void shouldIgnoreEndPropagationOfEndedChildExecution() {
    ExecutionEntity child = execution("c", "pi", "pi");
    child.setEnded(true);

    child.propagateEnd();

    assertThat(child.isEnded()).isTrue();
  }

  @Test
  public void shouldRefuseConcurrentExecutionUnderNonScopeExecution() {
    ExecutionEntity e = execution("e", "pi", "pi");
    e.setScope(false);

    assertThatThrownBy(e::createConcurrentExecution)
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Cannot create concurrent execution");
  }

  @Test
  public void shouldRefuseToTakeWithoutTransition() {
    ExecutionEntity e = execution("e", null, "e");

    assertThatThrownBy(e::take)
        .isInstanceOf(PvmException.class)
        .hasMessageContaining("no transition to take");
  }

  @Test
  public void shouldRefuseExecuteActivityForActivityInNormalFlow() {
    ExecutionEntity e = execution("e", null, "e");

    assertThatThrownBy(() -> e.executeActivity(task))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("normal flow");
  }

  @Test
  public void shouldRefuseNonDefaultStartBehaviorOnNonScopeExecution() {
    ExecutionEntity e = execution("e", "pi", "pi");
    e.setScope(false);
    ActivityImpl standalone = definition.createActivity("standalone");
    standalone.setActivityStartBehavior(ActivityStartBehavior.INTERRUPT_FLOW_SCOPE);

    assertThatThrownBy(() -> e.executeActivity(standalone))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("non-scope execution");
  }

  // scope mapping guards /////////////////////////////////////////////////////

  @Test
  public void shouldRequireScopeExecutionForActivityExecutionMapping() {
    ExecutionEntity e = execution("e", "pi", "pi");
    e.setScope(false);

    assertThatThrownBy(() -> e.createActivityExecutionMapping(definition))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Execution must be a scope execution");
  }

  @Test
  public void shouldRequireScopeCurrentScopeForActivityExecutionMapping() {
    ExecutionEntity e = execution("e", null, "e");
    e.setScope(true);
    task.setScope(false);

    assertThatThrownBy(() -> e.createActivityExecutionMapping(task))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Current scope must be a scope");
  }

  @Test
  public void shouldRequireScopeTargetWhenFindingExecutionForScope() {
    ExecutionEntity e = execution("e", null, "e");
    task.setScope(false);

    assertThatThrownBy(() -> e.findExecutionForScope(definition, task))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Target scope must be a scope");
  }

  // tree navigation //////////////////////////////////////////////////////////

  @Test
  public void shouldFindExecutionsParkedInActivityAcrossTheTree() {
    ExecutionEntity pi = execution("pi", null, "pi");
    ExecutionEntity a = execution("a", "pi", "pi");
    ExecutionEntity b = execution("b", "pi", "pi");
    ExecutionEntity nested = execution("n", "a", "pi");
    a.setActivity(task);
    b.setActivity(start);
    nested.setActivity(task);
    tree(pi, a, b, nested);

    List<PvmExecution> found = pi.findExecutions("task");

    assertThat(found).containsExactlyInAnyOrder(a, nested);
    assertThat(pi.findExecutions("unknown")).isEmpty();
  }

  @Test
  public void shouldExposeParentIdBusinessKeyAndCurrentActivity() {
    ExecutionEntity pi = execution("pi", null, "pi");
    pi.setBusinessKey("bk-1");
    ExecutionEntity child = execution("c", "pi", "pi");
    tree(pi, child);

    assertThat(child.getParentId()).isEqualTo("pi");
    assertThat(pi.getParentId()).isNull();
    assertThat(child.getProcessBusinessKey()).isEqualTo("bk-1");

    assertThat(child.getCurrentActivityName()).isNull();
    task.setProperty("name", "Review");
    child.setActivity(task);
    assertThat(child.getCurrentActivityId()).isEqualTo("task");
    assertThat(child.getCurrentActivityName()).isEqualTo("Review");
  }

  @Test
  public void shouldDetectReplacementByParent() {
    ExecutionEntity pi = execution("pi", null, "pi");
    ExecutionEntity child = execution("c", "pi", "pi");
    tree(pi, child);

    assertThat(child.isReplacedByParent()).isFalse();

    child.replacedBy = pi;
    assertThat(child.isReplacedByParent()).isTrue();

    child.replacedBy = execution("other", null, "other");
    assertThat(child.isReplacedByParent()).isFalse();
  }

  @Test
  public void shouldNotPruneWithoutExactlyOneConcurrentChild() {
    ExecutionEntity pi = execution("pi", null, "pi");
    assertThat(tree(pi).tryPruneLastConcurrentChild()).isFalse();

    ExecutionEntity pi2 = execution("pi2", null, "pi2");
    ExecutionEntity notConcurrent = execution("c", "pi2", "pi2");
    notConcurrent.setConcurrent(false);
    assertThat(tree(pi2, notConcurrent).tryPruneLastConcurrentChild()).isFalse();
  }

  @Test
  public void shouldReportLeafScopeActivityOnlyForActiveScopeActivityWithInstanceId() {
    ExecutionEntity e = execution("e", null, "e");
    task.setScope(true);
    assertThat(e.isExecutingScopeLeafActivity()).isFalse();

    e.setActivity(task);
    e.setActive(true);
    assertThat(e.isExecutingScopeLeafActivity()).isFalse(); // no activity instance id yet

    e.setActivityInstanceId("ai-1");
    assertThat(e.isExecutingScopeLeafActivity()).isTrue();

    e.setActive(false);
    assertThat(e.isExecutingScopeLeafActivity()).isFalse();
  }
}
