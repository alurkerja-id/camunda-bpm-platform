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
package org.camunda.bpm.webapp.impl.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.JobQuery;
import org.junit.Test;

public class JobExecutorHelperTest {

  protected JobExecutor jobExecutor = mock(JobExecutor.class);
  protected ManagementService managementService = mock(ManagementService.class);
  protected JobQuery jobQuery = mock(JobQuery.class);
  protected JobExecutorHelper helper;

  protected void init(Throwable... queryFailures) {
    ProcessEngineImpl engine = mock(ProcessEngineImpl.class);
    ProcessEngineConfigurationImpl config = mock(ProcessEngineConfigurationImpl.class);
    when(engine.getProcessEngineConfiguration()).thenReturn(config);
    when(config.getJobExecutor()).thenReturn(jobExecutor);
    when(engine.getManagementService()).thenReturn(managementService);
    when(managementService.createJobQuery()).thenReturn(jobQuery);
    org.mockito.stubbing.OngoingStubbing<java.util.List<Job>> stub = when(jobQuery.list());
    for (Throwable t : queryFailures) {
      stub = stub.thenThrow(t);
    }
    stub.thenReturn(Collections.<Job>emptyList());
    helper = new JobExecutorHelper(engine);
  }

  @Test
  public void shouldIgnoreExceptionWhileQueryingJobsAndRetry() {
    // e.g. MSSQL table lock: query fails once, then succeeds with no jobs left
    init(new IllegalStateException("table locked"));

    helper.waitForJobExecutorToProcessAllJobs(10_000);

    assertThat(helper.areJobsAvailable()).isFalse();
  }

  @Test
  public void shouldNotSwallowErrors() {
    init(new AssertionError("fatal"));

    assertThatThrownBy(() -> helper.waitForJobExecutorToProcessAllJobs(10_000))
      .isInstanceOf(AssertionError.class);
  }
}
