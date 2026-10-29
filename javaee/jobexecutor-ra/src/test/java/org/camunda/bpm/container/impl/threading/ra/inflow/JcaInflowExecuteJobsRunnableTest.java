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
package org.camunda.bpm.container.impl.threading.ra.inflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import javax.resource.ResourceException;
import javax.resource.spi.UnavailableException;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;

import org.camunda.bpm.container.impl.threading.ra.JcaExecutorServiceConnector;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.impl.jobexecutor.JobFailureCollector;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public class JcaInflowExecuteJobsRunnableTest {

  /** an endpoint that is both the delivery hook and the handler, like the proxy the container hands out */
  public interface HandlerEndpoint extends MessageEndpoint, JobExecutionHandler {
  }

  protected ProcessEngineImpl engine;
  protected JcaExecutorServiceConnector connector;
  protected MessageEndpointFactory factory;
  protected HandlerEndpoint endpoint;
  protected CommandExecutor commandExecutor;
  protected JobFailureCollector collector;
  protected JcaInflowExecuteJobsRunnable runnable;

  @Before
  public void setUp() throws Exception {
    engine = mock(ProcessEngineImpl.class);
    ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
    when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
    when(configuration.getJobExecutor()).thenReturn(mock(JobExecutor.class));
    connector = new JcaExecutorServiceConnector();
    factory = mock(MessageEndpointFactory.class);
    endpoint = mock(HandlerEndpoint.class);
    when(factory.createEndpoint(null)).thenReturn(endpoint);
    commandExecutor = mock(CommandExecutor.class);
    collector = mock(JobFailureCollector.class);
    runnable = new JcaInflowExecuteJobsRunnable(Collections.singletonList("job-1"), engine, connector);
  }

  protected void activate() throws Exception {
    connector.endpointActivation(factory, new JobExecutionHandlerActivationSpec());
  }

  @Test
  public void shouldSkipExecutionWithoutHandlerActivation() throws Exception {
    runnable.executeJob("job-1", commandExecutor, collector);

    verify(factory, never()).createEndpoint(any());
  }

  @Test
  public void shouldDeliverJobThroughEndpointLifecycle() throws Exception {
    activate();

    runnable.executeJob("job-1", commandExecutor, collector);

    InOrder order = inOrder(endpoint);
    order.verify(endpoint).beforeDelivery(any());
    order.verify(endpoint).executeJob("job-1", commandExecutor);
    order.verify(endpoint).afterDelivery();
    order.verify(endpoint).release();
  }

  @Test
  public void shouldStillExecuteWhenBeforeDeliveryFails() throws Exception {
    activate();
    doThrow(new NoSuchMethodException("m")).doThrow(new ResourceException("r")).when(endpoint).beforeDelivery(any());

    runnable.executeJob("job-1", commandExecutor, collector);
    runnable.executeJob("job-2", commandExecutor, collector);

    verify(endpoint).executeJob("job-1", commandExecutor);
    verify(endpoint).executeJob("job-2", commandExecutor);
    verify(endpoint, org.mockito.Mockito.times(2)).release();
  }

  @Test
  public void shouldReleaseEndpointWhenJobExecutionOrAfterDeliveryFails() throws Exception {
    activate();
    when(endpoint.executeJob("job-1", commandExecutor)).thenThrow(new IllegalStateException("job failed"));
    doThrow(new ResourceException("after")).when(endpoint).afterDelivery();

    runnable.executeJob("job-1", commandExecutor, collector);

    verify(endpoint).afterDelivery();
    verify(endpoint).release();
  }

  @Test
  public void shouldSurviveUnavailableEndpoint() throws Exception {
    activate();
    when(factory.createEndpoint(null)).thenThrow(new UnavailableException("down"));

    runnable.executeJob("job-1", commandExecutor, collector);

    verify(endpoint, never()).release();
  }

  @Test
  public void shouldUseContextClassLoader() {
    assertThat(runnable.switchClassLoader()).isSameAs(Thread.currentThread().getContextClassLoader());
  }

}
