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
package org.camunda.bpm.spring.boot.starter.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Date;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.TransitionImpl;
import org.camunda.bpm.spring.boot.starter.property.EventingProperty;
import org.junit.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Covers the event snapshots and the parse listener that attaches the publishing listeners:
 * which listeners are added depends on the eventing flags, and {@code skippable} decides between
 * regular and built-in listeners.
 */
public class EventingUnitTest {

  protected EventingProperty property(boolean task, boolean execution, boolean skippable) {
    var property = new EventingProperty();
    property.setTask(task);
    property.setExecution(execution);
    property.setSkippable(skippable);
    return property;
  }

  protected ActivityImpl activity() {
    return new ProcessDefinitionEntity().createActivity("a1");
  }

  @Test
  public void shouldSnapshotTaskFieldsAtEventTime() {
    var date = new Date(1000);
    var task = mock(DelegateTask.class);
    when(task.getId()).thenReturn("t1");
    when(task.getName()).thenReturn("Review");
    when(task.getAssignee()).thenReturn("ana");
    when(task.getPriority()).thenReturn(60);
    when(task.getCreateTime()).thenReturn(date);
    when(task.getEventName()).thenReturn("create");
    when(task.getTenantId()).thenReturn("tn");

    var event = new TaskEvent(task);
    when(task.getAssignee()).thenReturn("changed");

    assertThat(event.getId()).isEqualTo("t1");
    assertThat(event.getName()).isEqualTo("Review");
    assertThat(event.getAssignee()).isEqualTo("ana");
    assertThat(event.getPriority()).isEqualTo(60);
    assertThat(event.getCreateTime()).isEqualTo(date);
    assertThat(event.getEventName()).isEqualTo("create");
    assertThat(event.getTenantId()).isEqualTo("tn");
    assertThat(event.getOwner()).isNull();
    assertThat(event.toString()).startsWith("TaskEvent[id=t1").contains("assignee=ana");
  }

  @Test
  public void shouldExposeEveryTaskEventGetter() throws Exception {
    var event = new TaskEvent(mock(DelegateTask.class));
    int called = 0;
    for (Method m : TaskEvent.class.getDeclaredMethods()) {
      if (m.getName().startsWith("get") && m.getParameterCount() == 0) {
        m.invoke(event);
        called++;
      }
    }
    assertThat(called).isGreaterThan(15);
  }

  @Test
  public void shouldSnapshotExecutionFields() throws Exception {
    var execution = mock(DelegateExecution.class);
    when(execution.getId()).thenReturn("e1");
    when(execution.getEventName()).thenReturn("start");
    when(execution.getProcessInstanceId()).thenReturn("pi1");

    var event = new ExecutionEvent(execution);
    int called = 0;
    for (Method m : ExecutionEvent.class.getDeclaredMethods()) {
      if (m.getName().startsWith("get") && m.getParameterCount() == 0) {
        m.invoke(event);
        called++;
      }
    }

    assertThat(event.getId()).isEqualTo("e1");
    assertThat(event.getEventName()).isEqualTo("start");
    assertThat(event.getProcessInstanceId()).isEqualTo("pi1");
    assertThat(called).isGreaterThan(5);
    assertThat(event.toString()).contains("e1");
  }

