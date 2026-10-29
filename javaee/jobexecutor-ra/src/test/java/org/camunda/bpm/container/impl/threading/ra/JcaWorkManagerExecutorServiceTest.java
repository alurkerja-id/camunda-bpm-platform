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
package org.camunda.bpm.container.impl.threading.ra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import javax.naming.Reference;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkException;
import javax.resource.spi.work.WorkManager;
import javax.resource.spi.work.WorkRejectedException;

import org.camunda.bpm.container.impl.threading.ra.inflow.JcaInflowExecuteJobsRunnable;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class JcaWorkManagerExecutorServiceTest {

  protected WorkManager workManager;
  protected JcaExecutorServiceConnector connector;
  protected JcaWorkManagerExecutorService service;

  @Before
  public void setUp() {
    workManager = mock(WorkManager.class);
    connector = new JcaExecutorServiceConnector();
    service = new JcaWorkManagerExecutorService(connector, workManager);
  }

  @Test
  public void shouldScheduleLongRunningWorkAndRunTheRunnable() throws Exception {
    Runnable runnable = mock(Runnable.class);

    assertThat(service.schedule(runnable, true)).isTrue();

    ArgumentCaptor<Work> work = ArgumentCaptor.forClass(Work.class);
    verify(workManager).scheduleWork(work.capture());
    work.getValue().run();
    work.getValue().release();
    verify(runnable).run();
  }

  @Test
  public void shouldStartShortRunningWorkWithTimeout() throws Exception {
    assertThat(service.schedule(mock(Runnable.class), false)).isTrue();

    verify(workManager).startWork(any(Work.class), org.mockito.ArgumentMatchers.eq((long) JcaWorkManagerExecutorService.START_WORK_TIMEOUT),
        isNull(), isNull());
  }

  @Test
  public void shouldReportFailureWhenLongRunningWorkCannotBeScheduled() throws Exception {
    doThrow(new WorkException("no")).when(workManager).scheduleWork(any(Work.class));

    assertThat(service.schedule(mock(Runnable.class), true)).isFalse();
  }

  @Test
  public void shouldReportFailureWhenShortRunningWorkIsRejectedOrFails() throws Exception {
    doThrow(new WorkRejectedException("full")).doThrow(new WorkException("broken"))
        .when(workManager).startWork(any(Work.class), anyLong(), isNull(), isNull());

    assertThat(service.schedule(mock(Runnable.class), false)).isFalse();
    assertThat(service.schedule(mock(Runnable.class), false)).isFalse();
  }

  protected static ProcessEngineImpl engineWithJobExecutor() {
    ProcessEngineImpl engine = mock(ProcessEngineImpl.class);
    ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
    when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
    when(configuration.getJobExecutor()).thenReturn(mock(JobExecutor.class));
    return engine;
  }

  @Test
  public void shouldCreateInflowRunnableAndExposeCollaborators() throws Exception {
    Runnable runnable = service.getExecuteJobsRunnable(Collections.singletonList("job"), engineWithJobExecutor());
    Reference reference = new Reference("type");
    service.setReference(reference);

    assertThat(runnable).isInstanceOf(JcaInflowExecuteJobsRunnable.class);
    assertThat(service.getReference()).isSameAs(reference);
    assertThat(service.getWorkManager()).isSameAs(workManager);
    assertThat(service.getPlatformJobExecutorConnector()).isSameAs(connector);
  }

}
