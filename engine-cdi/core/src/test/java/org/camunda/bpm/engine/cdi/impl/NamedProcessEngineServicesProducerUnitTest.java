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
package org.camunda.bpm.engine.cdi.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.InjectionPoint;

import org.camunda.bpm.container.RuntimeContainerDelegate;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.cdi.annotation.ProcessEngineName;
import org.camunda.bpm.ProcessEngineService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NamedProcessEngineServicesProducerUnitTest {

  private final NamedProcessEngineServicesProducer producer = new NamedProcessEngineServicesProducer();
  private final ProcessEngine engine = mock(ProcessEngine.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
  private final ProcessEngineService engineService = mock(ProcessEngineService.class);
  private final RuntimeContainerDelegate delegate = mock(RuntimeContainerDelegate.class);
  private RuntimeContainerDelegate previous;

  @Before
  public void setUp() {
    previous = RuntimeContainerDelegate.INSTANCE.get();
    RuntimeContainerDelegate.INSTANCE.set(delegate);
    when(delegate.getProcessEngineService()).thenReturn(engineService);
    when(engineService.getProcessEngine("named")).thenReturn(engine);
  }

  @After
  public void tearDown() {
    RuntimeContainerDelegate.INSTANCE.set(previous);
  }

  private InjectionPoint injectionPoint(String engineName) {
    ProcessEngineName annotation = mock(ProcessEngineName.class);
    when(annotation.value()).thenReturn(engineName);
    Annotated annotated = mock(Annotated.class);
    when(annotated.getAnnotation(ProcessEngineName.class)).thenReturn(annotation);
    InjectionPoint ip = mock(InjectionPoint.class);
    when(ip.getAnnotated()).thenReturn(annotated);
    return ip;
  }

  @Test
  public void resolvesEngineByName() {
    assertThat(producer.processEngine(injectionPoint("named"))).isSameAs(engine);
  }

  @Test
  public void rejectsEmptyName() {
    assertThatThrownBy(() -> producer.processEngine(injectionPoint("")))
      .isInstanceOf(ProcessEngineException.class).hasMessageContaining("@ProcessEngineName");
    assertThatThrownBy(() -> producer.processEngine(injectionPoint(null)))
      .isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void wrapsLookupFailure() {
    when(engineService.getProcessEngine("broken")).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> producer.processEngine(injectionPoint("broken")))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("'broken'").hasMessageContaining("boom")
      .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void servicesComeFromNamedEngine() {
    InjectionPoint ip = injectionPoint("named");

    assertThat(producer.runtimeService(ip)).isSameAs(engine.getRuntimeService());
    assertThat(producer.taskService(ip)).isSameAs(engine.getTaskService());
    assertThat(producer.repositoryService(ip)).isSameAs(engine.getRepositoryService());
    assertThat(producer.formService(ip)).isSameAs(engine.getFormService());
    assertThat(producer.historyService(ip)).isSameAs(engine.getHistoryService());
    assertThat(producer.identityService(ip)).isSameAs(engine.getIdentityService());
    assertThat(producer.managementService(ip)).isSameAs(engine.getManagementService());
    assertThat(producer.authorizationService(ip)).isSameAs(engine.getAuthorizationService());
    assertThat(producer.filterService(ip)).isSameAs(engine.getFilterService());
    assertThat(producer.externalTaskService(ip)).isSameAs(engine.getExternalTaskService());
    assertThat(producer.caseService(ip)).isSameAs(engine.getCaseService());
    assertThat(producer.decisionService(ip)).isSameAs(engine.getDecisionService());
  }
}
