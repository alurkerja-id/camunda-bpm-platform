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
package org.camunda.bpm.engine.cdi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.InjectionPoint;

import org.camunda.bpm.engine.cdi.annotation.ProcessVariable;
import org.camunda.bpm.engine.cdi.annotation.ProcessVariableLocal;
import org.camunda.bpm.engine.cdi.annotation.ProcessVariableLocalTyped;
import org.camunda.bpm.engine.cdi.annotation.ProcessVariableTyped;
import org.camunda.bpm.engine.cdi.impl.ProcessVariableLocalMap;
import org.camunda.bpm.engine.cdi.impl.ProcessVariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProcessVariablesUnitTest {

  @Mock private BusinessProcess businessProcess;
  @Mock private ProcessVariableMap processVariableMap;
  @Mock private ProcessVariableLocalMap processVariableLocalMap;
  @InjectMocks private ProcessVariables producer;

  private InjectionPoint injectionPoint(Class<? extends Annotation> type, String annotationValue, String memberName) throws Exception {
    Annotation annotation = mock(type);
    if (type == ProcessVariable.class) {
      when(((ProcessVariable) annotation).value()).thenReturn(annotationValue);
    } else if (type == ProcessVariableTyped.class) {
      when(((ProcessVariableTyped) annotation).value()).thenReturn(annotationValue);
    } else if (type == ProcessVariableLocal.class) {
      when(((ProcessVariableLocal) annotation).value()).thenReturn(annotationValue);
    } else {
      when(((ProcessVariableLocalTyped) annotation).value()).thenReturn(annotationValue);
    }
    Annotated annotated = mock(Annotated.class);
    when(annotated.getAnnotation((Class) type)).thenReturn(annotation);
    java.lang.reflect.Member member = mock(java.lang.reflect.Member.class);
    when(member.getName()).thenReturn(memberName);
    InjectionPoint ip = mock(InjectionPoint.class);
    when(ip.getAnnotated()).thenReturn(annotated);
    when(ip.getMember()).thenReturn(member);
    return ip;
  }

  private TypedValue typed;

  @Before
  public void setUp() {
    typed = Variables.stringValue("hello");
  }

  @Test
  public void variableNameUsesAnnotationValueOrFallsBackToMemberName() throws Exception {
    assertThat(producer.getVariableName(injectionPoint(ProcessVariable.class, "explicit", "field"))).isEqualTo("explicit");
    assertThat(producer.getVariableName(injectionPoint(ProcessVariable.class, "", "field"))).isEqualTo("field");
    assertThat(producer.getVariableTypedName(injectionPoint(ProcessVariableTyped.class, "explicit", "field"))).isEqualTo("explicit");
    assertThat(producer.getVariableTypedName(injectionPoint(ProcessVariableTyped.class, "", "field"))).isEqualTo("field");
    assertThat(producer.getVariableLocalName(injectionPoint(ProcessVariableLocal.class, "explicit", "field"))).isEqualTo("explicit");
    assertThat(producer.getVariableLocalName(injectionPoint(ProcessVariableLocal.class, "", "field"))).isEqualTo("field");
    assertThat(producer.getVariableLocalTypedName(injectionPoint(ProcessVariableLocalTyped.class, "explicit", "field"))).isEqualTo("explicit");
    assertThat(producer.getVariableLocalTypedName(injectionPoint(ProcessVariableLocalTyped.class, "", "field"))).isEqualTo("field");
  }

  @Test
  public void producersReadFromBusinessProcess() throws Exception {
    when(businessProcess.getVariable("v")).thenReturn("value");
    when(businessProcess.getVariableLocal("v")).thenReturn("local");
    when(businessProcess.getVariableTyped("v")).thenReturn(typed);
    when(businessProcess.getVariableLocalTyped("v")).thenReturn(typed);

    assertThat(producer.getProcessVariable(injectionPoint(ProcessVariable.class, "v", "x"))).isEqualTo("value");
    assertThat(producer.getProcessVariableLocal(injectionPoint(ProcessVariableLocal.class, "v", "x"))).isEqualTo("local");
    assertThat(producer.getProcessVariableTyped(injectionPoint(ProcessVariableTyped.class, "v", "x"))).isSameAs(typed);
    assertThat(producer.getProcessVariableLocalTyped(injectionPoint(ProcessVariableLocalTyped.class, "v", "x"))).isSameAs(typed);
  }

  @Test
  public void producersReadFromBusinessProcessWithFineLoggingEnabled() throws Exception {
    java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ProcessVariables.class.getName());
    java.util.logging.Level before = logger.getLevel();
    logger.setLevel(java.util.logging.Level.FINE);
    try {
      when(businessProcess.getVariable("v")).thenReturn("value");
      when(businessProcess.getVariableLocal("v")).thenReturn("local");
      when(businessProcess.getVariableTyped("v")).thenReturn(typed);
      when(businessProcess.getVariableLocalTyped("v")).thenReturn(typed);

      assertThat(producer.getProcessVariable(injectionPoint(ProcessVariable.class, "v", "x"))).isEqualTo("value");
      assertThat(producer.getProcessVariableLocal(injectionPoint(ProcessVariableLocal.class, "v", "x"))).isEqualTo("local");
      assertThat(producer.getProcessVariableTyped(injectionPoint(ProcessVariableTyped.class, "v", "x"))).isSameAs(typed);
      assertThat(producer.getProcessVariableLocalTyped(injectionPoint(ProcessVariableLocalTyped.class, "v", "x"))).isSameAs(typed);
    } finally {
      logger.setLevel(before);
    }
  }

  @Test
  public void mapProducersExposeInjectedMaps() {
    assertThat(producer.processVariables()).isSameAs(processVariableMap);
    assertThat(producer.processVariableMap()).isSameAs(processVariableMap);
    assertThat(producer.processVariablesLocal()).isSameAs(processVariableLocalMap);
    assertThat(producer.processVariableMapLocal()).isSameAs(processVariableLocalMap);
  }
}
