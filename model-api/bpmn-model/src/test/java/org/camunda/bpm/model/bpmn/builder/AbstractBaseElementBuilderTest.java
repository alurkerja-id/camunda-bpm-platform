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
package org.camunda.bpm.model.bpmn.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelException;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Association;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.CompensateEventDefinition;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.Documentation;
import org.camunda.bpm.model.bpmn.instance.Error;
import org.camunda.bpm.model.bpmn.instance.ErrorEventDefinition;
import org.camunda.bpm.model.bpmn.instance.Escalation;
import org.camunda.bpm.model.bpmn.instance.EscalationEventDefinition;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.Message;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.camunda.bpm.model.bpmn.instance.ParallelGateway;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.Property;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.Signal;
import org.camunda.bpm.model.bpmn.instance.SignalEventDefinition;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import org.junit.Test;

/** Exercises the shared helpers of {@link AbstractBaseElementBuilder} through concrete builders. */
public class AbstractBaseElementBuilderTest {

  protected UserTaskBuilder taskBuilder(BpmnModelInstance[] holder) {
    UserTaskBuilder builder = Bpmn.createExecutableProcess("p").startEvent("start").userTask("task");
    holder[0] = builder.done();
    return builder;
  }

  @Test
  public void shouldSetIdDocumentationAndExtensionElement() {
    BpmnModelInstance[] model = new BpmnModelInstance[1];
    UserTaskBuilder builder = taskBuilder(model);
    ExtensionElements existing = null;

    builder.id("renamed").documentation("docs");
    Documentation extension = model[0].newInstance(Documentation.class);
    builder.addExtensionElement(extension);
    builder.addExtensionElement(model[0].newInstance(Documentation.class));

    UserTask task = builder.getElement();
    assertThat(task.getId()).isEqualTo("renamed");
    assertThat(task.getChildElementsByType(Documentation.class).iterator().next().getTextContent()).isEqualTo("docs");
    Collection<ExtensionElements> extensions = task.getChildElementsByType(ExtensionElements.class);
    assertThat(extensions).hasSize(1);
    assertThat(extensions.iterator().next().getElements()).hasSize(2);
    assertThat(existing).isNull();
  }

  @Test
  public void shouldCreateInstancesChildrenAndSiblings() {
    BpmnModelInstance[] model = new BpmnModelInstance[1];
    UserTaskBuilder builder = taskBuilder(model);

    SequenceFlow named = builder.createInstance(SequenceFlow.class, "flow");
    assertThat(named.getId()).isEqualTo("flow");
    assertThat(named.getName()).isEqualTo("flow");
    BaseElement unnamed = builder.createInstance(Property.class, null);
    assertThat(unnamed.getId()).startsWith("property_");

    Definitions definitions = model[0].getDefinitions();
    Message message = builder.createChild(definitions, Message.class, "msg");
    assertThat(message.getId()).isEqualTo("msg");
    assertThat(builder.createChild(Documentation.class)).isNotNull();
    assertThat(builder.createChild(Property.class, "docId").getId()).isEqualTo("docId");

    assertThat(builder.createSibling(Documentation.class)).isNotNull();
    assertThat(builder.createSibling(Property.class, "sibling").getParentElement())
        .isEqualTo(builder.getElement().getParentElement());
  }

  @Test
  public void shouldFailOnAmbiguousSingleChild() {
    BpmnModelInstance[] model = new BpmnModelInstance[1];
    UserTaskBuilder builder = taskBuilder(model);
    builder.createChild(Documentation.class);
    builder.createChild(Documentation.class);

    assertThatThrownBy(() -> builder.getCreateSingleChild(Documentation.class))
        .isInstanceOf(BpmnModelException.class)
        .hasMessageContaining("more than one child element of type");

    ExtensionElements first = builder.getCreateSingleChild(ExtensionElements.class);
    assertThat(builder.getCreateSingleChild(ExtensionElements.class)).isEqualTo(first);
    assertThat(builder.getCreateSingleExtensionElement(Documentation.class)).isNotNull();
  }

