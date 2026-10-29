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
package org.camunda.bpm.engine.impl.jobexecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.junit.Test;

public class NotifyAcquisitionRejectedJobsHandlerTest {

  private final NotifyAcquisitionRejectedJobsHandler handler = new NotifyAcquisitionRejectedJobsHandler();
  private final List<String> jobIds = Arrays.asList("a", "b");

  @Test
  public void shouldSubmitRejectedBatchToSequentialAcquisition() {
    ProcessEngineImpl engine = mock(ProcessEngineImpl.class);
    when(engine.getName()).thenReturn("engine-1");
    JobExecutor jobExecutor = mock(JobExecutor.class);
    SequentialJobAcquisitionRunnable runnable = mock(SequentialJobAcquisitionRunnable.class);
    JobAcquisitionContext context = mock(JobAcquisitionContext.class);
    when(jobExecutor.getAcquireJobsRunnable()).thenReturn(runnable);
    when(runnable.getAcquisitionContext()).thenReturn(context);

    handler.jobsRejected(jobIds, engine, jobExecutor);

    verify(context).submitRejectedBatch("engine-1", jobIds);
    verify(jobExecutor, never()).getExecuteJobsRunnable(any(), any());
  }

  @Test
  public void shouldRunJobsDirectlyForOtherAcquisitionStrategies() {
    ProcessEngineImpl engine = mock(ProcessEngineImpl.class);
    JobExecutor jobExecutor = mock(JobExecutor.class);
    when(jobExecutor.getAcquireJobsRunnable()).thenReturn(mock(AcquireJobsRunnable.class));
    Runnable executeJobs = mock(Runnable.class);
    when(jobExecutor.getExecuteJobsRunnable(eq(jobIds), eq(engine))).thenReturn(executeJobs);

    handler.jobsRejected(jobIds, engine, jobExecutor);

    verify(executeJobs).run();
  }
}
