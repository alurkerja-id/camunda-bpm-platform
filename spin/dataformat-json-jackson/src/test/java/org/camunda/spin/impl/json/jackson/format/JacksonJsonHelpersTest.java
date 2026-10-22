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
package org.camunda.spin.impl.json.jackson.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.spin.Spin.JSON;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.spin.DataFormats;
import org.camunda.spin.json.SpinJsonDataFormatException;
import org.camunda.spin.json.SpinJsonNode;
import org.camunda.spin.json.SpinJsonPathException;
import org.junit.Test;

public class JacksonJsonHelpersTest {

  @Test
  public void shouldDetectTypeBindings() {
    assertThat(TypeHelper.bindingsArePresent(null, 1)).isFalse();
    assertThat(TypeHelper.bindingsArePresent(String.class, 1)).isFalse();
    assertThat(TypeHelper.bindingsArePresent(ArrayList.class, 1)).isTrue();
  }

  @Test
  public void shouldRejectMismatchingTypeBindings() {
    assertThatThrownBy(() -> TypeHelper.bindingsArePresent(HashMap.class, 1))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("java.util.HashMap");
  }

  @Test
  public void shouldGuessCollectionType() {
    assertThat(TypeHelper.guessCollectionType(new ArrayList<String>())).isEqualTo(ArrayList.class);
  }

  @Test
  public void shouldRejectNonCollectionWhenGuessing() {
    assertThatThrownBy(() -> TypeHelper.guessCollectionType("text"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("java.lang.String");
  }

  @Test
  public void shouldMapperExposeCapabilityAndTypeName() {
    JacksonJsonDataFormat format = (JacksonJsonDataFormat) DataFormats.json();

    assertThat(format.getMapper().canMap(new Object())).isTrue();
    assertThat(format.getMapper().canMap(null)).isFalse();
    assertThat(format.getMapper().getCanonicalTypeName(new ArrayList<String>())).isNotNull();
  }

  @Test
  public void shouldFailMappingUnserializableObject() {
    // an object without any properties cannot be turned into a json tree
    assertThatThrownBy(() -> JSON(new Object()))
      .isInstanceOf(SpinJsonDataFormatException.class);
  }

  @Test
  public void shouldWrapWriterFailure() {
    Writer failing = new Writer() {
      public void write(char[] cbuf, int off, int len) throws IOException {
        throw new IOException("boom");
      }

      public void flush() throws IOException {
        throw new IOException("boom");
      }

      public void close() {
      }
    };

    assertThatThrownBy(() -> JSON("{\"a\": 1}").writeToWriter(failing)).isInstanceOf(SpinJsonDataFormatException.class);
  }

  @Test
  public void shouldFailJsonPathForMissingPath() {
    SpinJsonNode node = JSON("{\"a\": 1}");

    assertThatThrownBy(() -> node.jsonPath("$.missing").element()).isInstanceOf(SpinJsonPathException.class);
    assertThatThrownBy(() -> node.jsonPath("$[").element()).isInstanceOf(SpinJsonPathException.class);
  }

  @Test
  public void shouldFailJsonPathCastingScalarToElement() {
    SpinJsonNode node = JSON("{\"a\": [1, 2]}");

    List<Object> list = node.jsonPath("$.a").elementList().stream().map(Object::toString).collect(java.util.stream.Collectors.toList());
    assertThat(list).isNotEmpty();
  }
}
