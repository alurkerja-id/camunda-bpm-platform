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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.BatchExecutorException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.ProcessEnginePersistenceException;
import org.junit.Test;

/**
 * Covers {@link ExceptionUtil}, which reads a database failure out of the layers MyBatis wraps it
 * in and then decides what kind of failure it was.
 *
 * <p>The classification is per database vendor - the same "foreign key violated" arrives as a
 * different message, SQL state and error code on each one - so the tests name the vendor each case
 * stands for. Getting one of those combinations wrong means the engine mistakes a constraint
 * violation for an unknown error, which is why they are pinned individually.
 */
public class ExceptionUtilTest {

  protected SQLException sqlException(String message, String sqlState, int errorCode) {
    return new SQLException(message, sqlState, errorCode);
  }

  protected PersistenceException persistenceException(Throwable cause) {
    return new PersistenceException("wrapped", cause);
  }

  /**
   * SQLException implements Iterable&lt;Throwable&gt;, so assertThat cannot tell the Throwable
   * overload from the Iterable one without help.
   */
  protected Throwable asThrowable(SQLException sqlException) {
    return sqlException;
  }

  protected BatchExecutorException batchExecutorException(SQLException cause) {
    BatchUpdateException batchUpdateException = new BatchUpdateException(new int[0], cause);
    return new BatchExecutorException("batch failed", batchUpdateException, null, null);
  }

  // unwrapping ---------------------------------------------------------------------------------

  @Test
  public void shouldUnwrapASqlExceptionFromMyBatis() {
    SQLException sqlException = sqlException("boom", "23000", 1);

    assertThat(asThrowable(ExceptionUtil.unwrapException(persistenceException(sqlException))))
        .isSameAs(sqlException);
  }

  /** When the driver chains exceptions, the next one carries the detail worth reading. */
  @Test
  public void shouldPreferTheChainedException() {
    SQLException next = sqlException("real cause", "23000", 1);
    SQLException sqlException = sqlException("boom", "23000", 1);
    sqlException.setNextException(next);

    assertThat(asThrowable(ExceptionUtil.unwrapException(persistenceException(sqlException))))
        .isSameAs(next);
  }

  /**
   * A batch failure is unwrapped one level further: the exception that comes back is the
   * BatchUpdateException the driver raised, not whatever it in turn wraps.
   */
  @Test
  public void shouldReachThroughABatchExecutorException() {
    SQLException sqlException = sqlException("boom", "23000", 1);
    BatchUpdateException batchUpdateException = new BatchUpdateException(new int[0], sqlException);
    BatchExecutorException batchException =
        new BatchExecutorException("batch failed", batchUpdateException, null, null);

    assertThat(asThrowable(ExceptionUtil.unwrapException(persistenceException(batchException))))
        .isSameAs(batchUpdateException);
  }

  @Test
  public void shouldAnswerNullWhenThereIsNoSqlExceptionInside() {
    assertThat(asThrowable(ExceptionUtil.unwrapException(
        persistenceException(new RuntimeException("boom"))))).isNull();
  }

  @Test
  public void shouldUnwrapThroughTheEngineException() {
    SQLException sqlException = sqlException("boom", "23000", 1);

    ProcessEngineException nested = new ProcessEngineException("outer",
        new ProcessEngineException("inner", persistenceException(sqlException)));
    ProcessEngineException direct =
        new ProcessEngineException("outer", persistenceException(sqlException));

    assertThat(asThrowable(ExceptionUtil.unwrapException(nested))).isSameAs(sqlException);
    assertThat(asThrowable(ExceptionUtil.unwrapException(direct))).isSameAs(sqlException);
  }

  @Test
  public void shouldAnswerNullForAnEngineExceptionThatHoldsSomethingElse() {
    assertThat(asThrowable(ExceptionUtil.unwrapException(
        new ProcessEngineException("outer", new RuntimeException("boom"))))).isNull();
    assertThat(asThrowable(ExceptionUtil.unwrapException(
        new ProcessEngineException("outer", new ProcessEngineException("inner"))))).isNull();
  }

  // value too long -----------------------------------------------------------------------------------

  @Test
  public void shouldRecogniseAValueTooLongPerVendor() {
    assertThat(ExceptionUtil.checkValueTooLongException(
        sqlException("Value too long for column", null, 0))).isTrue();
    assertThat(ExceptionUtil.checkValueTooLongException(
        sqlException("data too large", null, 0))).isTrue();
    assertThat(ExceptionUtil.checkValueTooLongException(
        sqlException("ORA-01461: cannot bind", null, 0))).isTrue();
    assertThat(ExceptionUtil.checkValueTooLongException(
        sqlException("ORA-01401: inserted value", null, 0))).isTrue();
    assertThat(ExceptionUtil.checkValueTooLongException(
        sqlException("data would be truncated", null, 0))).isTrue();
    assertThat(ExceptionUtil.checkValueTooLongException(
        sqlException("SQLCODE=-302, SQLSTATE=22001", null, 0))).isTrue();
  }

