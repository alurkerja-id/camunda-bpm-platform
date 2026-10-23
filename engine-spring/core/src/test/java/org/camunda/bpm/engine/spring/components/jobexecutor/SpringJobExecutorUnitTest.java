package org.camunda.bpm.engine.spring.components.jobexecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.jobexecutor.RejectedJobsHandler;
import org.junit.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Covers {@link SpringJobExecutor}.
 */
public class SpringJobExecutorUnitTest {

  protected List<String> jobIds = Arrays.asList("1", "2");
  protected ProcessEngineImpl engine = mock(ProcessEngineImpl.class);

  protected void bind(SpringJobExecutor executor) {
    ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
    when(configuration.getJobExecutor()).thenReturn(executor);
    when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
  }

  @Test
  public void shouldHandOverJobsToTaskExecutor() {
    TaskExecutor taskExecutor = mock(TaskExecutor.class);
    SpringJobExecutor executor = new SpringJobExecutor();
    executor.setTaskExecutor(taskExecutor);
    bind(executor);

    executor.executeJobs(jobIds, engine);

    assertThat(executor.getTaskExecutor()).isSameAs(taskExecutor);
    verify(taskExecutor).execute(any(Runnable.class));
  }

  @Test
  public void shouldDelegateRejectedJobsToHandler() {
    TaskExecutor taskExecutor = mock(TaskExecutor.class);
    doThrow(new RejectedExecutionException("full")).when(taskExecutor).execute(any(Runnable.class));
    RejectedJobsHandler handler = mock(RejectedJobsHandler.class);
    SpringJobExecutor executor = new SpringJobExecutor();
    executor.setTaskExecutor(taskExecutor);
    bind(executor);
    executor.setRejectedJobsHandler(handler);

    executor.executeJobs(jobIds, engine);

    verify(handler).jobsRejected(jobIds, engine, executor);
  }

  @Test
  public void shouldReportPoolStateForThreadPoolTaskExecutor() {
    ThreadPoolTaskExecutor taskExecutor = mock(ThreadPoolTaskExecutor.class);
    SpringJobExecutor executor = new SpringJobExecutor();
    executor.setTaskExecutor(taskExecutor);
    bind(executor);

    executor.executeJobs(jobIds, engine);

    verify(taskExecutor).execute(any(Runnable.class));
    verify(taskExecutor).getQueueSize();
    verify(taskExecutor).getQueueCapacity();
    verify(taskExecutor).getMaxPoolSize();
    verify(taskExecutor).getActiveCount();
  }

  @Test
  public void shouldStartAndStopAcquisitionThread() {
    SpringJobExecutor executor = new SpringJobExecutor();
    executor.setTaskExecutor(mock(TaskExecutor.class));

    executor.startExecutingJobs();
    assertThat(org.springframework.test.util.ReflectionTestUtils.getField(executor, "jobAcquisitionThread")).isNotNull();
    executor.stopExecutingJobs();
  }
}
