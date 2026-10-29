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
package org.camunda.bpm.engine.impl.ant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class LaunchThreadTest {

  static class RecordingTask extends Task {
    final List<String> lines = new ArrayList<>();

    @Override
    public void log(String msg) {
      lines.add(msg);
    }
  }

  @Before
  public void requireShell() {
    Assume.assumeTrue(new java.io.File("/bin/sh").exists());
  }

  @Test
  public void shouldFailOnNullCommand() {
    assertThatThrownBy(() -> LaunchThread.launch(new RecordingTask(), null, null, null))
      .isInstanceOf(BuildException.class)
      .hasMessage("cmd is null");
  }

  @Test
  public void shouldReadUntilProcessEndsWhenNoCompletionMessage() {
    RecordingTask task = new RecordingTask();

    LaunchThread.launch(task, new String[] {"/bin/sh", "-c", "echo first; echo second"}, null, null);

    assertThat(task.lines).contains("not waiting for a launch completion msg.", "  first", "  second", "launched process completed");
  }

  @Test
  public void shouldStopReadingAtCompletionMessage() {
    RecordingTask task = new RecordingTask();

    LaunchThread.launch(task, new String[] {"/bin/sh", "-c", "echo booting; echo READY; echo after"}, null, "READY");

    assertThat(task.lines).contains("waiting for launch completion msg 'READY'...", "  booting", "  READY");
    assertThat(task.lines).doesNotContain("  after", "launched process completed");
  }

  @Test
  public void shouldLogCommandAndDirectory() {
    RecordingTask task = new RecordingTask();
    java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"));

    LaunchThread.launch(task, new String[] {"/bin/sh", "-c", "pwd"}, dir, null);

    assertThat(task.lines.get(0)).startsWith("launching cmd '/bin/sh -c pwd ' in dir '").contains(dir.toString());
  }

  @Test
  public void shouldReportStartFailureFromThread() throws InterruptedException {
    RecordingTask task = new RecordingTask();
    LaunchThread thread = new LaunchThread(task, new String[] {"/nonexistent/binary-xyz"}, null, null);
    Throwable[] caught = new Throwable[1];
    thread.setUncaughtExceptionHandler((t, e) -> caught[0] = e);

    thread.start();
    thread.join();

    assertThat(caught[0]).isInstanceOf(BuildException.class).hasMessageContaining("couldn't launch /nonexistent/binary-xyz");
    assertThat(task.lines).doesNotContain("launched process completed");
  }

  @Test
  public void shouldWrapInterruptionAndRestoreFlag() {
    RecordingTask task = new RecordingTask();
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> LaunchThread.launch(task, new String[] {"/bin/sh", "-c", "sleep 1"}, null, null))
        .isInstanceOf(BuildException.class)
        .hasMessageContaining("couldn't launch cmd: /bin/sh -c sleep 1 ");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
