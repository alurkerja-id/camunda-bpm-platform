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

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.persistence.entity.DeploymentEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Public helpers and fluent overrides of {@link BpmnParse} plus the parse listener hooks of
 * elements that are only reachable with a registered {@link BpmnParseListener}.
 */
public class BpmnParseHelpersTest {

  @Rule
  public ProcessEngineRule engineRule = new ProvidedProcessEngineRule();

  protected ProcessEngineConfigurationImpl configuration;
  protected BpmnParser parser;

  @Before
  public void init() {
    configuration = engineRule.getProcessEngineConfiguration();
    parser = new BpmnParser(configuration.getExpressionManager(), configuration.getBpmnParseFactory());
  }

  private BpmnParse newParse() {
    return parser.createParse();
  }

  @Test
  public void parseBooleanAttributeRecognizesTrueSynonyms() {
    BpmnParse parse = newParse();

    for (String text : new String[] { "true", "enabled", "on", "active", "yes" }) {
      assertThat(parse.parseBooleanAttribute(text)).as(text).isTrue();
    }
  }

  @Test
  public void parseBooleanAttributeRecognizesFalseSynonyms() {
    BpmnParse parse = newParse();

    for (String text : new String[] { "false", "disabled", "off", "inactive", "no" }) {
      assertThat(parse.parseBooleanAttribute(text)).as(text).isFalse();
    }
  }

  @Test
  public void parseBooleanAttributeYieldsNullForUnknownOrMissingText() {
    BpmnParse parse = newParse();

    assertThat(parse.parseBooleanAttribute("maybe")).isNull();
    assertThat(parse.parseBooleanAttribute(null)).isNull();
    assertThat(parse.parseBooleanAttribute("")).isNull();
  }

  @Test
  public void fluentSourceMethodsReturnTheParseItself() throws Exception {
    BpmnParse parse = newParse();
    URL url = getClass().getResource("/org/camunda/bpm/engine/impl/bpmn/parser/BPMN20.xsd");

    assertThat(parse.sourceResource("org/camunda/bpm/engine/impl/bpmn/parser/BPMN20.xsd", getClass().getClassLoader())).isSameAs(parse);

    BpmnParse second = newParse();
    assertThat(second.sourceResource("org/camunda/bpm/engine/impl/bpmn/parser/BPMN20.xsd")).isSameAs(second);

    BpmnParse third = newParse();
    assertThat(third.sourceString("<definitions/>")).isSameAs(third);

    BpmnParse fourth = newParse();
    assertThat(fourth.sourceUrl(url)).isSameAs(fourth);

    BpmnParse fifth = newParse();
    assertThat(fifth.sourceUrl(url.toString())).isSameAs(fifth);
  }

  @Test
  public void jobDeclarationsByKeyAreEmptyBeforeParsing() {
    BpmnParse parse = newParse();

    assertThat(parse.getJobDeclarationsByKey("unknown")).isNull();
    assertThat(parse.getJobDeclarations()).isEmpty();
  }

  @Test
  public void sendTaskBehaviorMayBeProvidedByParseListener() {
    BpmnParser listeningParser = new BpmnParser(configuration.getExpressionManager(), configuration.getBpmnParseFactory());
    final boolean[] notified = { false };
    listeningParser.setParseListeners(Collections.<BpmnParseListener> singletonList(new AbstractBpmnParseListener() {
      @Override
      public void parseSendTask(org.camunda.bpm.engine.impl.util.xml.Element sendTaskElement, ScopeImpl scope, ActivityImpl activity) {
        notified[0] = true;
        activity.setActivityBehavior(new UserTaskActivityBehavior(null));
      }
    }));

    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"test\">"
        + "<process id=\"p\" isExecutable=\"true\"><startEvent id=\"s\"/><sendTask id=\"send\"/>"
        + "<endEvent id=\"e\"/><sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"send\"/>"
        + "<sequenceFlow id=\"f2\" sourceRef=\"send\" targetRef=\"e\"/></process></definitions>";

    BpmnParse parse = configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      BpmnParse p = listeningParser.createParse();
      p.deployment(new DeploymentEntity()).sourceInputStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      p.execute();
      return p;
    });

    assertThat(notified[0]).isTrue();
    assertThat(parse.hasErrors()).isFalse();
  }
}