  @Test
  public void shouldReuseMessagesSignalsErrorsAndEscalationsByKey() {
    BpmnModelInstance[] model = new BpmnModelInstance[1];
    UserTaskBuilder builder = taskBuilder(model);

    Message message = builder.findMessageForName("m");
    assertThat(builder.findMessageForName("m")).isEqualTo(message);
    MessageEventDefinition messageDefinition = builder.createMessageEventDefinition("m");
    assertThat(messageDefinition.getMessage()).isEqualTo(message);
    assertThat(builder.createEmptyMessageEventDefinition().getMessage()).isNull();

    Signal signal = builder.findSignalForName("s");
    assertThat(builder.findSignalForName("s")).isEqualTo(signal);
    SignalEventDefinition signalDefinition = builder.createSignalEventDefinition("s");
    assertThat(signalDefinition.getSignal()).isEqualTo(signal);

    assertThat(builder.findErrorDefinitionForCode("E1")).isNull();
    Error error = builder.findErrorForNameAndCode("E1", "message");
    assertThat(error.getCamundaErrorMessage()).isEqualTo("message");
    assertThat(builder.findErrorForNameAndCode("E1")).isEqualTo(error);
    assertThat(builder.findErrorForNameAndCode("E2", "").getCamundaErrorMessage()).isNull();
    assertThat(builder.findErrorForNameAndCode("E3").getErrorCode()).isEqualTo("E3");
    ErrorEventDefinition errorDefinition = builder.createErrorEventDefinition("E1");
    assertThat(errorDefinition.getError()).isEqualTo(error);
    builder.createChild((StartEvent) model[0].getModelElementById("start"), ErrorEventDefinition.class).setError(error);
    assertThat(builder.findErrorDefinitionForCode("E1")).isNotNull();
    assertThat(builder.findErrorDefinitionForCode("other")).isNull();
    assertThat(builder.createEmptyErrorEventDefinition().getError()).isNull();

    Escalation escalation = builder.findEscalationForCode("C1");
    assertThat(builder.findEscalationForCode("C1")).isEqualTo(escalation);
    EscalationEventDefinition escalationDefinition = builder.createEscalationEventDefinition("C1");
    assertThat(escalationDefinition.getEscalation()).isEqualTo(escalation);

    CompensateEventDefinition compensate = builder.createCompensateEventDefinition();
    assertThat(compensate).isNotNull();
  }

  @Test
  public void shouldSizeShapesPerNodeType() {
    BpmnModelInstance model = Bpmn.createExecutableProcess("p")
        .startEvent("start")
        .userTask("task")
        .exclusiveGateway("xor")
        .parallelGateway("and")
        .subProcess("sub").embeddedSubProcess().startEvent("inner").endEvent("innerEnd").subProcessDone()
        .endEvent("end")
        .done();

    assertShape(model, "start", 36, 36);
    assertShape(model, "task", 100, 80);
    assertShape(model, "xor", 50, 50);
    assertShape(model, "and", 50, 50);
    BpmnShape xor = shapeOf(model, "xor");
    assertThat(xor.isMarkerVisible()).isTrue();
    assertThat(shapeOf(model, "and").isMarkerVisible()).isFalse();
    BpmnShape sub = shapeOf(model, "sub");
    assertThat(sub.isExpanded()).isTrue();
    assertThat((double) sub.getBounds().getWidth()).isGreaterThanOrEqualTo(350);
    assertThat((Object) model.getModelElementById("xor")).isInstanceOf(ExclusiveGateway.class);
    assertThat((Object) model.getModelElementById("and")).isInstanceOf(ParallelGateway.class);
    assertThat((Object) model.getModelElementById("sub")).isInstanceOf(SubProcess.class);
  }

