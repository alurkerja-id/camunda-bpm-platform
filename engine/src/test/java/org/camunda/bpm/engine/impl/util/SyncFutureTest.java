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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class SyncFutureTest {

  @Test
  public void shouldReturnResult() throws Exception {
    SyncFuture<String> future = new SyncFuture<>("value");

    assertThat(future.get()).isEqualTo("value");
    assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("value");
  }

  @Test
  public void shouldAlwaysBeDoneAndNeverCancellable() {
    SyncFuture<String> future = new SyncFuture<>("value");

    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.cancel(true)).isFalse();
  }

  @Test
  public void shouldWrapFailureInExecutionException() {
    IllegalStateException failure = new IllegalStateException("boom");
    SyncFuture<String> future = new SyncFuture<>(failure);

    assertThatThrownBy(future::get)
      .isInstanceOf(ExecutionException.class)
      .hasCause(failure);
    assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
      .isInstanceOf(ExecutionException.class)
      .hasCause(failure);
  }
}