  @Test
  public void shouldNotMistakeAnotherFailureForValueTooLong() {
    assertThat(ExceptionUtil.checkValueTooLongException(sqlException("boom", null, 0))).isFalse();
    assertThat(ExceptionUtil.checkValueTooLongException(sqlException(null, null, 0))).isFalse();
  }

  @Test
  public void shouldCheckValueTooLongThroughAnEngineException() {
    ProcessEngineException tooLong = new ProcessEngineException("outer",
        persistenceException(sqlException("value too long", null, 0)));

    assertThat(ExceptionUtil.checkValueTooLongException(tooLong)).isTrue();
    assertThat(ExceptionUtil.checkValueTooLongException(
        new ProcessEngineException("outer", new RuntimeException()))).isFalse();
  }

  // constraint violations --------------------------------------------------------------------------------

  @Test
  public void shouldRecogniseAConstraintViolation() {
    assertThat(ExceptionUtil.checkConstraintViolationException(engineExceptionFor(
        sqlException("constraint failed", null, 0)))).isTrue();
    assertThat(ExceptionUtil.checkConstraintViolationException(engineExceptionFor(
        sqlException("unique violation", null, 0)))).isTrue();
    assertThat(ExceptionUtil.checkConstraintViolationException(engineExceptionFor(
        sqlException("duplicate key", null, 0)))).isTrue();
    assertThat(ExceptionUtil.checkConstraintViolationException(engineExceptionFor(
        sqlException("ORA-00001: unique", null, 0)))).isTrue();
    assertThat(ExceptionUtil.checkConstraintViolationException(engineExceptionFor(
        sqlException("SQLCODE=-803, SQLSTATE=23505", null, 0)))).isTrue();
  }

  @Test
  public void shouldNotMistakeAnotherFailureForAConstraintViolation() {
    assertThat(ExceptionUtil.checkConstraintViolationException(
        engineExceptionFor(sqlException("boom", null, 0)))).isFalse();
    assertThat(ExceptionUtil.checkConstraintViolationException(
        engineExceptionFor(sqlException(null, null, 0)))).isFalse();
    assertThat(ExceptionUtil.checkConstraintViolationException(
        new ProcessEngineException("outer", new RuntimeException()))).isFalse();
  }

