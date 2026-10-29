package org.camunda.bpm.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.camunda.bpm.application.ProcessApplication;
import org.junit.Test;

public class AbstractServletProcessApplicationDeployerTest {

  static class TestDeployer extends AbstractServletProcessApplicationDeployer {
    @Override
    protected Exception getServletException(String message) {
      return new IllegalStateException(message);
    }

    void run(Set<Class<?>> classes, List<String> registered) throws Exception {
      onStartUp(classes, "/ctx", ServletProcessApplication.class, registered::add);
    }
  }

  public static class MyServletPa extends ServletProcessApplication {
  }

  public static class MyEmbeddedPa extends EmbeddedProcessApplication {
  }

  private final TestDeployer deployer = new TestDeployer();
  private final List<String> registered = new ArrayList<>();

  @Test
  public void shouldSkipNullClasses() throws Exception {
    deployer.run(null, registered);
    assertThat(registered).isEmpty();
  }

  @Test
  public void shouldSkipEmptyClasses() throws Exception {
    deployer.run(Collections.<Class<?>>emptySet(), registered);
    assertThat(registered).isEmpty();
  }

  @Test
  public void shouldRegisterServletProcessApplication() throws Exception {
    Set<Class<?>> classes = new HashSet<>();
    classes.add(MyServletPa.class);

    deployer.run(classes, registered);

    assertThat(registered).containsExactly(MyServletPa.class.getName());
  }

  @Test
  public void shouldIgnoreAnnotationClassAndSupportImmutableSet() throws Exception {
    Set<Class<?>> classes = Collections.unmodifiableSet(new HashSet<Class<?>>(
        java.util.Arrays.<Class<?>>asList(ProcessApplication.class, MyServletPa.class)));

    deployer.run(classes, registered);

    assertThat(registered).containsExactly(MyServletPa.class.getName());
  }

  @Test
  public void shouldNotRegisterNonServletProcessApplication() throws Exception {
    Set<Class<?>> classes = new HashSet<>();
    classes.add(MyEmbeddedPa.class);

    deployer.run(classes, registered);

    assertThat(registered).isEmpty();
  }

  @Test
  public void shouldHandleOnlyAnnotationClass() throws Exception {
    Set<Class<?>> classes = new HashSet<>();
    classes.add(ProcessApplication.class);

    deployer.run(classes, registered);

    assertThat(registered).isEmpty();
  }

  @Test
  public void shouldFailOnMultipleProcessApplications() {
    Set<Class<?>> classes = new HashSet<>();
    classes.add(MyServletPa.class);
    classes.add(MyEmbeddedPa.class);

    assertThatThrownBy(() -> deployer.run(classes, registered))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("/ctx");
    assertThat(registered).isEmpty();
  }

  @Test
  public void shouldFailOnClassThatIsNotAProcessApplication() {
    Set<Class<?>> classes = new HashSet<>();
    classes.add(String.class);

    assertThatThrownBy(() -> deployer.run(classes, registered))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.class.getName());
    assertThat(registered).isEmpty();
  }
}
