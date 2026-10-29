package org.camunda.bpm.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Callable;
import org.camunda.bpm.application.ProcessApplicationExecutionException;
import org.camunda.bpm.application.ProcessApplicationInterface;
import org.camunda.bpm.application.ProcessApplicationReference;
import org.junit.After;
import org.junit.Test;

public class AbstractEjbProcessApplicationTest {

  private final ClassLoader original = Thread.currentThread().getContextClassLoader();

  @After
  public void restore() {
    Thread.currentThread().setContextClassLoader(original);
  }

  static class TestEjbPa extends AbstractEjbProcessApplication {
    int ensureReferenceCalls;
    int lookupSelfCalls;
    ProcessApplicationReference reference;
    ProcessApplicationInterface self;
    ClassLoader paClassLoader;

    @Override
    protected void ensureEjbProcessApplicationReferenceInitialized() {
      ensureReferenceCalls++;
    }

    @Override
    protected ProcessApplicationReference getEjbProcessApplicationReference() {
      return reference;
    }

    @Override
    protected ProcessApplicationInterface lookupSelfReference() {
      lookupSelfCalls++;
      return self;
    }

    @Override
    public ClassLoader getProcessApplicationClassloader() {
      return paClassLoader;
    }
  }

  @Test
  public void shouldInitializeLazilyAndReturnEjbReference() {
    TestEjbPa pa = new TestEjbPa();
    pa.reference = org.mockito.Mockito.mock(ProcessApplicationReference.class);
    pa.self = org.mockito.Mockito.mock(ProcessApplicationInterface.class);

    assertThat(pa.getReference()).isSameAs(pa.reference);
    assertThat(pa.selfReference).isSameAs(pa.self);
    assertThat(pa.lookupSelfCalls).isEqualTo(1);

    pa.getReference();
    assertThat(pa.lookupSelfCalls).isEqualTo(1);
    assertThat(pa.ensureReferenceCalls).isEqualTo(2);
  }

  @Test
  public void shouldExposeDefaultBusinessInterface() {
    assertThat(new TestEjbPa().getBusinessInterface()).isEqualTo(ProcessApplicationInterface.class);
  }

  @Test
  public void shouldFailToAutodetectNameOutsideContainer() {
    TestEjbPa pa = new TestEjbPa();

    assertThatThrownBy(pa::autodetectProcessApplicationName)
        .hasMessageContaining("ENGINE-07010")
        .hasCauseInstanceOf(javax.naming.NamingException.class);
  }

  @Test
  public void shouldExecuteWithProcessApplicationClassLoaderAndRestoreIt() throws Exception {
    TestEjbPa pa = new TestEjbPa();
    ClassLoader paLoader = new ClassLoader() { };
    pa.paClassLoader = paLoader;

    ClassLoader seen = pa.execute(() -> Thread.currentThread().getContextClassLoader());

    assertThat(seen).isSameAs(paLoader);
    assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
  }

  @Test
  public void shouldExecuteWithoutSwitchingWhenClassLoaderIsSame() throws Exception {
    TestEjbPa pa = new TestEjbPa();
    pa.paClassLoader = original;

    String result = pa.execute(() -> "done");

    assertThat(result).isEqualTo("done");
    assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
  }

  @Test
  public void shouldWrapExceptionAndRestoreClassLoader() {
    TestEjbPa pa = new TestEjbPa();
    pa.paClassLoader = new ClassLoader() { };
    IllegalStateException failure = new IllegalStateException("boom");
    Callable<String> failing = () -> {
      throw failure;
    };

    assertThatThrownBy(() -> pa.execute(failing))
        .isInstanceOf(ProcessApplicationExecutionException.class)
        .hasCause(failure);
    assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(original);
  }
}
