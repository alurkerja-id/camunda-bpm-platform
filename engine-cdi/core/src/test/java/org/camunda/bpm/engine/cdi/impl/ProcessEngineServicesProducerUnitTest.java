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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.camunda.bpm.ProcessEngineService;
import org.camunda.bpm.container.RuntimeContainerDelegate;
import org.camunda.bpm.engine.ProcessEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ProcessEngineServicesProducerUnitTest {

  private final ProcessEngineServicesProducer producer = new ProcessEngineServicesProducer();
  private final ProcessEngineService engineService = mock(ProcessEngineService.class);
  private final RuntimeContainerDelegate delegate = mock(RuntimeContainerDelegate.class);
  private final ProcessEngine defaultEngine = mock(ProcessEngine.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
  private RuntimeContainerDelegate previous;

  @Before
  public void setUp() {
    previous = RuntimeContainerDelegate.INSTANCE.get();
    RuntimeContainerDelegate.INSTANCE.set(delegate);
    when(delegate.getProcessEngineService()).thenReturn(engineService);
  }

  @After
  public void tearDown() {
    RuntimeContainerDelegate.INSTANCE.set(previous);
  }

  @Test
  public void prefersPlatformDefaultEngine() {
    when(engineService.getDefaultProcessEngine()).thenReturn(defaultEngine);

    assertThat(producer.processEngine()).isSameAs(defaultEngine);
  }

  @Test
  public void fallsBackToSingleRegisteredEngine() {
    ProcessEngine only = mock(ProcessEngine.class);
    when(engineService.getDefaultProcessEngine()).thenReturn(null);
    when(engineService.getProcessEngines()).thenReturn(Collections.singletonList(only));

    assertThat(producer.processEngine()).isSameAs(only);
  }

  @Test
  public void servicesComeFromDefaultEngine() {
    when(engineService.getDefaultProcessEngine()).thenReturn(defaultEngine);

    assertThat(producer.runtimeService()).isSameAs(defaultEngine.getRuntimeService());
    assertThat(producer.taskService()).isSameAs(defaultEngine.getTaskService());
    assertThat(producer.repositoryService()).isSameAs(defaultEngine.getRepositoryService());
    assertThat(producer.formService()).isSameAs(defaultEngine.getFormService());
    assertThat(producer.historyService()).isSameAs(defaultEngine.getHistoryService());
    assertThat(producer.identityService()).isSameAs(defaultEngine.getIdentityService());
    assertThat(producer.managementService()).isSameAs(defaultEngine.getManagementService());
    assertThat(producer.authorizationService()).isSameAs(defaultEngine.getAuthorizationService());
    assertThat(producer.filterService()).isSameAs(defaultEngine.getFilterService());
    assertThat(producer.externalTaskService()).isSameAs(defaultEngine.getExternalTaskService());
    assertThat(producer.caseService()).isSameAs(defaultEngine.getCaseService());
    assertThat(producer.decisionService()).isSameAs(defaultEngine.getDecisionService());
  }
}
