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
package org.camunda.bpm.engine.cdi.impl.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.camunda.bpm.engine.impl.pvm.process.TransitionImpl;
import org.camunda.bpm.engine.impl.task.TaskDefinition;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.junit.Before;
import org.junit.Test;

public class CdiEventSupportBpmnParseListenerUnitTest {

  private final CdiEventSupportBpmnParseListener listener = new CdiEventSupportBpmnParseListener();
  private final Element element = mock(Element.class);
  private ProcessDefinitionImpl processDefinition;
  private ActivityImpl activity;

  @Before
  public void setUp() {
    processDefinition = new ProcessDefinitionImpl("pd:1");
    activity = new ActivityImpl("a1", processDefinition);
  }

  private void assertStartAndEndListenerAdded() {
    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_START)).hasSize(1);
    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_END)).hasSize(1);
    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_START).get(0)).isInstanceOf(CdiEventListener.class);
    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_END).get(0)).isInstanceOf(CdiEventListener.class);
  }

  private void assertNoListenerAdded() {
    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_START)).isEmpty();
    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_END)).isEmpty();
  }

  @Test
  public void activityElementsGetStartAndEndListeners() {
    listener.parseStartEvent(element, processDefinition, activity);
    assertStartAndEndListenerAdded();
  }

  @Test
  public void gatewaysGetListeners() {
    for (int i = 0; i < 4; i++) {
      activity = new ActivityImpl("g" + i, processDefinition);
      switch (i) {
        case 0: listener.parseExclusiveGateway(element, processDefinition, activity); break;
        case 1: listener.parseInclusiveGateway(element, processDefinition, activity); break;
        case 2: listener.parseParallelGateway(element, processDefinition, activity); break;
        default: listener.parseEventBasedGateway(element, processDefinition, activity); break;
      }
      assertStartAndEndListenerAdded();
    }
  }

  @Test
  public void tasksGetListeners() {
    Runnable[] parsers = new Runnable[] {
      () -> listener.parseScriptTask(element, processDefinition, activity),
      () -> listener.parseServiceTask(element, processDefinition, activity),
      () -> listener.parseBusinessRuleTask(element, processDefinition, activity),
      () -> listener.parseTask(element, processDefinition, activity),
      () -> listener.parseManualTask(element, processDefinition, activity),
      () -> listener.parseSendTask(element, processDefinition, activity),
      () -> listener.parseReceiveTask(element, processDefinition, activity),
    };
    for (Runnable parser : parsers) {
      activity = new ActivityImpl("t", processDefinition);
      parser.run();
      assertStartAndEndListenerAdded();
    }
  }

  @Test
  public void structuralElementsGetListeners() {
    Runnable[] parsers = new Runnable[] {
      () -> listener.parseEndEvent(element, processDefinition, activity),
      () -> listener.parseSubProcess(element, processDefinition, activity),
      () -> listener.parseCallActivity(element, processDefinition, activity),
      () -> listener.parseTransaction(element, processDefinition, activity),
      () -> listener.parseIntermediateThrowEvent(element, processDefinition, activity),
      () -> listener.parseMultiInstanceLoopCharacteristics(element, element, activity),
    };
    for (Runnable parser : parsers) {
      activity = new ActivityImpl("s", processDefinition);
      parser.run();
      assertStartAndEndListenerAdded();
    }
  }

  @Test
  public void eventDefinitionsGetListeners() {
    Runnable[] parsers = new Runnable[] {
      () -> listener.parseBoundaryTimerEventDefinition(element, true, activity),
      () -> listener.parseBoundaryErrorEventDefinition(element, true, activity, activity),
      () -> listener.parseIntermediateTimerEventDefinition(element, activity),
      () -> listener.parseIntermediateSignalCatchEventDefinition(element, activity),
      () -> listener.parseBoundarySignalEventDefinition(element, true, activity),
    };
    for (Runnable parser : parsers) {
      activity = new ActivityImpl("e", processDefinition);
      parser.run();
      assertThat(activity.getListeners(ExecutionListener.EVENTNAME_START)).isNotEmpty();
      assertThat(activity.getListeners(ExecutionListener.EVENTNAME_END)).isNotEmpty();
    }
  }

  @Test
  public void userTaskGetsActivityAndAllTaskListeners() {
    TaskDefinition taskDefinition = new TaskDefinition(null);
    UserTaskActivityBehavior behavior = new UserTaskActivityBehavior(null, taskDefinition);
    activity.setActivityBehavior(behavior);

    listener.parseUserTask(element, processDefinition, activity);

    assertStartAndEndListenerAdded();
    for (String event : new String[] {
        TaskListener.EVENTNAME_CREATE, TaskListener.EVENTNAME_ASSIGNMENT, TaskListener.EVENTNAME_COMPLETE,
        TaskListener.EVENTNAME_UPDATE, TaskListener.EVENTNAME_DELETE}) {
      assertThat(taskDefinition.getTaskListeners().get(event)).as(event).hasSize(1);
      assertThat(taskDefinition.getTaskListeners().get(event).get(0)).isInstanceOf(CdiEventListener.class);
    }
  }

  @Test
  public void sequenceFlowGetsTakeListener() {
    ActivityImpl target = new ActivityImpl("target", processDefinition);
    TransitionImpl transition = activity.createOutgoingTransition("flow");
    transition.setDestination(target);

    listener.parseSequenceFlow(element, processDefinition, transition);

    assertThat(transition.getListeners(ExecutionListener.EVENTNAME_TAKE)).hasSize(1);
    assertThat(transition.getListeners(ExecutionListener.EVENTNAME_TAKE).get(0)).isInstanceOf(CdiEventListener.class);
  }

  @Test
  public void noOpHooksLeaveActivityUntouched() {
    listener.parseProcess(element, null);
    listener.parseRootElement(element, java.util.Collections.emptyList());
    listener.parseProperty(element, null, activity);
    listener.parseCompensateEventDefinition(element, activity);
    listener.parseIntermediateCatchEvent(element, processDefinition, activity);
    listener.parseBoundaryEvent(element, processDefinition, activity);
    listener.parseIntermediateMessageCatchEventDefinition(element, activity);
    listener.parseBoundaryMessageEventDefinition(element, true, activity);
    listener.parseBoundaryEscalationEventDefinition(element, true, activity);
    listener.parseBoundaryConditionalEventDefinition(element, true, activity);
    listener.parseIntermediateConditionalEventDefinition(element, activity);
    listener.parseConditionalStartEventForEventSubprocess(element, activity, true);
    listener.parseIoMapping(element, activity, null);

    assertNoListenerAdded();
  }
}