  @Test
  public void shouldAddRegularListenersWhenSkippable() {
    var activity = activity();

    new PublishDelegateParseListener(mock(ApplicationEventPublisher.class), property(true, true, true))
        .parseServiceTask(null, null, activity);

    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_START)).hasSize(1);
    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_END)).hasSize(1);
    assertThat(activity.getBuiltInListeners(ExecutionListener.EVENTNAME_START)).isEmpty();
  }

  @Test
  public void shouldAddBuiltInListenersWhenNotSkippable() {
    var activity = activity();

    new PublishDelegateParseListener(mock(ApplicationEventPublisher.class), property(true, true, false))
        .parseStartEvent(null, null, activity);

    assertThat(activity.getBuiltInListeners(ExecutionListener.EVENTNAME_START)).hasSize(1);
  }

  @Test
  public void shouldAddNoListenersWhenExecutionEventingDisabled() {
    var activity = activity();
    var listener = new PublishDelegateParseListener(mock(ApplicationEventPublisher.class), property(true, false, true));

    listener.parseTask(null, null, activity);
    listener.parseEndEvent(null, null, activity);
    listener.parseSequenceFlow(null, null, activity.createOutgoingTransition("tr"));
    listener.parseProcess(null, new ProcessDefinitionEntity());

    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_START)).isEmpty();
    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_END)).isEmpty();
  }

  @Test
  public void shouldListenOnTakeOfSequenceFlows() {
    var activity = activity();
    TransitionImpl transition = activity.createOutgoingTransition("tr");

    new PublishDelegateParseListener(mock(ApplicationEventPublisher.class), property(false, true, true))
        .parseSequenceFlow(null, null, transition);

    assertThat(transition.getListeners(ExecutionListener.EVENTNAME_TAKE)).hasSize(1);
  }

  @Test
  public void shouldAddProcessLevelListenersAndPublishEvents() {
    var publisher = mock(ApplicationEventPublisher.class);
    var definition = new ProcessDefinitionEntity();
    new PublishDelegateParseListener(publisher, property(false, true, true)).parseProcess(null, definition);

    assertThat(definition.getListeners(ExecutionListener.EVENTNAME_START)).hasSize(1);
    var listener = (ExecutionListener) definition.getListeners(ExecutionListener.EVENTNAME_START).get(0);
    var execution = mock(DelegateExecution.class);

    try {
      listener.notify(execution);
    } catch (Exception e) {
      throw new AssertionError(e);
    }

    verify(publisher).publishEvent(execution);
    verify(publisher).publishEvent(any(ExecutionEvent.class));
  }

  @Test
  public void shouldPublishTaskEventsFromTaskListener() throws Exception {
    var publisher = mock(ApplicationEventPublisher.class);
    var parse = new PublishDelegateParseListener(publisher, property(true, false, true));
    var field = PublishDelegateParseListener.class.getDeclaredField("taskListener");
    field.setAccessible(true);
    var taskListener = (TaskListener) field.get(parse);
    var task = mock(DelegateTask.class);

    taskListener.notify(task);

    verify(publisher).publishEvent(task);
    verify(publisher, times(2)).publishEvent(any(Object.class));
  }

  @Test
  public void shouldHaveNoTaskListenerWhenTaskEventingDisabled() throws Exception {
    var parse = new PublishDelegateParseListener(mock(ApplicationEventPublisher.class), property(false, true, true));
    var field = PublishDelegateParseListener.class.getDeclaredField("taskListener");
    field.setAccessible(true);

    assertThat(field.get(parse)).isNull();
  }

  @Test
  public void shouldIgnoreCallbacksThatAreHandledElsewhere() {
    var activity = activity();
    var listener = new PublishDelegateParseListener(mock(ApplicationEventPublisher.class), property(true, true, true));

    listener.parseBoundaryErrorEventDefinition(null, true, activity, activity);
    listener.parseBoundaryMessageEventDefinition(null, true, activity);
    listener.parseBoundarySignalEventDefinition(null, true, activity);
    listener.parseBoundaryTimerEventDefinition(null, true, activity);
    listener.parseCompensateEventDefinition(null, activity);
    listener.parseIntermediateMessageCatchEventDefinition(null, activity);
    listener.parseIntermediateSignalCatchEventDefinition(null, activity);
    listener.parseIntermediateTimerEventDefinition(null, activity);
    listener.parseMultiInstanceLoopCharacteristics(null, null, activity);

    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_START)).isEmpty();
  }

  @Test
  public void shouldAddListenersForEveryActivityKind() {
    var listener = new PublishDelegateParseListener(mock(ApplicationEventPublisher.class), property(false, true, true));
    var activity = activity();

    listener.parseBoundaryEvent(null, null, activity);
    listener.parseBusinessRuleTask(null, null, activity);
    listener.parseCallActivity(null, null, activity);
    listener.parseEventBasedGateway(null, null, activity);
    listener.parseExclusiveGateway(null, null, activity);
    listener.parseInclusiveGateway(null, null, activity);
    listener.parseIntermediateCatchEvent(null, null, activity);
    listener.parseIntermediateThrowEvent(null, null, activity);
    listener.parseManualTask(null, null, activity);
    listener.parseParallelGateway(null, null, activity);
    listener.parseReceiveTask(null, null, activity);
    listener.parseScriptTask(null, null, activity);
    listener.parseSendTask(null, null, activity);
    listener.parseSubProcess(null, null, activity);
    listener.parseTransaction(null, null, activity);

    assertThat(activity.getListeners(ExecutionListener.EVENTNAME_START)).hasSize(15);
  }
}
