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
package org.camunda.bpm.impl.juel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.el.ELException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Covers where the factory takes its configuration from: the tree store constructors,
 * {@code $JAVA_HOME/lib/el.properties}, the {@code jakarta.el.ExpressionFactory} system property and
 * an {@code el.properties} on the context class loader.
 */
public class ExpressionFactorySourcesTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  protected String originalJavaHome;
  protected ClassLoader originalLoader;
  protected Properties originalSystemProperties;

  @Before
  public void rememberEnvironment() {
    originalJavaHome = System.getProperty("java.home");
    originalLoader = Thread.currentThread().getContextClassLoader();
    originalSystemProperties = new Properties();
    originalSystemProperties.putAll(System.getProperties());
  }

  @After
  public void restoreEnvironment() {
    System.setProperty("java.home", originalJavaHome);
    Thread.currentThread().setContextClassLoader(originalLoader);
    for (String name : new String[] { "jakarta.el.ExpressionFactory", ExpressionFactoryImpl.PROP_METHOD_INVOCATIONS,
        ExpressionFactoryImpl.PROP_VAR_ARGS, ExpressionFactoryImpl.PROP_NULL_PROPERTIES }) {
      if (originalSystemProperties.containsKey(name)) {
        System.setProperty(name, originalSystemProperties.getProperty(name));
      } else {
        System.clearProperty(name);
      }
    }
  }

  protected static final String FACTORY_NAME = ExpressionFactoryImpl.class.getName();

  /** Whether a method invocation can be evaluated, which only works if the feature is enabled. */
  protected static boolean supportsMethodInvocations(ExpressionFactoryImpl factory) {
    SimpleContext context = new SimpleContext(new SimpleResolver());
    context.setVariable("text", factory.createValueExpression("abc", String.class));
    try {
      return Integer.valueOf(3).equals(factory.createValueExpression(context, "${text.length()}", Object.class).getValue(context));
    } catch (ELException e) {
      return false;
    }
  }

  protected void writeJavaHomeElProperties(String content) throws IOException {
    Path home = folder.newFolder("java-home").toPath();
    Path lib = Files.createDirectories(home.resolve("lib"));
    Files.write(lib.resolve("el.properties"), content.getBytes(StandardCharsets.ISO_8859_1));
    System.setProperty("java.home", home.toString());
  }

  // tree store constructors ///////////////////////////////////

  @Test
  public void shouldUseGivenTreeStore() {
    ExpressionFactoryImpl factory = new ExpressionFactoryImpl(new TreeStore(new Builder(), new Cache(10)));

    SimpleContext context = new SimpleContext(new SimpleResolver());
    assertThat(factory.createValueExpression(context, "${1 + 2}", Long.class).getValue(context)).isEqualTo(3L);
    assertThat(factory.coerceToType("7", Integer.class)).isEqualTo(7);
  }

  @Test
  public void shouldUseGivenTypeConverterWithTreeStore() {
    TypeConverter converter = new TypeConverterImpl() {
      private static final long serialVersionUID = 1L;

      @Override
      public <T> T convert(Object value, Class<T> type) {
        return type.cast("converted");
      }
    };
    ExpressionFactoryImpl factory = new ExpressionFactoryImpl(new TreeStore(new Builder(), null), converter);

    assertThat(factory.coerceToType("anything", String.class)).isEqualTo("converted");
    assertThat(factory.createValueExpression("x", String.class).getValue(new SimpleContext())).isEqualTo("converted");
  }

  // $JAVA_HOME/lib/el.properties ////////////////////////////////

  @Test
  public void shouldReadDefaultPropertiesFromJavaHomeWhenTheyNameThisFactory() throws IOException {
    writeJavaHomeElProperties("jakarta.el.ExpressionFactory=" + FACTORY_NAME + "\n"
        + ExpressionFactoryImpl.PROP_METHOD_INVOCATIONS + "=true\n");

    assertThat(supportsMethodInvocations(new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE5))).isTrue();
  }

  @Test
  public void shouldIgnoreDefaultPropertiesFromJavaHomeNamingAnotherFactory() throws IOException {
    writeJavaHomeElProperties("jakarta.el.ExpressionFactory=some.other.Factory\n"
        + ExpressionFactoryImpl.PROP_METHOD_INVOCATIONS + "=true\n");

    assertThat(supportsMethodInvocations(new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE5))).isFalse();
  }

  @Test
  public void shouldFailWhenDefaultPropertiesFileCannotBeRead() throws IOException {
    Path home = folder.newFolder("broken-home").toPath();
    // a directory in place of the file: it exists but cannot be opened as a stream
    Files.createDirectories(home.resolve("lib").resolve("el.properties"));
    System.setProperty("java.home", home.toString());

    assertThatThrownBy(() -> new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE5))
        .isInstanceOf(ELException.class)
        .hasMessage("Cannot read default EL properties");
  }

  @Test
  public void shouldIgnoreMissingDefaultPropertiesFile() throws IOException {
    System.setProperty("java.home", folder.newFolder("empty-home").getAbsolutePath());

    assertThat(supportsMethodInvocations(new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE5))).isFalse();
    assertThat(new File(System.getProperty("java.home"), "lib" + File.separator + "el.properties")).doesNotExist();
  }

  // system properties ///////////////////////////////////////////

  @Test
  public void shouldFallBackToSystemPropertiesWhenTheyNameThisFactory() {
    System.setProperty("jakarta.el.ExpressionFactory", FACTORY_NAME);
    System.setProperty(ExpressionFactoryImpl.PROP_METHOD_INVOCATIONS, "true");

    assertThat(supportsMethodInvocations(new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE5))).isTrue();
  }

  @Test
  public void shouldNotUseSystemPropertiesNamingAnotherFactory() {
    System.setProperty("jakarta.el.ExpressionFactory", "some.other.Factory");
    System.setProperty(ExpressionFactoryImpl.PROP_METHOD_INVOCATIONS, "true");

    assertThat(supportsMethodInvocations(new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE5))).isFalse();
  }

  // el.properties on the class path /////////////////////////////

  /** A class loader that serves the given stream for el.properties and nothing else. */
  protected static class ElPropertiesLoader extends ClassLoader {
    protected final InputStream stream;

    ElPropertiesLoader(InputStream stream) {
      super(ExpressionFactorySourcesTest.class.getClassLoader());
      this.stream = stream;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      return "el.properties".equals(name) ? stream : super.getResourceAsStream(name);
    }
  }

  @Test
  public void shouldReadElPropertiesFromContextClassLoader() {
    InputStream stream = new ByteArrayInputStream(
        (ExpressionFactoryImpl.PROP_METHOD_INVOCATIONS + "=true\n").getBytes(StandardCharsets.ISO_8859_1));
    Thread.currentThread().setContextClassLoader(new ElPropertiesLoader(stream));

    assertThat(supportsMethodInvocations(new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE5))).isTrue();
  }

  @Test
  public void shouldFailWhenElPropertiesCannotBeRead() {
    InputStream broken = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("unreadable");
      }

      @Override
      public void close() throws IOException {
        throw new IOException("cannot close");
      }
    };
    Thread.currentThread().setContextClassLoader(new ElPropertiesLoader(broken));

    assertThatThrownBy(() -> new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE5))
        .isInstanceOf(ELException.class)
        .hasMessage("Cannot read EL properties");
  }

  @Test
  public void shouldIgnoreFailureToCloseElProperties() {
    InputStream stream = new ByteArrayInputStream(new byte[0]) {
      @Override
      public void close() throws IOException {
        throw new IOException("cannot close");
      }
    };
    Thread.currentThread().setContextClassLoader(new ElPropertiesLoader(stream));

    assertThat(supportsMethodInvocations(new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE6))).isTrue();
  }

  @Test
  public void shouldFallBackToSystemClassLoaderWhenContextClassLoaderIsDenied() {
    Thread.currentThread().setContextClassLoader(new ClassLoader() {
      @Override
      public InputStream getResourceAsStream(String name) {
        throw new SecurityException("denied");
      }
    });

    assertThat(supportsMethodInvocations(new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE6))).isTrue();
  }

  // plug-in class loading ///////////////////////////////////////

  @Test
  public void shouldLoadPluginClassWithClassForNameWithoutContextClassLoader() {
    Properties properties = new Properties();
    properties.setProperty(TypeConverter.class.getName(), ExpressionFactoryConfigurationTest.CustomConverter.class.getName());
    Thread.currentThread().setContextClassLoader(null);

    ExpressionFactoryImpl factory = new ExpressionFactoryImpl(properties);

    assertThat(factory.coerceToType("5", Integer.class)).isEqualTo(5);
  }

  @Test
  public void shouldReportUnknownPluginClassWithoutContextClassLoader() {
    Properties properties = new Properties();
    properties.setProperty(TypeConverter.class.getName(), "does.not.Exist");
    Thread.currentThread().setContextClassLoader(null);

    assertThatThrownBy(() -> new ExpressionFactoryImpl(properties))
        .isInstanceOf(ELException.class)
        .hasMessage("Class does.not.Exist not found");
  }

  @Test
  public void shouldReportPluginClassThatFailsToLoad() {
    Properties properties = new Properties();
    properties.setProperty(TypeConverter.class.getName(), "any.Name");
    Thread.currentThread().setContextClassLoader(new ClassLoader() {
      @Override
      public Class<?> loadClass(String name) {
        throw new IllegalStateException("loader exploded");
      }
    });

    assertThatThrownBy(() -> new ExpressionFactoryImpl(properties))
        .isInstanceOf(ELException.class)
        .hasMessage("Class any.Name could not be instantiated")
        .hasCauseInstanceOf(IllegalStateException.class);
  }
}
