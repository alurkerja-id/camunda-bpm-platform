package org.camunda.bpm.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProcessEnginesTest {

  private static final String VALID_CFG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<beans xmlns=\"http://www.springframework.org/schema/beans\" "
      + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
      + "xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd\">\n"
      + "<bean id=\"processEngineConfiguration\" class=\"org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration\">\n"
      + "<property name=\"processEngineName\" value=\"pe-test-engine\"/>\n"
      + "<property name=\"jdbcUrl\" value=\"jdbc:h2:mem:pe-test-db;DB_CLOSE_DELAY=1000\"/>\n"
      + "<property name=\"databaseSchemaUpdate\" value=\"true\"/>\n"
      + "<property name=\"jobExecutorActivate\" value=\"false\"/>\n"
      + "<property name=\"enforceHistoryTimeToLive\" value=\"false\"/>\n"
      + "</bean></beans>";

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private ClassLoader originalClassLoader;
  private boolean savedInitialized;
  private Map<String, ProcessEngine> savedEngines;
  private Map<String, ProcessEngineInfo> savedByName;
  private Map<String, ProcessEngineInfo> savedByUrl;
  private List<ProcessEngineInfo> savedInfos;

  @Before
  public void saveStaticState() {
    originalClassLoader = Thread.currentThread().getContextClassLoader();
    savedInitialized = ProcessEngines.isInitialized;
    savedEngines = ProcessEngines.processEngines;
    savedByName = new HashMap<>(ProcessEngines.processEngineInfosByName);
    savedByUrl = new HashMap<>(ProcessEngines.processEngineInfosByResourceUrl);
    savedInfos = new ArrayList<>(ProcessEngines.processEngineInfos);

    ProcessEngines.isInitialized = false;
    ProcessEngines.processEngines = new HashMap<>();
    ProcessEngines.processEngineInfosByName.clear();
    ProcessEngines.processEngineInfosByResourceUrl.clear();
    ProcessEngines.processEngineInfos.clear();
  }

  @After
  public void restoreStaticState() {
    Thread.currentThread().setContextClassLoader(originalClassLoader);
    ProcessEngines.isInitialized = savedInitialized;
    ProcessEngines.processEngines = savedEngines;
    ProcessEngines.processEngineInfosByName.clear();
    ProcessEngines.processEngineInfosByName.putAll(savedByName);
    ProcessEngines.processEngineInfosByResourceUrl.clear();
    ProcessEngines.processEngineInfosByResourceUrl.putAll(savedByUrl);
    ProcessEngines.processEngineInfos.clear();
    ProcessEngines.processEngineInfos.addAll(savedInfos);
  }

  private File classpathWith(String cfgContent) throws Exception {
    File root = folder.newFolder();
    if (cfgContent != null) {
      Files.write(new File(root, "camunda.cfg.xml").toPath(), cfgContent.getBytes(StandardCharsets.UTF_8));
    }
    // parent-last is not needed: an empty parent would hide the engine classes, so use an isolated resource lookup only
    ClassLoader loader = new URLClassLoader(new URL[] { root.toURI().toURL() }, originalClassLoader) {
      @Override
      public java.util.Enumeration<URL> getResources(String name) throws java.io.IOException {
        if ("camunda.cfg.xml".equals(name)) {
          return findResources(name);
        }
        return super.getResources(name);
      }
    };
    Thread.currentThread().setContextClassLoader(loader);
    return new File(root, "camunda.cfg.xml");
  }

  private ProcessEngine namedEngine(String name) {
    ProcessEngine engine = mock(ProcessEngine.class);
    when(engine.getName()).thenReturn(name);
    return engine;
  }

  @Test
  public void shouldRegisterAndUnregisterEngine() {
    ProcessEngine engine = namedEngine("custom");

    ProcessEngines.registerProcessEngine(engine);
    assertThat(ProcessEngines.getProcessEngines()).containsEntry("custom", engine);

    ProcessEngines.unregister(engine);
    assertThat(ProcessEngines.getProcessEngines()).doesNotContainKey("custom");
  }

  @Test
  public void shouldReturnRegisteredEngineWithoutInitializing() throws Exception {
    classpathWith(null);
    ProcessEngine engine = namedEngine(ProcessEngines.NAME_DEFAULT);
    ProcessEngines.registerProcessEngine(engine);

    assertThat(ProcessEngines.getDefaultProcessEngine()).isSameAs(engine);
    assertThat(ProcessEngines.getDefaultProcessEngine(false)).isSameAs(engine);
    assertThat(ProcessEngines.getProcessEngine("default")).isSameAs(engine);
    assertThat(ProcessEngines.isInitialized).isTrue();
  }

  @Test
  public void shouldInitializeEngineFromConfigurationOnClasspath() throws Exception {
    File cfg = classpathWith(VALID_CFG);

    ProcessEngines.init();

    assertThat(ProcessEngines.isInitialized).isTrue();
    ProcessEngine engine = ProcessEngines.getProcessEngine("pe-test-engine");
    assertThat(engine).isNotNull();
    ProcessEngineInfo info = ProcessEngines.getProcessEngineInfo("pe-test-engine");
    assertThat(info.getException()).isNull();
    assertThat(info.getResourceUrl()).isEqualTo(cfg.toURI().toURL().toString());
    assertThat(ProcessEngines.getProcessEngineInfos()).containsExactly(info);

    ProcessEngines.destroy();
    assertThat(ProcessEngines.isInitialized).isFalse();
    assertThat(ProcessEngines.getProcessEngines()).isEmpty();
    assertThat(ProcessEngines.getProcessEngineInfos()).isEmpty();
    assertThat(ProcessEngines.getProcessEngineInfo("pe-test-engine")).isNull();
  }

  @Test
  public void shouldIgnoreSecondInit() throws Exception {
    classpathWith(null);
    ProcessEngines.init(false);
    assertThat(ProcessEngines.isInitialized).isTrue();

    ProcessEngines.init(false);

    assertThat(ProcessEngines.isInitialized).isTrue();
    assertThat(ProcessEngines.getProcessEngines()).isEmpty();
  }

  @Test
  public void shouldRecordFailureForBrokenConfiguration() throws Exception {
    File cfg = classpathWith("<broken");

    ProcessEngines.init();

    assertThat(ProcessEngines.getProcessEngines()).isEmpty();
    assertThat(ProcessEngines.getProcessEngineInfos()).hasSize(1);
    ProcessEngineInfo info = ProcessEngines.getProcessEngineInfos().get(0);
    assertThat(info.getName()).isNull();
    assertThat(info.getResourceUrl()).isEqualTo(cfg.toURI().toURL().toString());
    assertThat(info.getException()).isNotEmpty();
  }

  @Test
  public void shouldRetryAndReplaceStaleInfoForSameResource() throws Exception {
    File cfg = classpathWith("<broken");
    ProcessEngines.init();
    String url = cfg.toURI().toURL().toString();
    assertThat(ProcessEngines.getProcessEngineInfos()).hasSize(1);

    Files.write(cfg.toPath(), VALID_CFG.getBytes(StandardCharsets.UTF_8));
    ProcessEngineInfo retried = ProcessEngines.retry(url);

    assertThat(retried.getException()).isNull();
    assertThat(retried.getName()).isEqualTo("pe-test-engine");
    assertThat(ProcessEngines.getProcessEngineInfos()).containsExactly(retried);
    assertThat(ProcessEngines.getProcessEngine("pe-test-engine")).isNotNull();

    ProcessEngineInfo retriedAgain = ProcessEngines.retry(url);
    assertThat(ProcessEngines.getProcessEngineInfos()).containsExactly(retriedAgain);
    assertThat(ProcessEngines.getProcessEngines()).hasSize(1);

    ProcessEngines.destroy();
  }

  @Test
  public void shouldRejectInvalidRetryUrl() {
    assertThatThrownBy(() -> ProcessEngines.retry("not a url"))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("invalid url");
    assertThatThrownBy(() -> ProcessEngines.retry("noscheme:///x"))
        .isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldFailInitWhenSpringContextCannotBeBuilt() throws Exception {
    File root = folder.newFolder();
    Files.write(new File(root, "activiti-context.xml").toPath(), "<beans/>".getBytes(StandardCharsets.UTF_8));
    URL resource = new File(root, "activiti-context.xml").toURI().toURL();

    assertThatThrownBy(() -> ProcessEngines.initProcessEngineFromSpringResource(resource))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("couldn't initialize process engine from spring configuration resource");
  }

  @Test
  public void shouldCloseAllEnginesOnDestroyEvenWhenOneFails() {
    ProcessEngine failing = namedEngine("failing");
    ProcessEngine healthy = namedEngine("healthy");
    doThrow(new IllegalStateException("close failed")).when(failing).close();
    ProcessEngines.isInitialized = true;
    ProcessEngines.registerProcessEngine(failing);
    ProcessEngines.registerProcessEngine(healthy);

    ProcessEngines.destroy();

    verify(failing).close();
    verify(healthy).close();
    assertThat(ProcessEngines.getProcessEngines()).isEmpty();
    assertThat(ProcessEngines.isInitialized).isFalse();
  }

  @Test
  public void shouldIgnoreDestroyWhenNotInitialized() {
    ProcessEngine engine = namedEngine("keep");
    ProcessEngines.registerProcessEngine(engine);

    ProcessEngines.destroy();

    assertThat(ProcessEngines.getProcessEngines()).containsEntry("keep", engine);
  }
}
