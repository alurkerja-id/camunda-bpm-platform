package org.camunda.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * Covers level dispatch and enabled-checks of {@link BaseLogger}, {@link Level} and {@link MdcAccess}.
 */
public class BaseLoggerLevelsTest {

  protected Logger delegate;
  protected ExampleLogger logger;

  @Before
  public void setUp() {
    delegate = mock(Logger.class);
    logger = BaseLogger.createLogger(ExampleLogger.class, "TEST", "some.logger", "01");
    logger.delegateLogger = delegate;
  }

  protected void enableAll(boolean enabled) {
    when(delegate.isTraceEnabled()).thenReturn(enabled);
    when(delegate.isDebugEnabled()).thenReturn(enabled);
    when(delegate.isInfoEnabled()).thenReturn(enabled);
    when(delegate.isWarnEnabled()).thenReturn(enabled);
    when(delegate.isErrorEnabled()).thenReturn(enabled);
  }

  @Test
  public void shouldDispatchToMatchingLevelWithFormattedMessage() {
    enableAll(true);

    logger.log("ERROR", "10", "msg {}", "a");
    logger.log("WARN", "11", "msg {}", "a");
    logger.log("INFO", "12", "msg {}", "a");
    logger.log("DEBUG", "13", "msg {}", "a");
    logger.log("TRACE", "14", "msg {}", "a");

    verify(delegate).error("TEST-0110 msg {}", new Object[] {"a"});
    verify(delegate).warn("TEST-0111 msg {}", new Object[] {"a"});
    verify(delegate).info("TEST-0112 msg {}", new Object[] {"a"});
    verify(delegate).debug("TEST-0113 msg {}", new Object[] {"a"});
    verify(delegate).trace("TEST-0114 msg {}", new Object[] {"a"});
  }

  @Test
  public void shouldFallBackToDefaultLevel() {
    enableAll(true);

    logger.log(null, "20", "m");
    logger.log("bogus", Level.WARN, "21", "m");

    verify(delegate).debug("TEST-0120 m", new Object[0]);
    verify(delegate).warn("TEST-0121 m", new Object[0]);
  }

  @Test
  public void shouldNotLogWhenLevelDisabled() {
    enableAll(false);

    logger.logTrace("1", "m");
    logger.logDebug("1", "m");
    logger.logInfo("1", "m");
    logger.logWarn("1", "m");
    logger.logError("1", "m");

    verify(delegate, never()).trace(anyString(), any(Object[].class));
    verify(delegate, never()).debug(anyString(), any(Object[].class));
    verify(delegate, never()).info(anyString(), any(Object[].class));
    verify(delegate, never()).warn(anyString(), any(Object[].class));
    verify(delegate, never()).error(anyString(), any(Object[].class));
  }

  @Test
  public void shouldDelegateEnabledChecks() {
    when(delegate.isDebugEnabled()).thenReturn(true);
    when(delegate.isInfoEnabled()).thenReturn(false);
    when(delegate.isWarnEnabled()).thenReturn(true);
    when(delegate.isErrorEnabled()).thenReturn(false);

    assertThat(logger.isDebugEnabled()).isTrue();
    assertThat(logger.isInfoEnabled()).isFalse();
    assertThat(logger.isWarnEnabled()).isTrue();
    assertThat(logger.isErrorEnabled()).isFalse();
  }

  @Test
  public void shouldFailToCreateLoggerWithoutAccessibleConstructor() {
    assertThatThrownBy(() -> BaseLogger.createLogger(NoDefaultConstructorLogger.class, "T", "n", "01"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unable to instantiate logger");
  }

  @Test
  public void levelParseIsCaseAndWhitespaceInsensitive() {
    assertThat(Level.parse(" info ", Level.ERROR)).isEqualTo(Level.INFO);
    assertThat(Level.parse("trace", Level.ERROR)).isEqualTo(Level.TRACE);
    assertThat(Level.parse(null, Level.ERROR)).isEqualTo(Level.ERROR);
    assertThat(Level.parse("nope", Level.WARN)).isEqualTo(Level.WARN);
    assertThat(Level.DEBUG.getValue()).isEqualTo("DEBUG");
  }

  @Test
  public void mdcAccessPutGetRemove() {
    MdcAccess.put("k", "v");
    assertThat(MdcAccess.get("k")).isEqualTo("v");
    MdcAccess.remove("k");
    assertThat(MdcAccess.get("k")).isNull();
  }

  static class NoDefaultConstructorLogger extends BaseLogger {
    NoDefaultConstructorLogger(String unused) {
    }
  }
}
