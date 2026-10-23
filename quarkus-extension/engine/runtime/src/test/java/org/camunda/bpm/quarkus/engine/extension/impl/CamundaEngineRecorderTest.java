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

package org.camunda.bpm.quarkus.engine.extension.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.camunda.bpm.container.RuntimeContainerDelegate;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.cdi.impl.event.CdiEventSupportBpmnParseListener;
import org.camunda.bpm.engine.cdi.impl.util.BeanManagerLookup;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.quarkus.engine.extension.CamundaEngineConfig;
import org.camunda.bpm.quarkus.engine.extension.CamundaJobExecutorConfig;
import org.camunda.bpm.quarkus.engine.extension.QuarkusProcessEngineConfiguration;
import org.camunda.bpm.quarkus.engine.extension.event.CamundaEngineStartupEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class CamundaEngineRecorderTest {

  private final CamundaEngineRecorder recorder = new CamundaEngineRecorder();
  private BeanManager previousLookup;

  @Before
  public void saveLookup() {
    previousLookup = BeanManagerLookup.localInstance;
  }

  @After
  public void restoreLookup() {
    BeanManagerLookup.localInstance = previousLookup;
  }

  @SuppressWarnings("unchecked")
  private static <T> BeanContainer containerReturning(Class<T> type, T bean) {
    BeanContainer container = mock(BeanContainer.class);
    BeanContainer.Factory<T> factory = mock(BeanContainer.Factory.class);
    BeanContainer.Instance<T> instance = mock(BeanContainer.Instance.class);
    when(container.beanInstanceFactory(type)).thenReturn(factory);
    when(factory.create()).thenReturn(instance);
    when(instance.get()).thenReturn(bean);
    return container;
  }

  private static CamundaEngineConfig config(int maxPool, int queue, Map<String, String> engine,
                                            Map<String, String> executor, Optional<String> datasource) {
    CamundaEngineConfig config = mock(CamundaEngineConfig.class);
    CamundaJobExecutorConfig jobExecutorConfig = mock(CamundaJobExecutorConfig.class);
    CamundaJobExecutorConfig.ThreadPoolConfig pool = mock(CamundaJobExecutorConfig.ThreadPoolConfig.class);
    when(config.genericConfig()).thenReturn(engine);
    when(config.jobExecutor()).thenReturn(jobExecutorConfig);
    when(config.datasource()).thenReturn(datasource);
    when(jobExecutorConfig.genericConfig()).thenReturn(executor);
    when(jobExecutorConfig.threadPool()).thenReturn(pool);
    when(pool.maxPoolSize()).thenReturn(maxPool);
    when(pool.queueSize()).thenReturn(queue);
    return config;
  }

  @Test
  public void shouldRegisterBeanManagerWhenLookupIsEmpty() {
    BeanManager beanManager = mock(BeanManager.class);
    BeanManagerLookup.localInstance = null;

    recorder.configureProcessEngineCdiBeans(containerReturning(BeanManager.class, beanManager));

    assertThat(BeanManagerLookup.localInstance).isSameAs(beanManager);
  }

  @Test
  public void shouldKeepExistingBeanManager() {
    BeanManager existing = mock(BeanManager.class);
    BeanManagerLookup.localInstance = existing;
    BeanContainer container = mock(BeanContainer.class);

    recorder.configureProcessEngineCdiBeans(container);

    assertThat(BeanManagerLookup.localInstance).isSameAs(existing);
    verifyNoInteractions(container);
  }

  @Test
  public void shouldCreateConfigurationWithJobExecutorAndCdiBridge() {
    QuarkusProcessEngineConfiguration configuration = new QuarkusProcessEngineConfiguration();
    configuration.setDataSource(mock(DataSource.class));
    configuration.setTransactionManager(mock(TransactionManager.class));
    Map<String, String> engine = new HashMap<>();
    engine.put("job-executor-activate", "false");
    Map<String, String> executor = new HashMap<>();
    executor.put("max-jobs-per-acquisition", "7");
    CamundaEngineConfig config = config(2, 1, engine, executor, Optional.empty());

    RuntimeValue<ProcessEngineConfigurationImpl> value = recorder.createProcessEngineConfiguration(
        containerReturning(QuarkusProcessEngineConfiguration.class, configuration), config);

    assertThat(value.getValue()).isSameAs(configuration);
    assertThat(configuration.isJobExecutorActivate()).isFalse();
    assertThat(configuration.getJobExecutor()).isInstanceOf(ManagedJobExecutor.class);
    assertThat(configuration.getJobExecutor().getMaxJobsPerAcquisition()).isEqualTo(7);
    assertThat(configuration.getCustomPostBPMNParseListeners())
        .hasSize(1).hasOnlyElementsOfType(CdiEventSupportBpmnParseListener.class);
  }

  @Test
  public void shouldKeepCustomJobExecutorAndAppendToExistingParseListeners() {
    QuarkusProcessEngineConfiguration configuration = new QuarkusProcessEngineConfiguration();
    configuration.setDataSource(mock(DataSource.class));
    configuration.setTransactionManager(mock(TransactionManager.class));
    JobExecutor custom = mock(JobExecutor.class);
    configuration.setJobExecutor(custom);
    BpmnParseListener existing = mock(BpmnParseListener.class);
    List<BpmnParseListener> listeners = new ArrayList<>();
    listeners.add(existing);
    configuration.setCustomPostBPMNParseListeners(listeners);
    CamundaEngineConfig config = config(1, 1, Collections.emptyMap(), Collections.emptyMap(), Optional.empty());

    recorder.createProcessEngineConfiguration(
        containerReturning(QuarkusProcessEngineConfiguration.class, configuration), config);

    assertThat(configuration.getJobExecutor()).isSameAs(custom);
    assertThat(configuration.getCustomPostBPMNParseListeners()).hasSize(2).startsWith(existing);
    assertThat(configuration.getCustomPostBPMNParseListeners().get(1))
        .isInstanceOf(CdiEventSupportBpmnParseListener.class);
  }

  @Test
  public void shouldBuildAndRegisterProcessEngine() {
    ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
    ProcessEngine engine = mock(ProcessEngine.class);
    when(configuration.buildProcessEngine()).thenReturn(engine);
    RuntimeContainerDelegate delegate = mock(RuntimeContainerDelegate.class);

    RuntimeContainerDelegate previous = RuntimeContainerDelegate.INSTANCE.get();
    RuntimeContainerDelegate.INSTANCE.set(delegate);
    try {
      RuntimeValue<ProcessEngine> value = recorder.createProcessEngine(new RuntimeValue<>(configuration));

      assertThat(value.getValue()).isSameAs(engine);
      verify(delegate).registerProcessEngine(engine);
    } finally {
      RuntimeContainerDelegate.INSTANCE.set(previous);
    }
  }

  @Test
  public void shouldFireStartupEventThroughArc() {
    ArcContainer arc = mock(ArcContainer.class);
    BeanManager beanManager = mock(BeanManager.class);
    @SuppressWarnings("unchecked")
    Event<Object> event = mock(Event.class);
    @SuppressWarnings("unchecked")
    Event<CamundaEngineStartupEvent> typed = mock(Event.class);
    when(arc.beanManager()).thenReturn(beanManager);
    when(beanManager.getEvent()).thenReturn(event);
    when(event.select(CamundaEngineStartupEvent.class)).thenReturn(typed);

    try (MockedStatic<Arc> arcStatic = mockStatic(Arc.class)) {
      arcStatic.when(Arc::container).thenReturn(arc);

      recorder.fireCamundaEngineStartEvent();
    }

    ArgumentCaptor<CamundaEngineStartupEvent> fired = ArgumentCaptor.forClass(CamundaEngineStartupEvent.class);
    verify(typed).fire(fired.capture());
    assertThat(fired.getValue()).isNotNull();
  }

  @Test
  public void shouldShutDownJobExecutorAndUnregisterEngineOnShutdown() {
    ProcessEngine engine = mock(ProcessEngine.class);
    ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
    JobExecutor executor = mock(JobExecutor.class);
    when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
    when(configuration.getJobExecutor()).thenReturn(executor);
    RuntimeContainerDelegate delegate = mock(RuntimeContainerDelegate.class);
    ShutdownContext shutdownContext = mock(ShutdownContext.class);

    recorder.registerShutdownTask(shutdownContext, new RuntimeValue<>(engine));

    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(shutdownContext).addShutdownTask(task.capture());
    verifyNoInteractions(executor);

    RuntimeContainerDelegate previous = RuntimeContainerDelegate.INSTANCE.get();
    RuntimeContainerDelegate.INSTANCE.set(delegate);
    try {
      task.getValue().run();
    } finally {
      RuntimeContainerDelegate.INSTANCE.set(previous);
    }

    verify(executor).shutdown();
    verify(delegate).unregisterProcessEngine(engine);
  }
}
