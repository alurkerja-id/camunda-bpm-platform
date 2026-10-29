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

  public static class FakeJndiFactory implements javax.naming.spi.InitialContextFactory {
    static final java.util.Map<String, Object> BINDINGS = new java.util.HashMap<>();

    @Override
    public javax.naming.Context getInitialContext(java.util.Hashtable<?, ?> environment) {
      javax.naming.Context context = org.mockito.Mockito.mock(javax.naming.Context.class);
      try {
        org.mockito.Mockito.when(context.lookup(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> BINDINGS.get(invocation.<String>getArgument(0)));
      } catch (javax.naming.NamingException e) {
        throw new IllegalStateException(e);
      }
      return context;
    }
  }

  private String lookupName(String appName, String moduleName) {
    String previous = System.getProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY);
    System.setProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY, FakeJndiFactory.class.getName());
    FakeJndiFactory.BINDINGS.clear();
    FakeJndiFactory.BINDINGS.put("java:app/AppName", appName);
    FakeJndiFactory.BINDINGS.put("java:module/ModuleName", moduleName);
    try {
      return new TestEjbPa().autodetectProcessApplicationName();
    } finally {
      FakeJndiFactory.BINDINGS.clear();
      if (previous == null) {
        System.clearProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY);
      } else {
        System.setProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY, previous);
      }
    }
  }

  @Test
  public void shouldCombineAppAndModuleNameFromJndi() {
    assertThat(lookupName("ear", "module")).isEqualTo("ear/module");
  }

  @Test
  public void shouldUseAppNameWhenModuleNameEqualsOrIsMissing() {
    assertThat(lookupName("ear", "ear")).isEqualTo("ear");
    assertThat(lookupName("ear", null)).isEqualTo("ear");
  }
}
