package org.camunda.bpm.application.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.camunda.bpm.application.AbstractProcessApplication;
import org.camunda.bpm.application.ProcessApplicationElResolver;
import org.camunda.bpm.impl.juel.jakarta.el.ELResolver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DefaultElResolverLookupTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private ClassLoader originalClassLoader;

  public static class NullResolverProvider implements ProcessApplicationElResolver {
    @Override
    public Integer getPrecedence() {
      return 10;
    }

    @Override
    public ELResolver getElResolver(AbstractProcessApplication processApplication) {
      return null;
    }
  }

  public static class SimpleResolverProvider implements ProcessApplicationElResolver {
    @Override
    public Integer getPrecedence() {
      return 20;
    }

    @Override
    public ELResolver getElResolver(AbstractProcessApplication processApplication) {
      return new org.camunda.bpm.impl.juel.jakarta.el.BeanELResolver();
    }
  }

  @Before
  public void rememberClassLoader() {
    originalClassLoader = Thread.currentThread().getContextClassLoader();
  }

  @After
  public void restoreClassLoader() {
    Thread.currentThread().setContextClassLoader(originalClassLoader);
  }

  private void registerProviders(Class<?>... providers) throws Exception {
    Path services = folder.newFolder("META-INF", "services").toPath();
    StringBuilder content = new StringBuilder();
    for (Class<?> provider : providers) {
      content.append(provider.getName()).append('\n');
    }
    Files.write(services.resolve(ProcessApplicationElResolver.class.getName()),
        content.toString().getBytes(StandardCharsets.UTF_8));
    URLClassLoader loader = new URLClassLoader(new URL[] { folder.getRoot().toURI().toURL() }, originalClassLoader);
    Thread.currentThread().setContextClassLoader(loader);
  }

  private AbstractProcessApplication application() {
    return new EmbeddedProcessApplication();
  }

  @Test
  public void shouldReturnNullWhenNoProviderRegistered() {
    assertThat(DefaultElResolverLookup.lookupResolver(application())).isNull();
  }

  @Test
  public void shouldReturnCompositeResolverWhenProvidersRegistered() throws Exception {
    registerProviders(NullResolverProvider.class, SimpleResolverProvider.class);

    ELResolver resolver = DefaultElResolverLookup.lookupResolver(application());

    assertThat(resolver).isInstanceOf(org.camunda.bpm.impl.juel.jakarta.el.CompositeELResolver.class);
  }

  @Test
  public void shouldReturnCompositeResolverWhenOnlyNullProviderRegistered() throws Exception {
    registerProviders(NullResolverProvider.class);

    ELResolver resolver = DefaultElResolverLookup.lookupResolver(application());

    assertThat(resolver).isInstanceOf(org.camunda.bpm.impl.juel.jakarta.el.CompositeELResolver.class);
  }

  @Test
  public void shouldBeInstantiableUtilityClass() {
    assertThat(new DefaultElResolverLookup()).isNotNull();
  }
}
