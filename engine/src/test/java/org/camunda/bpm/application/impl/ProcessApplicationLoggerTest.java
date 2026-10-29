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
package org.camunda.bpm.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.naming.NamingException;
import org.camunda.bpm.application.ProcessApplicationExecutionException;
import org.camunda.bpm.application.ProcessApplicationUnavailableException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.impl.ProcessEngineLogger;
import org.camunda.bpm.engine.impl.application.ProcessApplicationManager;
import org.camunda.bpm.engine.impl.cmmn.entity.repository.CaseDefinitionEntity;
import org.camunda.bpm.engine.impl.cmmn.entity.runtime.CaseExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.junit.Test;

public class ProcessApplicationLoggerTest {

  private final ProcessApplicationLogger log = ProcessEngineLogger.PROCESS_APPLICATION_LOGGER;

  @Test
  public void shouldCreateExceptionsWithCodedMessages() {
    Exception cause = new IllegalStateException("boom");
    NamingException naming = new NamingException("naming");

    ProcessEngineException listener = log.exceptionWhileNotifyingPaTaskListener(cause);
    assertThat(listener.getMessage()).contains("002");
    assertThat(listener.getCause()).isSameAs(cause);

    ProcessApplicationExecutionException exec = log.processApplicationExecutionException(cause);
    assertThat(exec.getCause()).isSameAs(cause);

    assertThat(log.ejbPaCannotLookupSelfReference(naming).getMessage()).contains("009");
    assertThat(log.ejbPaCannotAutodetectName(naming).getCause()).isSameAs(naming);

    ProcessApplicationUnavailableException withCause = log.processApplicationUnavailableException("app", cause);
    assertThat(withCause.getMessage()).contains("app");
    assertThat(withCause.getCause()).isSameAs(cause);
    assertThat(log.processApplicationUnavailableException("other").getMessage()).contains("other");

    ProcessEngineException jobExecutor = log.exceptionWhileRegisteringDeploymentsWithJobExecutor(cause);
    assertThat(jobExecutor.getCause()).isSameAs(cause);
  }

  @Test
  public void shouldBuildMessages() {
    Set<Class<?>> classes = new LinkedHashSet<>();
    classes.add(String.class);
    classes.add(Integer.class);

    String multiple = log.multiplePasException(classes, "my-app");
    assertThat(multiple).contains("my-app", String.class.getName(), Integer.class.getName(), "013");

    String wrongType = log.paWrongTypeException(String.class);
    assertThat(wrongType).contains(String.class.getName(), "014");
  }

  @Test
  public void shouldContextSwitchLoggableMatchDebugLevel() {
    assertThat(log.isContextSwitchLoggable()).isEqualTo(log.isDebugEnabled());
  }

  @Test
  public void shouldLogWithoutThrowing() {
    DelegateTask task = mock(DelegateTask.class);
    DelegateExecution execution = mock(DelegateExecution.class);
    ProcessApplicationUnavailableException unavailable = new ProcessApplicationUnavailableException("gone");
    ProcessApplicationManager manager = mock(ProcessApplicationManager.class);
    when(manager.getRegistrationSummary()).thenReturn("summary");

    ExecutionEntity entity = mock(ExecutionEntity.class);
    ProcessDefinitionEntity definition = mock(ProcessDefinitionEntity.class);
    when(entity.getProcessDefinition()).thenReturn(definition);
    when(definition.getDeploymentId()).thenReturn("dep");

    CaseExecutionEntity caseExecution = mock(CaseExecutionEntity.class);
    CaseDefinitionEntity caseDefinition = mock(CaseDefinitionEntity.class);
    when(caseExecution.getCaseDefinition()).thenReturn(caseDefinition);
    when(caseDefinition.getDeploymentId()).thenReturn("dep");

    Throwable thrown = null;
    try {
    log.taskNotRelatedToExecution(task);
    log.noTargetProcessApplicationForExecution(execution);
    log.paDoesNotProvideExecutionListener("pa");
    log.cannotInvokeListenerPaUnavailable("pa", unavailable);
    log.paDoesNotProvideTaskListener("pa");
    log.paElResolversDiscovered("summary");
    log.noElResolverProvided("pa", "resolver");
    log.servletDeployerNoPaFound("ctx");
    log.detectedPa(String.class);
    log.alreadyDeployed();
    log.notDeployed();
    log.couldNotRemoveDefinitionsFromCache(new RuntimeException("x"));
    log.exceptionWhileUnregisteringDeploymentsWithJobExecutor(new RuntimeException("x"));
    log.registrationSummary("summary");
    log.exceptionWhileLoggingRegistrationSummary(new RuntimeException("x"));
    log.debugNoTargetProcessApplicationFound(entity, manager);
    log.debugNoTargetProcessApplicationFoundForCaseExecution(caseExecution, manager);

    } catch (Throwable t) {
      thrown = t;
    }
    assertThat(thrown).isNull();
  }
}
