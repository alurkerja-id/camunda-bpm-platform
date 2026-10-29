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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LaunchTaskTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private static class RecordingLaunchTask extends LaunchTask {
    final List<String> lines = new ArrayList<>();

    @Override
    public void log(String msg) {
      lines.add(msg);
    }
  }

  private File script(String name, String body) throws IOException {
    File file = folder.newFile(name);
    java.nio.file.Files.write(file.toPath(), body.getBytes());
    assertThat(file.setExecutable(true)).isTrue();
    return file;
  }

  @Test
  public void shouldRequireDir() {
    LaunchTask task = new LaunchTask();
    task.setScript("run");

    assertThatThrownBy(task::execute).isInstanceOf(BuildException.class).hasMessage("dir attribute is required with the launch task");
  }

  @Test
  public void shouldRequireScript() {
    LaunchTask task = new LaunchTask();
    task.setDir(folder.getRoot());

    assertThatThrownBy(task::execute).isInstanceOf(BuildException.class).hasMessage("script attribute is required with the launch task");
  }

  @Test
  public void shouldFindPlainScript() throws IOException {
    script("plain", "#!/bin/sh\n");
    LaunchTask task = new LaunchTask();
    task.setDir(folder.getRoot());
    task.setScript("plain");

    assertThat(task.getExecutable()).isEqualTo(new File(folder.getRoot(), "plain").getAbsolutePath());
  }

  @Test
  public void shouldFindShScriptOnLinuxOrMac() throws IOException {
    String os = System.getProperty("os.name").toLowerCase();
    Assume.assumeTrue(os.contains("linux") || os.contains("mac"));
    script("withext.sh", "#!/bin/sh\n");
    LaunchTask task = new LaunchTask();
    task.setDir(folder.getRoot());
    task.setScript("withext");

    assertThat(task.getExecutable()).isEqualTo(new File(folder.getRoot(), "withext.sh").getAbsolutePath());
  }

  @Test
  public void shouldFailWhenNoExecutableExists() {
    LaunchTask task = new LaunchTask();
    task.setDir(folder.getRoot());
    task.setScript("missing");

    assertThatThrownBy(task::getExecutable).isInstanceOf(BuildException.class).hasMessageContaining("couldn't find executable for script");
  }

  @Test
  public void shouldReportExistence() throws IOException {
    File file = folder.newFile("exists.txt");

    assertThat(new LaunchTask().exists(file.getAbsolutePath())).isTrue();
    assertThat(new LaunchTask().exists(file.getAbsolutePath() + ".nope")).isFalse();
  }

  @Test
  public void shouldLaunchScriptWithoutArgs() throws IOException {
    Assume.assumeTrue(new File("/bin/sh").exists());
    script("hello.sh", "#!/bin/sh\necho hello-from-script\n");
    RecordingLaunchTask task = new RecordingLaunchTask();
    task.setDir(folder.getRoot());
    task.setScript("hello.sh");
    task.setMsg("hello-from-script");

    task.execute();

    assertThat(task.lines).contains("  hello-from-script");
  }

  @Test
  public void shouldLaunchScriptWithArgs() throws IOException {
    Assume.assumeTrue(new File("/bin/sh").exists());
    script("withargs.sh", "#!/bin/sh\necho started\n");
    RecordingLaunchTask task = new RecordingLaunchTask();
    task.setDir(folder.getRoot());
    task.setScript("withargs.sh");
    task.setArgs("a b");

    task.execute();

    assertThat(task.lines).contains("  started", "launched process completed");
    assertThat(task.lines.get(0)).contains("withargs.sh args ");
  }
}
