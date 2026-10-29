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
package org.camunda.bpm.engine.impl.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.UndeclaredThrowableException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import org.junit.Before;
import org.junit.Test;

public class JtaTransactionInterceptorTest {

  private TransactionManager txManager;
  private CommandInterceptor next;
  private Command<String> command;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() throws Exception {
    txManager = mock(TransactionManager.class);
    next = mock(CommandInterceptor.class);
    command = mock(Command.class);
    when(txManager.getStatus()).thenReturn(Status.STATUS_NO_TRANSACTION);
    when(next.execute(command)).thenReturn("result");
  }

  private JtaTransactionInterceptor interceptor(boolean requiresNew) {
    JtaTransactionInterceptor interceptor = new JtaTransactionInterceptor(txManager, requiresNew, null);
    interceptor.setNext(next);
    return interceptor;
  }

  @Test
  public void shouldBeginAndCommitWhenNoTransactionExists() throws Exception {
    assertThat(interceptor(false).execute(command)).isEqualTo("result");

    verify(txManager).begin();
    verify(txManager).commit();
    verify(txManager, never()).rollback();
    verify(txManager, never()).suspend();
  }

  @Test
  public void shouldJoinExistingTransactionWithoutCommit() throws Exception {
    when(txManager.getStatus()).thenReturn(Status.STATUS_ACTIVE);

    assertThat(interceptor(false).execute(command)).isEqualTo("result");

    verify(txManager, never()).begin();
    verify(txManager, never()).commit();
  }

  @Test
  public void shouldSuspendAndResumeWhenRequiresNew() throws Exception {
    Transaction old = mock(Transaction.class);
    when(txManager.getStatus()).thenReturn(Status.STATUS_ACTIVE);
    when(txManager.suspend()).thenReturn(old);

    assertThat(interceptor(true).execute(command)).isEqualTo("result");

    verify(txManager).suspend();
    verify(txManager).begin();
    verify(txManager).commit();
    verify(txManager).resume(old);
  }

  @Test
  public void shouldRollbackNewTransactionOnRuntimeException() throws Exception {
    IllegalStateException failure = new IllegalStateException("boom");
    when(next.execute(command)).thenThrow(failure);

    assertThatThrownBy(() -> interceptor(false).execute(command)).isSameAs(failure);

    verify(txManager).rollback();
    verify(txManager, never()).commit();
  }

  @Test
  public void shouldMarkRollbackOnlyWhenJoiningExistingTransaction() throws Exception {
    when(txManager.getStatus()).thenReturn(Status.STATUS_ACTIVE);
    when(next.execute(command)).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> interceptor(false).execute(command)).isInstanceOf(IllegalStateException.class);

    verify(txManager).setRollbackOnly();
    verify(txManager, never()).rollback();
  }

  @Test
  public void shouldRollbackOnError() throws Exception {
    AssertionError failure = new AssertionError("err");
    when(next.execute(command)).thenThrow(failure);

    assertThatThrownBy(() -> interceptor(false).execute(command)).isSameAs(failure);

    verify(txManager).rollback();
  }

  @Test
  public void shouldSwallowSystemExceptionDuringRollback() throws Exception {
    when(next.execute(command)).thenThrow(new IllegalStateException("boom"));
    doThrow(new SystemException("rollback failed")).when(txManager).rollback();

    assertThatThrownBy(() -> interceptor(false).execute(command))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("boom");
  }

  @Test
  public void shouldWrapCheckedExceptionOfCommand() throws Exception {
    Exception checked = new Exception("checked");
    when(next.execute(command)).then(invocation -> {
      throw checked;
    });

    assertThatThrownBy(() -> interceptor(false).execute(command))
      .isInstanceOf(UndeclaredThrowableException.class)
      .hasCause(checked);

    verify(txManager).rollback();
  }

  @Test
  public void shouldWrapFailureToBegin() throws Exception {
    doThrow(new NotSupportedException("no")).when(txManager).begin();

    assertThatThrownBy(() -> interceptor(false).execute(command))
      .isInstanceOf(TransactionException.class)
      .hasMessage("Unable to begin transaction");
  }

  @Test
  public void shouldWrapFailureToReadStatus() throws Exception {
    when(txManager.getStatus()).thenThrow(new SystemException("status"));

    assertThatThrownBy(() -> interceptor(false).execute(command))
      .isInstanceOf(TransactionException.class)
      .hasMessage("Unable to retrieve transaction status");
  }

  @Test
  public void shouldWrapFailureToSuspend() throws Exception {
    when(txManager.getStatus()).thenReturn(Status.STATUS_ACTIVE);
    when(txManager.suspend()).thenThrow(new SystemException("suspend"));

    assertThatThrownBy(() -> interceptor(true).execute(command))
      .isInstanceOf(TransactionException.class)
      .hasMessage("Unable to suspend transaction");
  }

  @Test
  public void shouldWrapFailureToResume() throws Exception {
    when(txManager.getStatus()).thenReturn(Status.STATUS_ACTIVE);
    when(txManager.suspend()).thenReturn(mock(Transaction.class));
    doThrow(new InvalidTransactionException("resume")).when(txManager).resume(any(Transaction.class));

    assertThatThrownBy(() -> interceptor(true).execute(command))
      .isInstanceOf(TransactionException.class)
      .hasMessage("Unable to resume transaction");
  }

  @Test
  public void shouldWrapFailureToCommit() throws Exception {
    doThrow(new HeuristicMixedException("mixed")).when(txManager).commit();

    assertThatThrownBy(() -> interceptor(false).execute(command))
      .isInstanceOf(TransactionException.class)
      .hasMessage("Unable to commit transaction");
  }

  @Test
  public void shouldReportRollbackAtCommitAsTransactionException() throws Exception {
    RollbackException rollback = new RollbackException("rolled back");
    doThrow(rollback).when(txManager).commit();

    assertThatThrownBy(() -> interceptor(false).execute(command))
      .isInstanceOf(TransactionException.class)
      .hasMessage("Unable to commit transaction")
      .hasCause(rollback);
  }

  @Test
  public void shouldRollbackAndRethrowRuntimeExceptionDuringCommit() throws Exception {
    IllegalStateException failure = new IllegalStateException("commit boom");
    doThrow(failure).when(txManager).commit();

    assertThatThrownBy(() -> interceptor(false).execute(command)).isSameAs(failure);

    verify(txManager).rollback();
  }
}
