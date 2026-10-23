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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.jobexecutor.RejectedJobsHandler;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.Before;
import org.junit.Test;

public class ManagedJobExecutorTest {

  private ManagedExecutor executor;
  private ManagedJobExecutor jobExecutor;

  @Before
  public void setUp() {
    executor = mock(ManagedExecutor.class);
    jobExecutor = new ManagedJobExecutor(executor);
  }

  @Test
  public void shouldScheduleAcquireJobsRunnable() {
    jobExecutor.start();

    assertThat(jobExecutor.getAcquireJobsRunnable()).isNotNull();
    verify(executor).execute(jobExecutor.getAcquireJobsRunnable());
  }

  @Test
  public void shouldWrapSchedulingFailure() {
    RuntimeException cause = new IllegalStateException("pool closed");
    doThrow(cause).when(executor).execute(any(Runnable.class));

    assertThatThrownBy(() -> jobExecutor.start())
        .isInstanceOf(ProcessEngineException.class)
        .hasMessage("Could not schedule AcquireJobsRunnable for execution.")
        .hasCause(cause);
  }

  @Test
  public void shouldDoNothingWhenStoppingJobExecution() {
    jobExecutor.start();

    jobExecutor.shutdown();

    assertThat(jobExecutor.isActive()).isFalse();
    verify(executor, times(1)).execute(any(Runnable.class));
  }

  @Test
  public void shouldSubmitExecuteJobsRunnable() {
    List<String> jobIds = Arrays.asList("a", "b");

    jobExecutor.executeJobs(jobIds, mock(ProcessEngineImpl.class, RETURNS_DEEP_STUBS));

    verify(executor).execute(any(Runnable.class));
  }

  @Test
  public void shouldDelegateToRejectedJobsHandlerWhenQueueIsFull() {
    RejectedJobsHandler handler = mock(RejectedJobsHandler.class);
    jobExecutor.setRejectedJobsHandler(handler);
    ProcessEngineImpl engine = mock(ProcessEngineImpl.class, RETURNS_DEEP_STUBS);
    List<String> jobIds = Arrays.asList("a", "b", "c");
    doThrow(new RejectedExecutionException()).when(executor).execute(any(Runnable.class));

    jobExecutor.executeJobs(jobIds, engine);

    verify(handler).jobsRejected(jobIds, engine, jobExecutor);
  }
}
