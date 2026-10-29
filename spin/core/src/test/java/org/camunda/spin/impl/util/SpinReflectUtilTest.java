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
package org.camunda.spin.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.camunda.spin.spi.ExampleCustomDataFormat;
import org.camunda.spin.spi.SpinDataFormatException;
import org.junit.After;
import org.junit.Test;

public class SpinReflectUtilTest {

  protected ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();

  @After
  public void restoreContextClassLoader() {
    Thread.currentThread().setContextClassLoader(originalContextLoader);
  }

  @Test
  public void shouldLoadClassViaContextClassLoader() {
    Class<?> clazz = SpinReflectUtil.loadClass("java.lang.String", new ExampleCustomDataFormat("f"));

    assertThat(clazz).isEqualTo(String.class);
  }

  @Test
  public void shouldFallBackToDataFormatClassLoader() {
    // context class loader that cannot find anything
    Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
    });

    Class<?> clazz = SpinReflectUtil.loadClass(ExampleCustomDataFormat.class.getName(), new ExampleCustomDataFormat("f"));

    assertThat(clazz).isEqualTo(ExampleCustomDataFormat.class);
  }

  @Test
  public void shouldFallBackWhenNoContextClassLoader() {
    Thread.currentThread().setContextClassLoader(null);

    Class<?> clazz = SpinReflectUtil.loadClass(ExampleCustomDataFormat.class.getName(), new ExampleCustomDataFormat("f"));

    assertThat(clazz).isEqualTo(ExampleCustomDataFormat.class);
  }

  @Test
  public void shouldFailForUnknownClass() {
    assertThatThrownBy(() -> SpinReflectUtil.loadClass("com.example.DoesNotExist", new ExampleCustomDataFormat("f")))
      .isInstanceOf(SpinDataFormatException.class)
      .hasMessageContaining("Class com.example.DoesNotExist not found");
  }
}
