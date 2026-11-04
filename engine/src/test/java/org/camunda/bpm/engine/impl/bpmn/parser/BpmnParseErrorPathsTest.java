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
package org.camunda.bpm.engine.impl.bpmn.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.SAXParserFactory;

import org.camunda.bpm.engine.Problem;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.persistence.entity.DeploymentEntity;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Drives {@link BpmnParse} directly (with XSD validation turned off, so that
 * structurally invalid models reach the semantic checks of the parser) and
 * asserts the diagnostics the parser reports for each kind of broken model.
 */
@RunWith(Parameterized.class)
public class BpmnParseErrorPathsTest {

  @Rule
  public ProcessEngineRule engineRule = new ProvidedProcessEngineRule();

  @Parameter(0)
  public String name;

  @Parameter(1)
  public String xml;

  @Parameter(2)
  public String expectedMessage;

  @Parameters(name = "{0}")
  public static Collection<Object[]> cases() {
    List<Object[]> cases = new ArrayList<>();

    // definitions / imports / errors
    cases.add(c("error without id", defs("<error errorCode=\"E\"/>", "<process id=\"p\">" + S + "</process>"),
        "'id' is mandatory on error definition"));
    cases.add(c("import of unknown type", defs("<import importType=\"urn:foo\" location=\"x\" namespace=\"y\"/>", "<process id=\"p\">" + S + "</process>"),
        "Could not import item of type urn:foo"));
    cases.add(c("import without importer for wsdl", defs("<import importType=\"http://schemas.xmlsoap.org/wsdl/\" location=\"x\" namespace=\"y\"/>",
        "<process id=\"p\">" + S + "</process>"), "Could not find importer for type http://schemas.xmlsoap.org/wsdl/"));

    // associations
    cases.add(c("association without sourceRef", proc(S + "<association id=\"a\" targetRef=\"s\"/>"),
        "association element missing attribute 'sourceRef'"));
    cases.add(c("association without targetRef", proc(S + "<association id=\"a\" sourceRef=\"s\"/>"),
        "association element missing attribute 'targetRef'"));
    cases.add(c("association with unknown sourceRef", proc(S + "<association id=\"a\" sourceRef=\"nope\" targetRef=\"s\"/>"),
        "Invalid reference sourceRef 'nope' of association element"));
    cases.add(c("association with unknown targetRef", proc(S + "<association id=\"a\" sourceRef=\"s\" targetRef=\"nope\"/>"),
        "Invalid reference targetRef 'nope' of association element"));

    // events
    cases.add(c("non interrupting error start of event subprocess",
        defs("<error id=\"err\" errorCode=\"E\"/>", "<process id=\"p\">" + S
            + "<subProcess id=\"sub\" triggeredByEvent=\"true\"><startEvent id=\"es\" isInterrupting=\"false\"><errorEventDefinition errorRef=\"err\"/></startEvent>"
            + "<endEvent id=\"ee\"/><sequenceFlow id=\"sf\" sourceRef=\"es\" targetRef=\"ee\"/></subProcess></process>"),
        "error start event of event subprocess must be interrupting"));
    cases.add(c("message start event without messageRef",
        proc("<startEvent id=\"ms\"><messageEventDefinition/></startEvent>"),
        "attribute 'messageRef' is required"));
    cases.add(c("message start event with unknown message",
        proc("<startEvent id=\"ms\"><messageEventDefinition messageRef=\"nope\"/></startEvent>"),
        "Invalid 'messageRef': no message with id 'nope' found."));
    cases.add(c("error end event without errorRef",
        proc(S + "<endEvent id=\"ee\"><errorEventDefinition/></endEvent><sequenceFlow id=\"f\" sourceRef=\"s\" targetRef=\"ee\"/>"),
        "'errorRef' attribute is mandatory on error end event"));
    cases.add(c("unsupported intermediate catch event",
        proc(S + "<intermediateCatchEvent id=\"ice\"/><sequenceFlow id=\"f\" sourceRef=\"s\" targetRef=\"ice\"/>"),
        "Unsupported intermediate catch event type"));
    cases.add(c("compensation throw event with waitForCompletion=false",
        proc(S + "<intermediateThrowEvent id=\"ite\"><compensateEventDefinition waitForCompletion=\"false\"/></intermediateThrowEvent>"
            + "<sequenceFlow id=\"f\" sourceRef=\"s\" targetRef=\"ite\"/>"),
        "'waitForCompletion=false' is not supported"));
    cases.add(c("compensation boundary event with activityRef",
        proc(S + "<userTask id=\"t\"/><boundaryEvent id=\"b\" attachedToRef=\"t\"><compensateEventDefinition activityRef=\"t\"/></boundaryEvent>"),
        "attribute 'activityRef' is not supported on catching compensation event"));

    // gateways
    cases.add(c("exclusive gateway with single conditional outgoing flow",
        proc(S + "<exclusiveGateway id=\"gw\"/><endEvent id=\"e\"/>"
            + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"gw\"/>"
            + "<sequenceFlow id=\"f2\" sourceRef=\"gw\" targetRef=\"e\"><conditionExpression xsi:type=\"tFormalExpression\">${true}</conditionExpression></sequenceFlow>"),
        "has only one outgoing sequence flow ('f2'). This is not allowed to have a condition."));
    cases.add(c("condition expression of unsupported type",
        proc(S + "<exclusiveGateway id=\"gw\"/><endEvent id=\"e\"/><endEvent id=\"e2\"/>"
            + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"gw\"/>"
            + "<sequenceFlow id=\"f2\" sourceRef=\"gw\" targetRef=\"e\"><conditionExpression xsi:type=\"tOther\">${true}</conditionExpression></sequenceFlow>"
            + "<sequenceFlow id=\"f3\" sourceRef=\"gw\" targetRef=\"e2\"/>"),
        "Invalid type, only tFormalExpression is currently supported"));
    cases.add(c("sequence flow with unknown source",
        proc(S + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"nope\" targetRef=\"e\"/>"),
        "Invalid source 'nope' of sequence flow 'f'"));
    cases.add(c("sequence flow towards link event without catching counterpart",
        proc(S + "<intermediateThrowEvent id=\"throw\"><linkEventDefinition name=\"L\"/></intermediateThrowEvent>"
            + "<sequenceFlow id=\"f\" sourceRef=\"s\" targetRef=\"throw\"/>"),
        "sequence flow points to link event source with name 'L' but no event target with that name exists"));

    // multi instance
    cases.add(c("empty loop cardinality",
        proc(S + "<userTask id=\"t\"><multiInstanceLoopCharacteristics><loopCardinality></loopCardinality></multiInstanceLoopCharacteristics></userTask>"),
        "loopCardinality must be defined"));
    cases.add(c("multi instance without cardinality or collection",
        proc(S + "<userTask id=\"t\"><multiInstanceLoopCharacteristics/></userTask>"),
        "Either loopCardinality or loopDataInputRef/activiti:collection must be set"));
    cases.add(c("multi instance element variable without collection",
        proc(S + "<userTask id=\"t\"><multiInstanceLoopCharacteristics camunda:elementVariable=\"x\"><loopCardinality>2</loopCardinality></multiInstanceLoopCharacteristics></userTask>"),
        "LoopDataInputRef/activiti:collection must be set when using inputDataItem or activiti:elementVariable"));

    // service task like
    cases.add(c("service task with invalid type", proc(S + "<serviceTask id=\"t\" camunda:type=\"bogus\"/>"),
        "Invalid usage of type attribute on serviceTask: 'bogus'"));
    cases.add(c("service task with delegateExpression and resultVariable",
        proc(S + "<serviceTask id=\"t\" camunda:delegateExpression=\"${d}\" camunda:resultVariable=\"r\"/>"),
        "'resultVariableName' not supported for serviceTask elements using 'delegateExpression'"));
    cases.add(c("service task without implementation", proc(S + "<serviceTask id=\"t\"/>"),
        "One of the attributes 'class', 'delegateExpression', 'type', "));
    cases.add(c("send task without implementation", proc(S + "<sendTask id=\"t\"/>"),
        "One of the attributes 'class', 'delegateExpression', 'type', or 'expression' is mandatory on sendTask."));
    cases.add(c("send task acting as service task without implementation",
        proc(S + "<sendTask id=\"t\" camunda:type=\"bogus\"/>"),
        "Invalid usage of type attribute on sendTask: 'bogus'"));
    cases.add(c("mail task without recipient",
        proc(S + "<serviceTask id=\"t\" camunda:type=\"mail\"><extensionElements><camunda:field name=\"text\"><camunda:string>x</camunda:string></camunda:field></extensionElements></serviceTask>"),
        "No recipient is defined on the mail activity"));
    cases.add(c("mail task without text",
        proc(S + "<serviceTask id=\"t\" camunda:type=\"mail\"><extensionElements><camunda:field name=\"to\"><camunda:string>x</camunda:string></camunda:field></extensionElements></serviceTask>"),
        "Text or html field should be provided"));
    cases.add(c("shell task without command",
        proc(S + "<serviceTask id=\"t\" camunda:type=\"shell\"><extensionElements><camunda:field name=\"wait\"><camunda:string>true</camunda:string></camunda:field></extensionElements></serviceTask>"),
        "No shell command is defined on the shell activity"));
    cases.add(c("shell task with undefined boolean value",
        proc(S + "<serviceTask id=\"t\" camunda:type=\"shell\"><extensionElements><camunda:field name=\"command\"><camunda:string>ls</camunda:string></camunda:field>"
            + "<camunda:field name=\"wait\"><camunda:string>maybe</camunda:string></camunda:field></extensionElements></serviceTask>"),
        "undefined value for shell wait parameter :maybe"));
    cases.add(c("field declaration without value",
        proc(S + "<serviceTask id=\"t\" camunda:class=\"a.B\"><extensionElements><camunda:field name=\"f\"/></extensionElements></serviceTask>"),
        "One of the following is mandatory on a field declaration"));
    cases.add(c("field declaration with attribute and element value",
        proc(S + "<serviceTask id=\"t\" camunda:class=\"a.B\"><extensionElements><camunda:field name=\"f\" stringValue=\"a\"><camunda:string>b</camunda:string></camunda:field></extensionElements></serviceTask>"),
        "Can't use attribute 'stringValue' and element 'string' together, only use one"));
    cases.add(c("field declaration with empty element value",
        proc(S + "<serviceTask id=\"t\" camunda:class=\"a.B\"><extensionElements><camunda:field name=\"f\"><camunda:string></camunda:string></camunda:field></extensionElements></serviceTask>"),
        "No valid value found in attribute 'stringValue' nor element 'string'"));
    cases.add(c("field declaration with duplicated string element",
        proc(S + "<serviceTask id=\"t\" camunda:class=\"a.B\"><extensionElements><camunda:field name=\"f\"><camunda:string>a</camunda:string><camunda:string>b</camunda:string></camunda:field></extensionElements></serviceTask>"),
        "Multiple string field declarations found"));
    cases.add(c("field declaration with duplicated expression element",
        proc(S + "<serviceTask id=\"t\" camunda:class=\"a.B\"><extensionElements><camunda:field name=\"f\"><camunda:expression>${a}</camunda:expression><camunda:expression>${b}</camunda:expression></camunda:field></extensionElements></serviceTask>"),
        "Multiple expression field declarations found"));

    // user task
    cases.add(c("multiple human performers",
        proc(S + "<userTask id=\"t\"><humanPerformer><resourceAssignmentExpression><formalExpression>a</formalExpression></resourceAssignmentExpression></humanPerformer>"
            + "<humanPerformer><resourceAssignmentExpression><formalExpression>b</formalExpression></resourceAssignmentExpression></humanPerformer></userTask>"),
        "multiple humanPerformer sub elements defined"));
    cases.add(c("task listener with unknown event",
        proc(S + "<userTask id=\"t\"><extensionElements><camunda:taskListener event=\"bogus\" class=\"a.B\"/></extensionElements></userTask>"),
        "Attribute 'event' must be one of {create|assignment|complete|update|delete|timeout}"));
    cases.add(c("task listener without event",
        proc(S + "<userTask id=\"t\"><extensionElements><camunda:taskListener class=\"a.B\"/></extensionElements></userTask>"),
        "Attribute 'event' is mandatory on taskListener"));
    cases.add(c("task listener without implementation",
        proc(S + "<userTask id=\"t\"><extensionElements><camunda:taskListener event=\"create\"/></extensionElements></userTask>"),
        "Element 'class', 'expression', 'delegateExpression' or 'script' is mandatory on taskListener"));
    cases.add(c("task listener with broken script",
        proc(S + "<userTask id=\"t\"><extensionElements><camunda:taskListener event=\"create\"><camunda:script/></camunda:taskListener></extensionElements></userTask>"),
        "Missing attribute 'scriptFormat' for 'script' element"));
    cases.add(c("timeout task listener without id",
        proc(S + "<userTask id=\"t\"><extensionElements><camunda:taskListener event=\"timeout\" class=\"a.B\"><timerEventDefinition><timeDuration>PT1H</timeDuration></timerEventDefinition></camunda:taskListener></extensionElements></userTask>"),
        "Element 'id' is mandatory on taskListener of type 'timeout'"));
    cases.add(c("timeout task listener without timer definition",
        proc(S + "<userTask id=\"t\"><extensionElements><camunda:taskListener id=\"l\" event=\"timeout\" class=\"a.B\"/></extensionElements></userTask>"),
        "Element 'timerEventDefinition' is mandatory on taskListener of type 'timeout'"));

    // execution listeners
    cases.add(c("execution listener with unknown event",
        proc(S + "<userTask id=\"t\"><extensionElements><camunda:executionListener event=\"bogus\" class=\"a.B\"/></extensionElements></userTask>"),
        "Attribute 'event' must be one of {start|end}"));
    cases.add(c("execution listener without event",
        proc(S + "<userTask id=\"t\"><extensionElements><camunda:executionListener class=\"a.B\"/></extensionElements></userTask>"),
        "Attribute 'event' is mandatory on listener"));
    cases.add(c("execution listener without implementation",
        proc(S + "<userTask id=\"t\"><extensionElements><camunda:executionListener event=\"start\"/></extensionElements></userTask>"),
        "Element 'class', 'expression', 'delegateExpression' or 'script' is mandatory on executionListener"));
    cases.add(c("execution listener with broken script",
        proc(S + "<userTask id=\"t\"><extensionElements><camunda:executionListener event=\"start\"><camunda:script/></camunda:executionListener></extensionElements></userTask>"),
        "Missing attribute 'scriptFormat' for 'script' element"));

    // boundary events
    cases.add(c("boundary event without attachedToRef",
        proc(S + "<boundaryEvent id=\"b\"><timerEventDefinition><timeDuration>PT1H</timeDuration></timerEventDefinition></boundaryEvent>"),
        "AttachedToRef is required when using a timerEventDefinition"));
    cases.add(c("boundary event attached to unknown activity",
        proc(S + "<boundaryEvent id=\"b\" attachedToRef=\"nope\"><timerEventDefinition><timeDuration>PT1H</timeDuration></timerEventDefinition></boundaryEvent>"),
        "Invalid reference in boundary event."));
    cases.add(c("boundary event with unsupported definition",
        proc(S + "<userTask id=\"t\"/><boundaryEvent id=\"b\" attachedToRef=\"t\"/>"),
        "Unsupported boundary event type"));

    // call activity
    cases.add(c("call activity without target",
        proc(S + "<callActivity id=\"ca\"/>"),
        "Missing attribute 'calledElement' or 'caseRef'"));
    cases.add(c("call activity with process and case target",
        proc(S + "<callActivity id=\"ca\" calledElement=\"x\" camunda:caseRef=\"y\"/>"),
        "cannot be used together"));
    cases.add(c("call activity version binding without version",
        proc(S + "<callActivity id=\"ca\" calledElement=\"x\" camunda:calledElementBinding=\"version\"/>"),
        "Missing attribute 'calledElementVersion' when 'calledElementBinding' has value 'version'"));
    cases.add(c("call activity in parameter without source",
        proc(S + "<callActivity id=\"ca\" calledElement=\"x\"><extensionElements><camunda:in target=\"t\"/></extensionElements></callActivity>"),
        "Missing parameter 'source' or 'sourceExpression' when passing variables"));
    cases.add(c("call activity in parameter without target",
        proc(S + "<callActivity id=\"ca\" calledElement=\"x\"><extensionElements><camunda:in source=\"s\"/></extensionElements></callActivity>"),
        "Missing attribute 'target' when attribute 'source' or 'sourceExpression' is set"));
    cases.add(c("call activity in parameter with empty target",
        proc(S + "<callActivity id=\"ca\" calledElement=\"x\"><extensionElements><camunda:in source=\"s\" target=\"\"/></extensionElements></callActivity>"),
        "Empty attribute 'target' when attribute 'source' or 'sourceExpression' is set"));
    cases.add(c("call activity in parameter with empty sourceExpression",
        proc(S + "<callActivity id=\"ca\" calledElement=\"x\"><extensionElements><camunda:in sourceExpression=\"\" target=\"t\"/></extensionElements></callActivity>"),
        "Empty attribute 'sourceExpression' when passing variables"));

    // properties / io mapping
    cases.add(c("property without id and name",
        proc(S + "<userTask id=\"t\"><property/></userTask>"),
        "Invalid property usage on line"));
    cases.add(c("input output mapping on unsupported element",
        proc("<startEvent id=\"s\"/><exclusiveGateway id=\"gw\"><extensionElements><camunda:inputOutput><camunda:inputParameter name=\"a\">1</camunda:inputParameter></camunda:inputOutput></extensionElements></exclusiveGateway>"
            + "<sequenceFlow id=\"f\" sourceRef=\"s\" targetRef=\"gw\"/>"),
        "camunda:inputOutput mapping unsupported for element type 'exclusiveGateway'."));
    cases.add(c("input output mapping with broken parameter",
        proc(S + "<userTask id=\"t\"><extensionElements><camunda:inputOutput><camunda:inputParameter>1</camunda:inputParameter></camunda:inputOutput></extensionElements></userTask>"),
        "Missing attribute 'name' for inputParameter"));

    // variable listener
    cases.add(c("conditional event with invalid variable event",
        proc("<startEvent id=\"s\"/><intermediateCatchEvent id=\"ice\"><conditionalEventDefinition camunda:variableEvents=\"bogus\"><condition>${true}</condition></conditionalEventDefinition></intermediateCatchEvent>"
            + "<sequenceFlow id=\"f\" sourceRef=\"s\" targetRef=\"ice\"/>"),
        "Variable event: bogus is not valid."));

    // diagram interchange
    cases.add(c("plane without bpmnElement",
        diagram(S, "<bpmndi:BPMNPlane/>"),
        "'bpmnElement' attribute is required on BPMNPlane"));
    cases.add(c("shape without bpmnElement",
        diagram(S, "<bpmndi:BPMNPlane bpmnElement=\"p\"><bpmndi:BPMNShape><dc:Bounds x=\"0\" y=\"0\" width=\"1\" height=\"1\"/></bpmndi:BPMNShape></bpmndi:BPMNPlane>"),
        "'bpmnElement' attribute is required on BPMNShape"));
    cases.add(c("shape referencing unknown element",
        diagram(S, "<bpmndi:BPMNPlane bpmnElement=\"p\"><bpmndi:BPMNShape bpmnElement=\"nope\"><dc:Bounds x=\"0\" y=\"0\" width=\"1\" height=\"1\"/></bpmndi:BPMNShape></bpmndi:BPMNPlane>"),
        "Invalid reference in 'bpmnElement' attribute, activity nope not found"));
    cases.add(c("shape without bounds",
        diagram(S, "<bpmndi:BPMNPlane bpmnElement=\"p\"><bpmndi:BPMNShape bpmnElement=\"s\"/></bpmndi:BPMNPlane>"),
        "'Bounds' element is required"));
    cases.add(c("shape with unparsable bounds",
        diagram(S, "<bpmndi:BPMNPlane bpmnElement=\"p\"><bpmndi:BPMNShape bpmnElement=\"s\"><dc:Bounds x=\"abc\" y=\"0\" width=\"1\" height=\"1\"/></bpmndi:BPMNShape></bpmndi:BPMNPlane>"),
        "Cannot parse x:"));
    cases.add(c("shape with missing bounds attribute",
        diagram(S, "<bpmndi:BPMNPlane bpmnElement=\"p\"><bpmndi:BPMNShape bpmnElement=\"s\"><dc:Bounds y=\"0\" width=\"1\" height=\"1\"/></bpmndi:BPMNShape></bpmndi:BPMNPlane>"),
        "x is required"));
    cases.add(c("edge with too few waypoints",
        diagram(S + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"s\" targetRef=\"e\"/>",
            "<bpmndi:BPMNPlane bpmnElement=\"p\"><bpmndi:BPMNEdge bpmnElement=\"f\"><di:waypoint x=\"0\" y=\"0\"/></bpmndi:BPMNEdge></bpmndi:BPMNPlane>"),
        "Minimum 2 waypoint elements must be definted for a 'BPMNEdge'"));
    cases.add(c("edge referencing unknown flow",
        diagram(S, "<bpmndi:BPMNPlane bpmnElement=\"p\"><bpmndi:BPMNEdge bpmnElement=\"nope\"><di:waypoint x=\"0\" y=\"0\"/><di:waypoint x=\"1\" y=\"1\"/></bpmndi:BPMNEdge></bpmndi:BPMNPlane>"),
        "Invalid reference in 'bpmnElement' attribute, sequenceFlow nope"));
    cases.add(c("edge without bpmnElement",
        diagram(S, "<bpmndi:BPMNPlane bpmnElement=\"p\"><bpmndi:BPMNEdge/></bpmndi:BPMNPlane>"),
        "'bpmnElement' attribute is required on BPMNEdge"));

    return cases;
  }