  @Test
  public void shouldPlaceFollowingShapesRightOfSourceAndBelowLastBranch() {
    BpmnModelInstance model = Bpmn.createExecutableProcess("p")
        .startEvent("start")
        .parallelGateway("fork")
        .userTask("a")
        .endEvent("endA")
        .moveToNode("fork")
        .userTask("b")
        .endEvent("endB")
        .done();

    BpmnShape start = shapeOf(model, "start");
    BpmnShape fork = shapeOf(model, "fork");
    BpmnShape a = shapeOf(model, "a");
    BpmnShape b = shapeOf(model, "b");
    assertThat((double) fork.getBounds().getX()).isEqualTo((double) start.getBounds().getX() + 36 + 50);
    assertThat((double) a.getBounds().getX()).isEqualTo((double) fork.getBounds().getX() + 50 + 50);
    assertThat((double) b.getBounds().getY()).isGreaterThanOrEqualTo((double) a.getBounds().getY() + 80 + 50);
  }

  @Test
  public void shouldCreateEdgesWithWaypoints() {
    BpmnModelInstance model = Bpmn.createExecutableProcess("p")
        .startEvent("start")
        .parallelGateway("fork")
        .userTask("a")
        .moveToNode("fork")
        .userTask("b")
        .done();

    Collection<BpmnEdge> edges = model.getModelElementsByType(BpmnEdge.class);
    assertThat(edges).isNotEmpty();
    boolean single = false;
    boolean branching = false;
    for (BpmnEdge edge : edges) {
      int points = edge.getWaypoints().size();
      single |= points == 2;
      branching |= points == 3;
      for (Waypoint waypoint : edge.getWaypoints()) {
        assertThat(waypoint).isNotNull();
      }
    }
    assertThat(single).isTrue();
    assertThat(branching).isTrue();
  }

  @Test
  public void shouldCreateEdgeForAssociationAndRejectOtherTypes() {
    BpmnModelInstance[] model = new BpmnModelInstance[1];
    UserTaskBuilder builder = taskBuilder(model);
    StartEvent start = model[0].getModelElementById("start");

    Association association = builder.createChild(model[0].getModelElementById("p"), Association.class);
    association.setSource(start);
    association.setTarget(builder.getElement());

    BpmnEdge edge = builder.createEdge(association);
    assertThat(edge).isNotNull();
    assertThat(builder.findBpmnEdge(association)).isEqualTo(edge);
    assertThat(builder.findBpmnEdge(start)).isNull();

    assertThatThrownBy(() -> builder.createEdge(start)).isInstanceOf(RuntimeException.class)
        .hasMessageContaining("not supported");
  }

  @Test
  public void shouldCreateDeprecatedEdgeForSequenceFlow() {
    BpmnModelInstance model = Bpmn.createExecutableProcess("p").startEvent("start").userTask("task").done();
    UserTaskBuilder builder = ((UserTask) model.getModelElementById("task")).builder();
    SequenceFlow flow = model.getModelElementsByType(SequenceFlow.class).iterator().next();

    @SuppressWarnings("deprecation")
    BpmnEdge edge = builder.createBpmnEdge(flow);

    assertThat(edge.getBpmnElement()).isEqualTo(flow);
  }

  @Test
  public void shouldReturnNullDiagramElementsWithoutPlane() {
    BpmnModelInstance model = Bpmn.createEmptyModel();
    Definitions definitions = model.newInstance(Definitions.class);
    definitions.setTargetNamespace("http://example.com");
    model.setDefinitions(definitions);
    Process process = model.newInstance(Process.class);
    process.setId("p");
    definitions.addChildElement(process);

    ProcessBuilder builder = process.builder();
    StartEvent start = model.newInstance(StartEvent.class);
    process.addChildElement(start);

    assertThat(builder.findBpmnShape(start)).isNull();
    assertThatThrownBy(() -> builder.createBpmnShape(start)).isInstanceOf(java.util.NoSuchElementException.class);
  }

  protected BpmnShape shapeOf(BpmnModelInstance model, String id) {
    for (BpmnShape shape : model.getModelElementsByType(BpmnShape.class)) {
      if (id.equals(shape.getBpmnElement().getId())) {
        return shape;
      }
    }
    throw new AssertionError("no shape for " + id);
  }

  protected void assertShape(BpmnModelInstance model, String id, double width, double height) {
    BpmnShape shape = shapeOf(model, id);
    assertThat((double) shape.getBounds().getWidth()).isEqualTo(width);
    assertThat((double) shape.getBounds().getHeight()).isEqualTo(height);
  }
}
