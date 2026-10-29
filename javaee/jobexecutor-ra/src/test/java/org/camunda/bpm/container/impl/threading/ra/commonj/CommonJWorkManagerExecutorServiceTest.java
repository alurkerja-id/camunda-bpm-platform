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
package org.camunda.bpm.container.impl.threading.ra.commonj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import commonj.work.Work;
import commonj.work.WorkException;
import commonj.work.WorkManager;
import commonj.work.WorkRejectedException;

import org.camunda.bpm.container.impl.threading.ra.JcaExecutorServiceConnector;
import org.camunda.bpm.container.impl.threading.ra.inflow.JcaInflowExecuteJobsRunnable;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class CommonJWorkManagerExecutorServiceTest {

  protected WorkManager workManager;
  protected CommonJWorkManagerExecutorService service;

  @Before
  public void setUp() {
    workManager = mock(WorkManager.class);
    service = new CommonJWorkManagerExecutorService(new JcaExecutorServiceConnector(), "wm/test") {
      @Override
      protected WorkManager lookupWorkMananger() {
        return CommonJWorkManagerExecutorServiceTest.this.workManager;
      }
    };
  }

  @Test
  public void shouldScheduleShortRunningWorkAsNonDaemon() throws Exception {
    service.workManager = workManager;
    Runnable runnable = mock(Runnable.class);

    assertThat(service.schedule(runnable, false)).isTrue();

    ArgumentCaptor<Work> work = ArgumentCaptor.forClass(Work.class);
    verify(workManager).schedule(work.capture());
    assertThat(work.getValue().isDaemon()).isFalse();
    work.getValue().run();
    work.getValue().release();
    verify(runnable).run();
  }

  @Test
  public void shouldLookUpWorkManagerAndScheduleLongRunningWorkAsDaemon() throws Exception {
    Runnable runnable = mock(Runnable.class);

    assertThat(service.getWorkManager()).isNull();
    assertThat(service.schedule(runnable, true)).isTrue();

    ArgumentCaptor<Work> work = ArgumentCaptor.forClass(Work.class);
    verify(workManager).schedule(work.capture());
    assertThat(service.getWorkManager()).isSameAs(workManager);
    assertThat(work.getValue().isDaemon()).isTrue();
    work.getValue().run();
    work.getValue().release();
    verify(runnable).run();
  }

  @Test
  public void shouldReportFailureWhenWorkIsRejectedOrFails() throws Exception {
    service.workManager = workManager;
    doThrow(new WorkRejectedException("full")).doThrow(new WorkException("broken")).doThrow(new WorkException("broken"))
        .when(workManager).schedule(any(Work.class));

    assertThat(service.schedule(mock(Runnable.class), false)).isFalse();
    assertThat(service.schedule(mock(Runnable.class), false)).isFalse();
    assertThat(service.schedule(mock(Runnable.class), true)).isFalse();
  }

  @Test
  public void shouldFailWhenJndiLookupIsNotPossible() {
    CommonJWorkManagerExecutorService unresolvable =
        new CommonJWorkManagerExecutorService(new JcaExecutorServiceConnector(), "wm/missing");

    assertThatThrownBy(() -> unresolvable.schedule(mock(Runnable.class), true)).isInstanceOf(RuntimeException.class)
        .hasMessageContaining("could not look up CommonJ WorkManager");
  }

  protected static ProcessEngineImpl engineWithJobExecutor() {
    ProcessEngineImpl engine = mock(ProcessEngineImpl.class);
    ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
    when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
    when(configuration.getJobExecutor()).thenReturn(mock(JobExecutor.class));
    return engine;
  }

  @Test
  public void shouldCreateInflowRunnable() {
    Runnable runnable = service.getExecuteJobsRunnable(Collections.singletonList("job"), engineWithJobExecutor());

    assertThat(runnable).isInstanceOf(JcaInflowExecuteJobsRunnable.class);
  }

}
