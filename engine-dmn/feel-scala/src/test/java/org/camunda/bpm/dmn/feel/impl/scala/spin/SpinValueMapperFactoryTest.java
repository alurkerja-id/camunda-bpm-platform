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
package org.camunda.bpm.dmn.feel.impl.scala.spin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.camunda.bpm.dmn.feel.impl.FeelException;
import org.camunda.feel.valuemapper.CustomValueMapper;
import org.junit.Test;

public class SpinValueMapperFactoryTest {

  protected final SpinValueMapperFactory factory = new SpinValueMapperFactory();

  public static class ThrowingRuntimeCtor {
    public ThrowingRuntimeCtor() {
      throw new IllegalStateException("boom");
    }
  }

  public static class ThrowingErrorCtor {
    public ThrowingErrorCtor() {
      throw new StackOverflowError("boom");
    }
  }

  public static class NoDefaultCtor {
    public NoDefaultCtor(String arg) {
    }
  }

  @Test
  public void shouldReturnNullWhenSpinPluginIsNotOnClasspath() {
    assertThat(factory.lookupClass()).isNull();
    assertThat(factory.createInstance()).isNull();
  }

  @Test
  public void shouldWrapRuntimeExceptionFromConstructor() {
    assertThatThrownBy(() -> factory.newInstance(ThrowingRuntimeCtor.class))
      .isInstanceOf(FeelException.class)
      .hasMessageContaining("005");
  }

  @Test
  public void shouldWrapErrorThrownByConstructor() {
    // wrapped by reflection in InvocationTargetException => still translated
    assertThatThrownBy(() -> factory.newInstance(ThrowingErrorCtor.class))
      .isInstanceOf(FeelException.class)
      .hasMessageContaining("005");
  }

  @Test
  public void shouldWrapMissingDefaultConstructor() {
    assertThatThrownBy(() -> factory.newInstance(NoDefaultCtor.class))
      .isInstanceOf(FeelException.class)
      .hasMessageContaining("005");
  }

  @Test
  public void shouldWrapClassCastForNonValueMapper() {
    assertThatThrownBy(() -> factory.newInstance(Object.class))
      .isInstanceOf(FeelException.class);
  }
}
