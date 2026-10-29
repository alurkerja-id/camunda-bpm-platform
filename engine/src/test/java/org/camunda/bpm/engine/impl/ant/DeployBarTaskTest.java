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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.ProcessEngineInfo;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DeployBarTaskTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private boolean savedInitialized;
  private Map<String, ProcessEngine> savedEngines;
  private List<ProcessEngineInfo> savedInfos;
  private ClassLoader savedLoader;

  private static Object get(String name) throws Exception {
    Field f = ProcessEngines.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(null);
  }

  private static void set(String name, Object value) throws Exception {
    Field f = ProcessEngines.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(null, value);
  }

  @SuppressWarnings("unchecked")
  @Before
  public void isolateEngines() throws Exception {
    savedLoader = Thread.currentThread().getContextClassLoader();
    savedInitialized = (Boolean) get("isInitialized");
    savedEngines = (Map<String, ProcessEngine>) get("processEngines");
    savedInfos = new ArrayList<>((List<ProcessEngineInfo>) get("processEngineInfos"));
    // already initialized with no engines: init() becomes a no-op
    set("isInitialized", true);
    set("processEngines", new HashMap<String, ProcessEngine>());
    ((List<ProcessEngineInfo>) get("processEngineInfos")).clear();
  }

  @SuppressWarnings("unchecked")
  @After
  public void restoreEngines() throws Exception {
    set("isInitialized", savedInitialized);
    set("processEngines", savedEngines);
    List<ProcessEngineInfo> infos = (List<ProcessEngineInfo>) get("processEngineInfos");
    infos.clear();
    infos.addAll(savedInfos);
    Thread.currentThread().setContextClassLoader(savedLoader);
  }

  private File zip(String name) throws IOException {
    File f = folder.newFile(name);
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
      out.putNextEntry(new ZipEntry("a.txt"));
      out.write(1);
      out.closeEntry();
    }
    return f;
  }

  private ProcessEngine registerMockEngine(String name, DeploymentBuilder builder) {
    RepositoryService repositoryService = mock(RepositoryService.class);
    when(repositoryService.createDeployment()).thenReturn(builder);
    ProcessEngine engine = mock(ProcessEngine.class);
    when(engine.getName()).thenReturn(name);
    when(engine.getRepositoryService()).thenReturn(repositoryService);
    ProcessEngines.registerProcessEngine(engine);
    return engine;
  }

  private DeploymentBuilder chainingBuilder() {
    DeploymentBuilder builder = mock(DeploymentBuilder.class);
    when(builder.name(anyString())).thenReturn(builder);
    when(builder.addZipInputStream(any(ZipInputStream.class))).thenReturn(builder);
    return builder;
  }

  @Test
  public void shouldExposeProperties() {
    DeployBarTask task = new DeployBarTask();
    File file = new File("x.bar");
    List<FileSet> sets = new ArrayList<>();

    assertThat(task.getProcessEngineName()).isEqualTo(ProcessEngines.NAME_DEFAULT);
    task.setProcessEngineName("other");
    task.setFile(file);
    task.setFileSets(sets);

    assertThat(task.getProcessEngineName()).isEqualTo("other");
    assertThat(task.getFile()).isSameAs(file);
    assertThat(task.getFileSets()).isSameAs(sets);
  }

  @Test
  public void shouldCollectSingleFileAndFileSetFiles() throws IOException {
    File single = zip("single.bar");
    File a = zip("a.bar");
    zip("skip.txt");
    FileSet fileSet = new FileSet();
    fileSet.setDir(folder.getRoot());
    fileSet.setIncludes("a.bar");
    DeployBarTask task = new DeployBarTask();
    task.setProject(new Project());
    task.setFile(single);
    task.setFileSets(Collections.singletonList(fileSet));

    List<File> files = task.collectFiles();

    assertThat(files).containsExactly(single, new File(folder.getRoot(), "a.bar"));
    assertThat(a).exists();
  }

  @Test
  public void shouldCollectNothingWhenNoFileConfigured() {
    assertThat(new DeployBarTask().collectFiles()).isEmpty();
  }

  @Test
  public void shouldDeployFiles() throws IOException {
    DeploymentBuilder builder = chainingBuilder();
    registerMockEngine(ProcessEngines.NAME_DEFAULT, builder);
    DeployBarTask task = new DeployBarTask();
    task.setFile(zip("deploy.bar"));

    task.execute();

    verify(builder).name("deploy.bar");
    verify(builder).deploy();
    assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(savedLoader);
  }

  @Test
  public void shouldFailWhenNoEnginesAreLoaded() {
    DeployBarTask task = new DeployBarTask();
    task.setProcessEngineName("ghost");

    assertThatThrownBy(task::execute)
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("Could not find a process engine with name 'ghost', no engines found.");
    assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(savedLoader);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldListLoadedEnginesWhenNamedEngineIsMissing() throws Exception {
    List<ProcessEngineInfo> infos = (List<ProcessEngineInfo>) get("processEngineInfos");
    infos.add(info(null, "res1", "Error with driver on UnpooledDataSource foo"));
    infos.add(info("eng2", "res2", "boom"));
    infos.add(info("eng3", "res3", null));
    DeployBarTask task = new DeployBarTask();
    task.setProcessEngineName("ghost");

    assertThatThrownBy(task::execute)
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("Could not find a process engine with name ghost, engines loaded:")
      .hasMessageContaining("Process engine name: unknown - resource: res1 - status: Error while initializing engine. Exception while initializing process engine! Database or database driver might not have been configured correctly.")
      .hasMessageContaining("Process engine name: eng2 - resource: res2 - status: Error while initializing engine. Stacktrace: boom")
      .hasMessageContaining("Process engine name: eng3 - resource: res3 - status: Initialised");
  }

  private ProcessEngineInfo info(String name, String resource, String exception) {
    ProcessEngineInfo info = mock(ProcessEngineInfo.class);
    when(info.getName()).thenReturn(name);
    when(info.getResourceUrl()).thenReturn(resource);
    when(info.getException()).thenReturn(exception);
    return info;
  }

  @Test
  public void shouldWrapDeployFailureWithFilePath() {
    DeploymentBuilder builder = chainingBuilder();
    when(builder.deploy()).thenThrow(new IllegalStateException("kaput"));
    RepositoryService repositoryService = mock(RepositoryService.class);
    when(repositoryService.createDeployment()).thenReturn(builder);
    File missing = new File(folder.getRoot(), "ok.bar");

    assertThatThrownBy(() -> new DeployBarTask().deployBar(repositoryService, zip(missing)))
      .isInstanceOf(BuildException.class)
      .hasMessage("couldn't deploy bar " + missing.getAbsolutePath() + ": kaput");
  }

  @Test
  public void shouldWrapMissingBarFile() {
    RepositoryService repositoryService = mock(RepositoryService.class);
    File missing = new File(folder.getRoot(), "missing.bar");

    assertThatThrownBy(() -> new DeployBarTask().deployBar(repositoryService, missing))
      .isInstanceOf(BuildException.class)
      .hasMessageContaining("couldn't deploy bar " + missing.getAbsolutePath());
  }

  private File zip(File f) throws IOException {
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
      out.putNextEntry(new ZipEntry("a.txt"));
      out.write(1);
      out.closeEntry();
    }
    return f;
  }
}
