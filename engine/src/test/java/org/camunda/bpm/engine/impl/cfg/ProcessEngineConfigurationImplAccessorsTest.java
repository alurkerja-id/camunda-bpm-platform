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
package org.camunda.bpm.engine.impl.cfg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * Round-trips every plain property of {@link ProcessEngineConfigurationImpl}: a value written through
 * its setter must come back unchanged from the matching getter.
 */
public class ProcessEngineConfigurationImplAccessorsTest {

  /** Setters that normalise, copy or derive their value, so a round-trip is not an identity. */
  private static final Set<String> NOT_IDENTITY = new HashSet<>(Arrays.asList("HistoryCleanupMetricsEnabled"));

  private static Method getterFor(Class<?> type, String property) {
    for (String prefix : new String[] {"get", "is"}) {
      try {
        return type.getMethod(prefix + property);
      } catch (NoSuchMethodException e) {
        // try next prefix
      }
    }
    return null;
  }

  private static Object sampleFor(Class<?> type, Object current) {
    if (type == boolean.class || type == Boolean.class) {
      return current == null ? Boolean.TRUE : !((Boolean) current);
    }
    if (type == int.class || type == Integer.class) {
      return current == null ? 7 : ((Integer) current) + 7;
    }
    if (type == long.class || type == Long.class) {
      return current == null ? 7L : ((Long) current) + 7L;
    }
    if (type == String.class) {
      return "sample-" + (current == null ? "" : current);
    }
    if (type == List.class) {
      return new ArrayList<>();
    }
    if (!type.isPrimitive() && !type.isArray() && !type.isEnum() && !Modifier.isFinal(type.getModifiers())) {
      return mock(type);
    }
    return null;
  }

  @Test
  public void shouldReportHistoryCleanupMetricsOnlyWhenMetricsAreEnabledToo() {
    ProcessEngineConfigurationImpl config = new StandaloneInMemProcessEngineConfiguration();

    config.setMetricsEnabled(false);
    config.setHistoryCleanupMetricsEnabled(true);
    assertThat(config.isHistoryCleanupMetricsEnabled()).isFalse();

    config.setMetricsEnabled(true);
    assertThat(config.isHistoryCleanupMetricsEnabled()).isTrue();

    config.setHistoryCleanupMetricsEnabled(false);
    assertThat(config.isHistoryCleanupMetricsEnabled()).isFalse();
  }

  @Test
  public void shouldReturnWhatWasSet() throws Exception {
    ProcessEngineConfigurationImpl config = new StandaloneInMemProcessEngineConfiguration();
    List<String> mismatches = new ArrayList<>();
    int roundTripped = 0;

    for (Method setter : ProcessEngineConfigurationImpl.class.getMethods()) {
      if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1 || Modifier.isStatic(setter.getModifiers())) {
        continue;
      }
      String property = setter.getName().substring(3);
      Method getter = getterFor(ProcessEngineConfigurationImpl.class, property);
      if (getter == null || NOT_IDENTITY.contains(property) || getter.getReturnType() != setter.getParameterTypes()[0]) {
        continue;
      }
      Object sample = sampleFor(setter.getParameterTypes()[0], getter.invoke(config));
      if (sample == null) {
        continue;
      }
      try {
        setter.invoke(config, sample);
      } catch (java.lang.reflect.InvocationTargetException e) {
        mismatches.add(property + " threw " + e.getCause());
        continue;
      }
      Object actual = getter.invoke(config);
      if (!sample.equals(actual)) {
        mismatches.add(property + ": set <" + sample + "> got <" + actual + ">");
      }
      roundTripped++;
    }

    assertThat(mismatches).isEmpty();
    assertThat(roundTripped).isGreaterThan(200);
  }
}
