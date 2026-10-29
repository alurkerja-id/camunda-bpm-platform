package org.camunda.bpm.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.junit.Test;

public class ProcessEngineConfigurationTest {

  private static final String CFG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<beans xmlns=\"http://www.springframework.org/schema/beans\" "
      + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
      + "xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd\">\n"
      + "<bean id=\"processEngineConfiguration\" class=\"org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration\">\n"
      + "<property name=\"processEngineName\" value=\"cfg-test\"/>\n"
      + "<property name=\"mailServerPort\" value=\"2525\"/>\n"
      + "</bean>\n"
      + "<bean id=\"other\" class=\"org.camunda.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration\">\n"
      + "<property name=\"processEngineName\" value=\"other-name\"/>\n"
      + "</bean></beans>";

  private static ByteArrayInputStream stream() {
    return new ByteArrayInputStream(CFG.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void shouldCreateStandaloneConfigurations() {
    assertThat(ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration())
        .isExactlyInstanceOf(StandaloneProcessEngineConfiguration.class);
    assertThat(ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration())
        .isExactlyInstanceOf(StandaloneInMemProcessEngineConfiguration.class);
  }

  @Test
  public void shouldCreateConfigurationFromInputStreamWithDefaultAndNamedBean() {
    ProcessEngineConfiguration byDefault = ProcessEngineConfiguration.createProcessEngineConfigurationFromInputStream(stream());
    assertThat(byDefault).isExactlyInstanceOf(StandaloneInMemProcessEngineConfiguration.class);
    assertThat(byDefault.getProcessEngineName()).isEqualTo("cfg-test");
    assertThat(byDefault.getMailServerPort()).isEqualTo(2525);

    ProcessEngineConfiguration named = ProcessEngineConfiguration.createProcessEngineConfigurationFromInputStream(stream(), "other");
    assertThat(named).isExactlyInstanceOf(StandaloneProcessEngineConfiguration.class);
    assertThat(named.getProcessEngineName()).isEqualTo("other-name");
  }

  @Test
  public void shouldCreateConfigurationFromClasspathResource() {
    ProcessEngineConfiguration byDefault = ProcessEngineConfiguration.createProcessEngineConfigurationFromResource("camunda.cfg.xml");
    assertThat(byDefault).isInstanceOf(ProcessEngineConfiguration.class);
    assertThat(byDefault.getProcessEngineName()).isEqualTo(ProcessEngines.NAME_DEFAULT);

    ProcessEngineConfiguration named = ProcessEngineConfiguration.createProcessEngineConfigurationFromResource(
        "camunda.cfg.xml", "processEngineConfiguration");
    assertThat(named.getProcessEngineName()).isEqualTo(byDefault.getProcessEngineName());
  }

  @Test
  public void shouldCreateConfigurationFromDefaultResource() {
    ProcessEngineConfiguration configuration = ProcessEngineConfiguration.createProcessEngineConfigurationFromResourceDefault();

    assertThat(configuration).isNotNull();
    assertThat(configuration.getProcessEngineName()).isEqualTo(ProcessEngines.NAME_DEFAULT);
  }

  private static Object sampleValue(Class<?> type, boolean flip) {
    if (type == String.class) {
      return flip ? "alpha" : "beta";
    } else if (type == int.class || type == Integer.class) {
      return flip ? 7 : 11;
    } else if (type == long.class || type == Long.class) {
      return flip ? 7L : 11L;
    } else if (type == boolean.class || type == Boolean.class) {
      return flip;
    }
    return null;
  }

  private static Method findGetter(Class<?> clazz, String property, Class<?> type) {
    String suffix = property;
    String[] prefixes = (type == boolean.class || type == Boolean.class)
        ? new String[] { "is", "get", "has" } : new String[] { "get" };
    for (String prefix : prefixes) {
      try {
        Method getter = clazz.getMethod(prefix + suffix);
        if (getter.getParameterCount() == 0) {
          return getter;
        }
      } catch (NoSuchMethodException e) {
        // try next prefix
      }
    }
    return null;
  }

  @Test
  public void shouldRoundTripEveryPlainSetterThatHasAMatchingGetter() throws Exception {
    List<String> mismatches = new ArrayList<>();
    int verified = 0;

    for (boolean flip : new boolean[] { true, false }) {
      ProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
      for (Method setter : ProcessEngineConfiguration.class.getMethods()) {
        if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1
            || Modifier.isStatic(setter.getModifiers()) || setter.getDeclaringClass() != ProcessEngineConfiguration.class) {
          continue;
        }
        Class<?> type = setter.getParameterTypes()[0];
        Object value = sampleValue(type, flip);
        if (value == null) {
          continue;
        }
        Method getter = findGetter(ProcessEngineConfiguration.class, setter.getName().substring(3), type);
        if (getter == null) {
          continue;
        }

        Object returned = setter.invoke(configuration, value);
        Object actual = getter.invoke(configuration);
        verified++;
        if (!value.equals(actual)) {
          mismatches.add(setter.getName() + " set " + value + " but " + getter.getName() + " returned " + actual);
        }
        if (setter.getReturnType() != void.class) {
          assertThat(returned).as(setter.getName() + " should be fluent").isSameAs(configuration);
        }
      }
    }

    assertThat(verified).isGreaterThan(100);
    assertThat(mismatches).isEmpty();
  }
}
