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
package org.camunda.bpm.engine.impl.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.camunda.bpm.engine.impl.util.LogUtil.LogFormatter;
import org.camunda.bpm.engine.impl.util.LogUtil.ThreadLogMode;
import org.junit.After;
import org.junit.Test;

/**
 * Covers {@link LogUtil}, the java.util.logging formatter the engine still carries.
 *
 * <p>The formatter maps each log level to a three letter tag and decides how threads are marked,
 * and the thread marking is global state - so every test puts the mode back afterwards.
 */
public class LogUtilTest {

  protected final LogFormatter formatter = new LogFormatter();

  @After
  public void resetGlobalState() {
    LogUtil.setThreadLogMode(ThreadLogMode.NONE);
    LogUtil.resetThreadIndents();
  }

  protected LogRecord record(Level level, String message) {
    LogRecord logRecord = new LogRecord(level, message);
    logRecord.setLoggerName("test.logger");
    return logRecord;
  }

  // level tags -----------------------------------------------------------------------------------

  @Test
  public void shouldTagEveryLevelWithItsOwnAbbreviation() {
    assertThat(formatter.format(record(Level.FINE, "m"))).contains(" FIN ");
    assertThat(formatter.format(record(Level.FINEST, "m"))).contains(" FST ");
    assertThat(formatter.format(record(Level.INFO, "m"))).contains(" INF ");
    assertThat(formatter.format(record(Level.SEVERE, "m"))).contains(" SEV ");
    assertThat(formatter.format(record(Level.WARNING, "m"))).contains(" WRN ");
    assertThat(formatter.format(record(Level.FINER, "m"))).contains(" FNR ");
    assertThat(formatter.format(record(Level.CONFIG, "m"))).contains(" CFG ");
  }

  @Test
  public void shouldLeaveAnUnknownLevelUntagged() {
    String line = formatter.format(record(Level.OFF, "m"));

    assertThat(line).doesNotContain(" INF ").doesNotContain(" SEV ");
  }

  // the line itself --------------------------------------------------------------------------------

  @Test
  public void shouldRenderMessageLoggerAndTimestamp() {
    String line = formatter.format(record(Level.INFO, "something happened"));

    assertThat(line).contains("something happened");
    assertThat(line).contains("[test.logger]");
    assertThat(line).matches("(?s)^\\d\\d:\\d\\d:\\d\\d,\\d\\d\\d.*");
    assertThat(line).endsWith(System.getProperty("line.separator"));
  }

  @Test
  public void shouldAppendTheStackTraceWhenThereIsOne() {
    LogRecord logRecord = record(Level.SEVERE, "it broke");
    logRecord.setThrown(new IllegalStateException("boom"));

    String line = formatter.format(logRecord);

    assertThat(line).contains("java.lang.IllegalStateException: boom");
    assertThat(line).contains(getClass().getName());
  }

  @Test
  public void shouldLeaveOutTheStackTraceWhenThereIsNone() {
    assertThat(formatter.format(record(Level.INFO, "fine"))).doesNotContain("\tat ");
  }

  // thread marking ------------------------------------------------------------------------------------

  @Test
  public void shouldMarkNoThreadByDefault() {
    assertThat(LogUtil.getThreadLogMode()).isEqualTo(ThreadLogMode.NONE);
    assertThat(LogFormatter.getThreadIndent(7)).isEmpty();
  }

  @Test
  public void shouldPrintTheThreadIdWhenAsked() {
    LogUtil.setThreadLogMode(ThreadLogMode.PRINT_ID);

    assertThat(LogFormatter.getThreadIndent(7)).isEqualTo("7");
  }

  /** In indent mode each new thread gets two more spaces than the one before it. */
  @Test
  public void shouldIndentOneStepPerThread() {
    LogUtil.setThreadLogMode(ThreadLogMode.INDENT);

    assertThat(LogFormatter.getThreadIndent(1)).isEmpty();
    assertThat(LogFormatter.getThreadIndent(2)).isEqualTo("  ");
    assertThat(LogFormatter.getThreadIndent(3)).isEqualTo("    ");
  }

  @Test
  public void shouldKeepTheIndentAThreadAlreadyHas() {
    LogUtil.setThreadLogMode(ThreadLogMode.INDENT);
    LogFormatter.getThreadIndent(1);
    LogFormatter.getThreadIndent(2);

    assertThat(LogFormatter.getThreadIndent(1)).isEmpty();
  }

  @Test
  public void shouldForgetTheIndentsOnReset() {
    LogUtil.setThreadLogMode(ThreadLogMode.INDENT);
    LogFormatter.getThreadIndent(1);
    LogFormatter.getThreadIndent(2);

    LogUtil.resetThreadIndents();

    assertThat(LogFormatter.getThreadIndent(2)).isEmpty();
  }

  @Test
  public void shouldAnswerThePreviousModeWhenChangingIt() {
    ThreadLogMode previous = LogUtil.setThreadLogMode(ThreadLogMode.PRINT_ID);

    assertThat(previous).isEqualTo(ThreadLogMode.NONE);
    assertThat(LogUtil.setThreadLogMode(ThreadLogMode.INDENT))
        .isEqualTo(ThreadLogMode.PRINT_ID);
  }

  @Test
  public void shouldReadLoggingConfigurationWithoutFailing() {
    LogUtil.readJavaUtilLoggingConfigFromClasspath();
  }
}
