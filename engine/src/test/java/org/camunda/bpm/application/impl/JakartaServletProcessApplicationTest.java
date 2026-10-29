package org.camunda.bpm.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import org.junit.Test;

public class JakartaServletProcessApplicationTest {

  static class TrackingApplication extends JakartaServletProcessApplication {
    int deployCalls;
    int undeployCalls;

    @Override
    public void deploy() {
      deployCalls++;
    }

    @Override
    public void undeploy() {
      undeployCalls++;
    }
  }

  private ServletContextEvent event(int majorVersion, ClassLoader contextClassLoader) {
    ServletContext context = mock(ServletContext.class);
    when(context.getContextPath()).thenReturn("/my-app");
    when(context.getServletContextName()).thenReturn("my-app-name");
    when(context.getMajorVersion()).thenReturn(majorVersion);
    when(context.getClassLoader()).thenReturn(contextClassLoader);
    ServletContextEvent event = mock(ServletContextEvent.class);
    when(event.getServletContext()).thenReturn(context);
    return event;
  }

  @Test
  public void shouldInitializeStateAndDeployOnContextInitialized() {
    ServletContextEvent event = event(3, new ClassLoader() { });
    TrackingApplication application = new TrackingApplication();

    application.contextInitialized(event);

    assertThat(application.getServletContext()).isSameAs(event.getServletContext());
    assertThat(application.deployCalls).isEqualTo(1);
    assertThat(application.getName()).isEqualTo("my-app-name");
    assertThat(application.getProperties()).containsValue("/my-app");
    assertThat(application.autodetectProcessApplicationName()).isEqualTo("my-app-name");
  }

  @Test
  public void shouldUseOwnClassLoaderForSubclasses() {
    TrackingApplication application = new TrackingApplication();

    application.contextInitialized(event(3, new ClassLoader() { }));

    assertThat(application.getProcessApplicationClassloader())
        .isSameAs(TrackingApplication.class.getClassLoader());
  }

  @Test
  public void shouldUseServletContextClassLoaderForPlainClassOnServlet3() {
    ClassLoader contextLoader = new ClassLoader() { };
    JakartaServletProcessApplication application = new JakartaServletProcessApplication();

    ClassLoader loader = application.initProcessApplicationClassloader(event(3, contextLoader));

    assertThat(loader).isSameAs(contextLoader);
  }

  @Test
  public void shouldUndeployAndClearReferenceOnContextDestroyed() {
    TrackingApplication application = new TrackingApplication();
    application.contextInitialized(event(3, new ClassLoader() { }));
    assertThat(application.getReference()).isNotNull();

    application.contextDestroyed(event(3, null));

    assertThat(application.undeployCalls).isEqualTo(1);
    assertThat(application.reference).isNull();
  }

  @Test
  public void shouldUndeployWithoutReference() {
    TrackingApplication application = new TrackingApplication();

    application.contextDestroyed(event(3, null));

    assertThat(application.undeployCalls).isEqualTo(1);
    assertThat(application.reference).isNull();
  }
}
