package org.camunda.commons.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.camunda.commons.testing.util.ExampleProcessEngineLogger;
import org.junit.Rule;
import org.junit.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Covers the combined and filtered log access of {@link ProcessEngineLoggingRule}.
 */
public class ProcessEngineLoggingRuleLogAccessTest {

  private static final String PERSISTENCE_LOGGER = "org.camunda.bpm.engine.persistence";
  private static final String JOB_EXECUTOR_LOGGER = "org.camunda.bpm.engine.jobexecutor";

  @Rule
  public ProcessEngineLoggingRule loggingRule = new ProcessEngineLoggingRule()
      .watch(PERSISTENCE_LOGGER, JOB_EXECUTOR_LOGGER)
      .level(Level.DEBUG);

  @Test
  public void shouldReturnEventsOfAllWatchedLoggersInChronologicalOrder() throws InterruptedException {
    ExampleProcessEngineLogger.PERSISTENCE_LOGGER.info();
    Thread.sleep(5);
    ExampleProcessEngineLogger.JOB_EXECUTOR_LOGGER.warn();
    Thread.sleep(5);
    ExampleProcessEngineLogger.PERSISTENCE_LOGGER.error();

    List<ILoggingEvent> log = loggingRule.getLog();

    assertThat(log).hasSize(3);
    assertThat(log.get(0).getLevel()).isEqualTo(Level.INFO);
    assertThat(log.get(1).getLevel()).isEqualTo(Level.WARN);
    assertThat(log.get(2).getLevel()).isEqualTo(Level.ERROR);
    assertThat(log.get(0).getTimeStamp()).isLessThanOrEqualTo(log.get(1).getTimeStamp());
    assertThat(log.get(1).getTimeStamp()).isLessThanOrEqualTo(log.get(2).getTimeStamp());
  }

  @Test
  public void shouldFilterAcrossAllLoggersBySubstring() {
    ExampleProcessEngineLogger.PERSISTENCE_LOGGER.info();
    ExampleProcessEngineLogger.JOB_EXECUTOR_LOGGER.warn();

    assertThat(loggingRule.getFilteredLog("INFO log")).hasSize(1);
    assertThat(loggingRule.getFilteredLog("WARN log")).hasSize(1);
    assertThat(loggingRule.getFilteredLog("log in component")).hasSize(2);
    assertThat(loggingRule.getFilteredLog("no such text")).isEmpty();
  }

  @Test
  public void shouldFilterSingleLoggerBySubstring() {
    ExampleProcessEngineLogger.PERSISTENCE_LOGGER.info();
    ExampleProcessEngineLogger.PERSISTENCE_LOGGER.error();
    ExampleProcessEngineLogger.JOB_EXECUTOR_LOGGER.error();

    assertThat(loggingRule.getFilteredLog(PERSISTENCE_LOGGER, "ERROR log")).hasSize(1);
    assertThat(loggingRule.getFilteredLog(PERSISTENCE_LOGGER, "log in component")).hasSize(2);
    assertThat(loggingRule.getFilteredLog(JOB_EXECUTOR_LOGGER, "INFO log")).isEmpty();
    assertThatThrownBy(() -> loggingRule.getFilteredLog("unwatched.logger", "x"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage(ProcessEngineLoggingRule.NOT_WATCHING_ERROR + "unwatched.logger");
  }

  @Test
  public void shouldWatchMultipleLoggersWithDefaultLevel() {
    ProcessEngineLoggingRule rule = new ProcessEngineLoggingRule().level(Level.WARN).watch("a.b", "c.d");

    assertThat(rule.globallyWatched).containsOnlyKeys("a.b", "c.d");
    assertThat(rule.globalLevel).isEqualTo(Level.WARN);
  }
}