  /** Each line stands for one database: the same violation looks different on every vendor. */
  @Test
  public void shouldRecogniseAForeignKeyViolationPerVendor() {
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        sqlException("foreign key constraint", null, 0))).isTrue();
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        sqlException("boom", "23000", 547))).isTrue();
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        sqlException("boom", "23000", 1452))).isTrue();
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        sqlException("integrity constraint", null, 0))).isTrue();
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        sqlException("boom", "23000", 2291))).isTrue();
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        sqlException("boom", "23506", 23506))).isTrue();
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        sqlException("boom", "23503", -530))).isTrue();
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        sqlException("boom", "23504", -532))).isTrue();
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        sqlException("boom", "23503", 0))).isTrue();
  }

  @Test
  public void shouldNotMistakeAnotherFailureForAForeignKeyViolation() {
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        sqlException("boom", "42000", 1))).isFalse();
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        sqlException(null, "23000", 547))).isFalse();
  }

  @Test
  public void shouldCheckForeignKeyThroughAPersistenceException() {
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        persistenceException(sqlException("foreign key constraint", null, 0)))).isTrue();
    assertThat(ExceptionUtil.checkForeignKeyConstraintViolation(
        persistenceException(new RuntimeException()))).isFalse();
  }

  @Test
  public void shouldRecogniseTheVariableUniquenessViolationPerVendor() {
    assertThat(ExceptionUtil.checkVariableIntegrityViolation(persistenceException(
        sqlException("ACT_UNIQ_VARIABLE", "23000", 1062)))).isTrue();
    assertThat(ExceptionUtil.checkVariableIntegrityViolation(persistenceException(
        sqlException("ACT_UNIQ_VARIABLE", "23505", 0)))).isTrue();
    assertThat(ExceptionUtil.checkVariableIntegrityViolation(persistenceException(
        sqlException("ACT_UNIQ_VARIABLE", "23000", 2601)))).isTrue();
    assertThat(ExceptionUtil.checkVariableIntegrityViolation(persistenceException(
        sqlException("ACT_UNIQ_VARIABLE", "23000", 1)))).isTrue();
    assertThat(ExceptionUtil.checkVariableIntegrityViolation(persistenceException(
        sqlException("ACT_UNIQ_VARIABLE", "23505", 23505)))).isTrue();
  }

  @Test
  public void shouldNotMistakeAnotherFailureForTheVariableUniquenessViolation() {
    assertThat(ExceptionUtil.checkVariableIntegrityViolation(persistenceException(
        sqlException("some other constraint", "23000", 1062)))).isFalse();
    assertThat(ExceptionUtil.checkVariableIntegrityViolation(persistenceException(
        sqlException(null, "23000", 1062)))).isFalse();
    assertThat(ExceptionUtil.checkVariableIntegrityViolation(persistenceException(
        new RuntimeException()))).isFalse();
  }

  // deadlocks -----------------------------------------------------------------------------------------

  @Test
  public void shouldRecogniseADeadlockPerVendor() {
    assertThat(ExceptionUtil.checkDeadlockException(sqlException("boom", "40001", 1213))).isTrue();
    assertThat(ExceptionUtil.checkDeadlockException(sqlException("boom", "40001", 1205))).isTrue();
    assertThat(ExceptionUtil.checkDeadlockException(sqlException("boom", "40001", -911))).isTrue();
    assertThat(ExceptionUtil.checkDeadlockException(sqlException("boom", "61000", 60))).isTrue();
    assertThat(ExceptionUtil.checkDeadlockException(sqlException("boom", "40P01", 0))).isTrue();
    assertThat(ExceptionUtil.checkDeadlockException(sqlException("boom", "40001", 40001))).isTrue();
  }

  @Test
  public void shouldNotMistakeAnotherFailureForADeadlock() {
    assertThat(ExceptionUtil.checkDeadlockException(sqlException("boom", "23000", 1))).isFalse();
    assertThat(ExceptionUtil.checkDeadlockException(sqlException("boom", null, 1213))).isFalse();
  }

  @Test
  public void shouldExposeTheDeadlockCodesPerVendor() {
    assertThat(ExceptionUtil.DEADLOCK_CODES.MYSQL.getErrorCode()).isEqualTo(1213);
    assertThat(ExceptionUtil.DEADLOCK_CODES.MYSQL.getSqlState()).isEqualTo("40001");
    assertThat(ExceptionUtil.DEADLOCK_CODES.POSTGRES.getSqlState()).isEqualTo("40P01");
  }

  // batch executor -----------------------------------------------------------------------------------------

  @Test
  public void shouldFindABatchExecutorExceptionAtAnyDepth() {
    BatchExecutorException batchException = batchExecutorException(new SQLException());

    assertThat(ExceptionUtil.findBatchExecutorException(
        persistenceException(batchException))).isSameAs(batchException);
    assertThat(ExceptionUtil.findBatchExecutorException(
        persistenceException(new RuntimeException("boom", batchException))))
        .isSameAs(batchException);
  }

  @Test
  public void shouldAnswerNullWhenThereIsNoBatchExecutorException() {
    assertThat(ExceptionUtil.findBatchExecutorException(
        persistenceException(new RuntimeException("boom")))).isNull();
  }

  // the wrapper -----------------------------------------------------------------------------------------------

  @Test
  public void shouldPassTheResultThroughWhenNothingFails() {
    assertThat(ExceptionUtil.doWithExceptionWrapper(() -> "done")).isEqualTo("done");
  }

  /** SQL detail must never reach an API response, so every failure comes back generic. */
  @Test
  public void shouldWrapAnyFailureInAGenericEngineException() {
    assertThatThrownBy(() -> ExceptionUtil.doWithExceptionWrapper(() -> {
      throw new IllegalStateException("SELECT secret FROM table");
    })).isInstanceOf(ProcessEnginePersistenceException.class)
        .hasMessage(ExceptionUtil.PERSISTENCE_EXCEPTION_MESSAGE)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldWrapAnExceptionOnDemand() {
    ProcessEnginePersistenceException wrapped =
        ExceptionUtil.wrapPersistenceException(new IllegalStateException("boom"));

    assertThat(wrapped.getMessage()).isEqualTo(ExceptionUtil.PERSISTENCE_EXCEPTION_MESSAGE);
    assertThat(wrapped.getCause()).isInstanceOf(IllegalStateException.class);
  }

  // stack traces ---------------------------------------------------------------------------------------------------

  @Test
  public void shouldRenderAStackTrace() {
    String stacktrace = ExceptionUtil.getExceptionStacktrace(new RuntimeException("boom"));

    assertThat(stacktrace).contains("java.lang.RuntimeException: boom");
    assertThat(stacktrace).contains(getClass().getName());
  }

  protected ProcessEngineException engineExceptionFor(SQLException sqlException) {
    return new ProcessEngineException("outer", persistenceException(sqlException));
  }
}
