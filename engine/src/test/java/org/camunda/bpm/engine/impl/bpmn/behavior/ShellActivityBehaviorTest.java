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
package org.camunda.bpm.engine.impl.bpmn.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.Expression;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ShellActivityBehaviorTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  protected ActivityExecution execution;
  protected AtomicInteger leaveCount;
  protected ShellActivityBehavior behavior;

  @Before
  public void setUp() {
    Assume.assumeTrue("needs a POSIX shell", Files.isExecutable(Path.of("/bin/sh")));
    execution = mock(ActivityExecution.class);
    leaveCount = new AtomicInteger();
    behavior = new ShellActivityBehavior() {
      @Override
      public void leave(ActivityExecution execution) {
        leaveCount.incrementAndGet();
      }
    };
  }

  protected Expression literal(String value) {
    Expression expression = mock(Expression.class);
    when(expression.getValue(any(DelegateExecution.class))).thenReturn(value);
    return expression;
  }

  @Test
  public void shouldStoreOutputAndExitCodeAndLeave() {
    behavior.command = literal("/bin/sh");
    behavior.arg1 = literal("-c");
    behavior.arg2 = literal("echo hello; exit 3");
    behavior.outputVariable = literal("out");
    behavior.errorCodeVariable = literal("code");

    behavior.execute(execution);

    verify(execution).setVariable("out", "hello\n");
    verify(execution).setVariable("code", "3");
    assertThat(leaveCount).hasValue(1);
  }

  @Test
  public void shouldPassAllFiveArguments() {
    behavior.command = literal("/bin/echo");
    behavior.arg1 = literal("a");
    behavior.arg2 = literal("b");
    behavior.arg3 = literal("c");
    behavior.arg4 = literal("d");
    behavior.arg5 = literal("e");
    behavior.outputVariable = literal("out");

    behavior.execute(execution);

    verify(execution).setVariable("out", "a b c d e\n");
  }

  @Test
  public void shouldMergeStderrWhenRedirectIsEnabled() {
    behavior.command = literal("/bin/sh");
    behavior.arg1 = literal("-c");
    behavior.arg2 = literal("echo oops 1>&2");
    behavior.redirectError = literal("true");
    behavior.outputVariable = literal("out");

    behavior.execute(execution);

    verify(execution).setVariable("out", "oops\n");
  }

  @Test
  public void shouldRunInTheConfiguredDirectory() throws IOException {
    Path dir = folder.newFolder().toPath().toRealPath();
    behavior.command = literal("/bin/pwd");
    behavior.directory = literal(dir.toString());
    behavior.outputVariable = literal("out");

    behavior.execute(execution);

    verify(execution).setVariable("out", dir + "\n");
  }

  @Test
  public void shouldRunWithEmptyEnvironmentWhenCleanEnvIsSet() {
    behavior.command = literal("/bin/sh");
    behavior.arg1 = literal("-c");
    behavior.arg2 = literal("echo \"[$HOME]\"");
    behavior.cleanEnv = literal("true");
    behavior.outputVariable = literal("out");

    behavior.execute(execution);

    verify(execution).setVariable("out", "[]\n");
  }

  @Test
  public void shouldNotCaptureAnythingWhenNotWaiting() {
    behavior.command = literal("/bin/true");
    behavior.wait = literal("false");
    behavior.outputVariable = literal("out");
    behavior.errorCodeVariable = literal("code");

    behavior.execute(execution);

    org.mockito.Mockito.verify(execution, org.mockito.Mockito.never()).setVariable(any(), any(Object.class));
    assertThat(leaveCount).hasValue(1);
  }

  @Test
  public void shouldWrapFailureToStartTheCommand() {
    behavior.command = literal("/no/such/binary");

    assertThatThrownBy(() -> behavior.execute(execution))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-");
    assertThat(leaveCount).hasValue(0);
  }

  @Test
  public void shouldReadFieldsAsNullWhenExpressionIsMissingOrEvaluatesToNull() {
    assertThat(behavior.getStringFromField(null, execution)).isNull();
    assertThat(behavior.getStringFromField(literal(null), execution)).isNull();
    assertThat(behavior.getStringFromField(literal("x"), execution)).isEqualTo("x");
  }

  @Test
  public void shouldConvertStreamToString() throws IOException {
    InputStream stream = new ByteArrayInputStream("héllo".getBytes(StandardCharsets.UTF_8));

    assertThat(ShellActivityBehavior.convertStreamToStr(stream)).isEqualTo("héllo");
  }

  @Test
  public void shouldConvertNullStreamToEmptyString() throws IOException {
    assertThat(ShellActivityBehavior.convertStreamToStr(null)).isEmpty();
  }
}
