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
package org.camunda.bpm.engine.test.bpmn.pvm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * Locks the behavior of PvmAtomicOperationTransitionDestroyScope and
 * PvmAtomicOperationProcessEnd. Must stay green on master and on the
 * java:S3776 refactoring branch.
 */
public class PvmScopeTransitionBehaviorTest {

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  @Before
  public void deploy() {
    // a user task with a boundary event is a scope activity with two outgoing flows
    BpmnModelInstance scopeFork = Bpmn.createExecutableProcess("scopeFork")
      .startEvent()
      .userTask("scope")
      .boundaryEvent("boundary").message("never").endEvent("boundaryEnd")
      .moveToActivity("scope")
      .userTask("a")
      .moveToActivity("scope")
      .userTask("b")
      .done();
    testRule.deploy(scopeFork);

    BpmnModelInstance concurrentScopeFork = Bpmn.createExecutableProcess("concurrentScopeFork")
      .startEvent()
      .parallelGateway("fork")
      .userTask("scope1")
      .boundaryEvent("boundary1").message("never1").endEvent("boundaryEnd1")
      .moveToActivity("scope1")
      .userTask("a1")
      .moveToActivity("scope1")
      .userTask("b1")
      .moveToNode("fork")
      .userTask("other")
      .done();
    testRule.deploy(concurrentScopeFork);
  }

  @Test
  public void scopeActivityWithTwoOutgoingFlowsStartsBothBranches() {
    ProcessInstance pi = engineRule.getRuntimeService().startProcessInstanceByKey("scopeFork");
    complete(pi, "scope");

    assertThat(activeTaskKeys(pi)).containsExactlyInAnyOrder("a", "b");
    ActivityInstance tree = engineRule.getRuntimeService().getActivityInstance(pi.getId());
    assertThat(tree.getChildActivityInstances()).hasSize(2);

    completeAll(pi);
    testRule.assertProcessEnded(pi.getId());
  }

  @Test
  public void concurrentScopeActivityWithTwoOutgoingFlowsStartsBothBranches() {
    ProcessInstance pi = engineRule.getRuntimeService().startProcessInstanceByKey("concurrentScopeFork");
    assertThat(activeTaskKeys(pi)).containsExactlyInAnyOrder("scope1", "other");

    complete(pi, "scope1");

    assertThat(activeTaskKeys(pi)).containsExactlyInAnyOrder("a1", "b1", "other");
    ActivityInstance tree = engineRule.getRuntimeService().getActivityInstance(pi.getId());
    assertThat(tree.getChildActivityInstances()).hasSize(3);

    completeAll(pi);
    testRule.assertProcessEnded(pi.getId());
  }

  protected void complete(ProcessInstance pi, String taskKey) {
    Task task = engineRule.getTaskService().createTaskQuery()
      .processInstanceId(pi.getId()).taskDefinitionKey(taskKey).singleResult();
    engineRule.getTaskService().complete(task.getId());
  }

  protected void completeAll(ProcessInstance pi) {
    List<Task> tasks = engineRule.getTaskService().createTaskQuery().processInstanceId(pi.getId()).list();
    for (Task task : tasks) {
      engineRule.getTaskService().complete(task.getId());
    }
  }

  protected List<String> activeTaskKeys(ProcessInstance pi) {
    return engineRule.getTaskService().createTaskQuery().processInstanceId(pi.getId()).list()
      .stream().map(Task::getTaskDefinitionKey).collect(Collectors.toList());
  }
}
