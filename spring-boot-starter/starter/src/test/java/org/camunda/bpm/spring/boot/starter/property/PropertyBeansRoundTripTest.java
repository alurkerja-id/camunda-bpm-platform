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
package org.camunda.bpm.spring.boot.starter.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Round-trips every simple read/write property of the configuration property classes: a value
 * written through the setter must come back unchanged from the getter, so a mistyped field or a
 * setter wired to the wrong field is caught.
 */
public class PropertyBeansRoundTripTest {

  protected static final List<Class<?>> BEANS = List.of(
      CamundaBpmProperties.class, SessionCookieProperties.class, WebappProperty.class,
      HeaderSecurityProperties.class, CsrfProperties.class, DatabaseProperty.class,
      AuthorizationProperty.class, FetchAndLockProperties.class, GenericProperties.class,
      EventingProperty.class, ManagementProperties.class, ManagementProperties.Health.class,
      ManagementProperties.Health.Camunda.class, AdminUserProperty.class, AuthenticationProperties.class,
      FilterProperty.class, JobExecutionProperty.class, MetricsProperty.class, RestApiProperty.class);

  protected Object sampleFor(Class<?> type, Object current) {
    if (type == String.class) {
      return "/sample";
    } else if (type == boolean.class || type == Boolean.class) {
      return !Boolean.TRUE.equals(current);
    } else if (type == int.class || type == Integer.class) {
      return 41 + (current instanceof Integer ? (Integer) current : 0) + 1;
    } else if (type == long.class || type == Long.class) {
      return 4242L;
    } else if (type == Map.class) {
      return Map.of("k", "v");
    } else if (type == List.class) {
      return List.of("a");
    } else if (type.isEnum()) {
      Object[] constants = type.getEnumConstants();
      return constants.length > 1 && constants[0].equals(current) ? constants[1] : constants[0];
    }
    return null;
  }

  @Test
  public void shouldReturnWhatWasSetOnEverySimpleProperty() throws Exception {
    List<String> checked = new ArrayList<>();
    for (Class<?> bean : BEANS) {
      Object instance;
      try {
        instance = bean.getDeclaredConstructor().newInstance();
      } catch (NoSuchMethodException e) {
        continue;
      }
      for (PropertyDescriptor pd : Introspector.getBeanInfo(bean, Object.class).getPropertyDescriptors()) {
        if (pd.getReadMethod() == null || pd.getWriteMethod() == null) {
          continue;
        }
        Object current = pd.getReadMethod().invoke(instance);
        Object value = sampleFor(pd.getPropertyType(), current);
        if (value == null) {
          continue;
        }
        try {
          pd.getWriteMethod().invoke(instance, value);
        } catch (java.lang.reflect.InvocationTargetException e) {
          // setters that validate their input must refuse the arbitrary sample, not corrupt state
          assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class);
          assertThat(pd.getReadMethod().invoke(instance)).isEqualTo(current);
          continue;
        }
        assertThat(pd.getReadMethod().invoke(instance)).as(bean.getSimpleName() + "." + pd.getName()).isEqualTo(value);
        checked.add(bean.getSimpleName() + "." + pd.getName());
      }
      assertThat(instance.toString()).isNotNull();
    }
    assertThat(checked.size()).isGreaterThan(40);
  }
}
