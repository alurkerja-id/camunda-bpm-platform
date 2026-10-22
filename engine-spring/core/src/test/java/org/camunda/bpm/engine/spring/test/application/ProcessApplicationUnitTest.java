package org.camunda.bpm.engine.spring.test.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import javax.servlet.ServletContext;

import org.camunda.bpm.application.ProcessApplicationInfo;
import org.camunda.bpm.application.ProcessApplicationReference;
import org.camunda.bpm.engine.spring.ApplicationContextElResolver;
import org.camunda.bpm.engine.spring.application.SpringProcessApplication;
import org.camunda.bpm.engine.spring.application.SpringProcessApplicationElResolver;
import org.camunda.bpm.engine.spring.application.SpringServletProcessApplication;
import org.camunda.bpm.impl.juel.jakarta.el.ELResolver;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Covers {@link SpringProcessApplication}, {@link SpringServletProcessApplication} and
 * {@link SpringProcessApplicationElResolver} without deploying anything.
 */
public class ProcessApplicationUnitTest {

  /** Records lifecycle calls instead of deploying. */
  static class RecordingApplication extends SpringProcessApplication {
    int deployed;
    int undeployed;

    String detectedName() {
      return autodetectProcessApplicationName();
    }

    @Override
    public void deploy() {
      deployed++;
      isDeployed = true;
    }

    @Override
    public void undeploy() {
      undeployed++;
      isDeployed = false;
    }
  }

  @Test
  public void shouldUseBeanNameAsApplicationName() {
    RecordingApplication application = new RecordingApplication();
    application.setBeanName("myApp");

    assertThat(application.detectedName()).isEqualTo("myApp");
  }

  @Test
  public void shouldExposeContextPropertiesAndReference() {
    SpringProcessApplication application = new SpringProcessApplication();
    GenericApplicationContext context = new GenericApplicationContext();
    application.setApplicationContext(context);
    application.setProperties(Collections.singletonMap("k", "v"));

    assertThat(application.getApplicationContext()).isSameAs(context);
    assertThat(application.getProperties()).containsEntry("k", "v");
    assertThat(application.getReference()).isInstanceOf(ProcessApplicationReference.class);
  }

  @Test
  public void shouldDeployOnceOnContextRefreshAndUndeployOnClose() {
    RecordingApplication application = new RecordingApplication();
    GenericApplicationContext context = new GenericApplicationContext();
    application.setApplicationContext(context);

    application.onApplicationEvent(new ContextRefreshedEvent(context));
    application.onApplicationEvent(new ContextRefreshedEvent(context));
    assertThat(application.deployed).isEqualTo(1);

    application.onApplicationEvent(new ContextClosedEvent(context));
    assertThat(application.undeployed).isEqualTo(1);
  }

  @Test
  public void shouldIgnoreEventsOfOtherContexts() {
    RecordingApplication application = new RecordingApplication();
    application.setApplicationContext(new GenericApplicationContext());
    GenericApplicationContext other = new GenericApplicationContext();

    application.onApplicationEvent(new ContextRefreshedEvent(other));
    application.onApplicationEvent(new ContextClosedEvent(other));

    assertThat(application.deployed).isZero();
    assertThat(application.undeployed).isZero();
  }

  @Test
  public void shouldWrapLifecycleFailuresInRuntimeException() {
    SpringProcessApplication application = new SpringProcessApplication() {
      @Override
      public void deploy() {
        throw new IllegalStateException("boom");
      }
    };
    GenericApplicationContext context = new GenericApplicationContext();
    application.setApplicationContext(context);

    assertThatThrownBy(() -> application.onApplicationEvent(new ContextRefreshedEvent(context)))
        .isInstanceOf(RuntimeException.class).hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldKeepBackwardsCompatibleLifecycleMethods() throws Exception {
    RecordingApplication application = new RecordingApplication();

    application.afterPropertiesSet();
    application.destroy();
    application.start();
    application.stop();

    assertThat(application.deployed).isEqualTo(2);
    assertThat(application.undeployed).isEqualTo(2);
  }

  @Test
  public void servletApplicationPublishesContextPathBeforeStarting() throws Exception {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getContextPath()).thenReturn("/ctx");
    int[] deployed = {0};
    SpringServletProcessApplication application = new SpringServletProcessApplication() {
      @Override
      public void deploy() {
        deployed[0]++;
      }
    };
    application.setServletContext(servletContext);

    application.afterPropertiesSet();

    assertThat(application.getProperties()).containsEntry(ProcessApplicationInfo.PROP_SERVLET_CONTEXT_PATH, "/ctx");
    assertThat(deployed[0]).isEqualTo(1);
  }

  @Test
  public void resolverHasSpringPrecedence() {
    assertThat(new SpringProcessApplicationElResolver().getPrecedence())
        .isEqualTo(org.camunda.bpm.application.ProcessApplicationElResolver.SPRING_RESOLVER);
  }

  @Test
  public void resolverUsesApplicationContextOfSpringApplication() {
    SpringProcessApplication application = new SpringProcessApplication();
    application.setApplicationContext(mock(ApplicationContext.class));

    ELResolver resolver = new SpringProcessApplicationElResolver().getElResolver(application);

    assertThat(resolver).isInstanceOf(ApplicationContextElResolver.class);
  }

  @Test
  public void resolverRejectsUnsupportedApplications() {
    org.camunda.bpm.application.AbstractProcessApplication other = mock(org.camunda.bpm.application.AbstractProcessApplication.class);

    assertThat(new SpringProcessApplicationElResolver().getElResolver(other)).isNull();
  }
}
