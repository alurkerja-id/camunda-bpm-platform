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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import javax.enterprise.concurrent.ManagedExecutorService;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.jobexecutor.RejectedJobsHandler;
import org.junit.Test;

public class ManagedJobExecutorUnitTest {

  private final ManagedExecutorService executorService = mock(ManagedExecutorService.class);
  private final ManagedJobExecutor jobExecutor = new ManagedJobExecutor(executorService);
  private final ProcessEngineImpl processEngine = mock(ProcessEngineImpl.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

  @Test
  public void executeJobsSubmitsRunnableToManagedExecutor() {
    jobExecutor.executeJobs(Arrays.asList("j1", "j2"), processEngine);

    verify(executorService).execute(any(Runnable.class));
  }

  @Test
  public void executeJobsDelegatesToRejectedHandlerWhenExecutorRejects() {
    RejectedJobsHandler handler = mock(RejectedJobsHandler.class);
    jobExecutor.setRejectedJobsHandler(handler);
    doThrow(new RejectedExecutionException("full")).when(executorService).execute(any(Runnable.class));
    List<String> ids = Arrays.asList("j1", "j2");

    jobExecutor.executeJobs(ids, processEngine);

    verify(handler).jobsRejected(ids, processEngine, jobExecutor);
  }

  @org.junit.Before
  public void installAcquireJobsRunnable() throws Exception {
    java.lang.reflect.Field field = org.camunda.bpm.engine.impl.jobexecutor.JobExecutor.class.getDeclaredField("acquireJobsRunnable");
    field.setAccessible(true);
    field.set(jobExecutor, mock(org.camunda.bpm.engine.impl.jobexecutor.AcquireJobsRunnable.class));
  }

  @Test
  public void startExecutingJobsSchedulesAcquisition() {
    jobExecutor.startExecutingJobs();

    verify(executorService).execute(any(Runnable.class));
  }

  @Test
  public void startExecutingJobsWrapsSchedulingFailure() {
    doThrow(new IllegalStateException("down")).when(executorService).execute(any(Runnable.class));

    assertThatThrownBy(jobExecutor::startExecutingJobs)
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("AcquireJobsRunnable")
      .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void stopExecutingJobsDoesNotTouchExecutor() {
    jobExecutor.stopExecutingJobs();

    org.mockito.Mockito.verifyNoInteractions(executorService);
  }
}