  protected static final String S = "<startEvent id=\"s\"/>";

  private static Object[] c(String name, String xml, String expected) {
    return new Object[] { name, xml, expected };
  }

  private static String defs(String beforeProcess, String process) {
    return defs(beforeProcess, process, "");
  }

  private static String defs(String beforeProcess, String process, String afterProcess) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
        + " xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\" xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
        + " xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\""
        + " targetNamespace=\"test\">" + beforeProcess + process + afterProcess + "</definitions>";
  }

  private static String proc(String body) {
    return defs("", "<process id=\"p\" isExecutable=\"true\">" + body + "</process>");
  }

  private static String diagram(String body, String plane) {
    return defs("", "<process id=\"p\" isExecutable=\"true\">" + body + "</process>",
        "<bpmndi:BPMNDiagram id=\"d\">" + plane + "</bpmndi:BPMNDiagram>");
  }

  private List<Problem> parse(String xml) {
    ProcessEngineConfigurationImpl configuration = engineRule.getProcessEngineConfiguration();
    return configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      BpmnParse parse = new NonValidatingBpmnParser(configuration).createParse();
      parse.setSchemaResource(null);
      parse.deployment(new DeploymentEntity()).sourceString(xml);
      try {
        parse.execute();
      } catch (ProcessEngineException e) {
        // errors are inspected through the collected problems
      }
      return parse.getProblems();
    });
  }

  /**
   * Namespace aware, but without XSD validation, so that the semantic checks of the parser are reached.
   */
  protected static class NonValidatingBpmnParser extends BpmnParser {

    protected NonValidatingBpmnParser(ProcessEngineConfigurationImpl configuration) {
      super(configuration.getExpressionManager(), configuration.getBpmnParseFactory());
    }

    @Override
    public BpmnParse createParse() {
      return new BpmnParse(this) {
        @Override
        public List<Problem> getProblems() {
          List<Problem> all = new ArrayList<>(errors);
          all.addAll(warnings);
          return all;
        }
      };
    }

    @Override
    protected SAXParserFactory getSaxParserFactoryLazily() {
      // fresh instance on every call: mutations by Parse#setSchemaResource are discarded
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setValidating(false);
      try {
        factory.setFeature(NAMESPACE_PREFIXES, true);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      return factory;
    }
  }

  @Test
  public void reportsExpectedProblem() {
    List<String> messages = parse(xml).stream().map(Problem::getMessage).collect(Collectors.toList());

    assertThat(messages.stream().anyMatch(message -> message.contains(expectedMessage)))
        .as("expected '%s' among problems %s", expectedMessage, messages)
        .isTrue();
  }
}
