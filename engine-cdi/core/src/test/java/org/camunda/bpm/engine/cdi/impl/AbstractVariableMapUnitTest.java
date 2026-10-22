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
package org.camunda.bpm.engine.cdi.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.Test;

public class AbstractVariableMapUnitTest {

  private final Map<String, Object> store = new HashMap<>();

  private final AbstractVariableMap map = new AbstractVariableMap() {
    @Override
    protected Object getVariable(String variableName) {
      return store.get(variableName);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T extends TypedValue> T getVariableTyped(String variableName) {
      Object value = store.get(variableName);
      return value == null ? null : (T) Variables.untypedValue(value);
    }

    @Override
    protected void setVariable(String variableName, Object value) {
      store.put(variableName, value);
    }
  };

  @Test
  public void getRejectsNullKeyAndReadsByStringifiedKey() {
    store.put("1", "one");
    assertThatThrownBy(() -> map.get(null)).isInstanceOf(IllegalArgumentException.class);
    assertThat(map.get(1)).isEqualTo("one");
  }

  @Test
  public void getValueChecksType() {
    store.put("n", 5);
    assertThat(map.getValue("n", Integer.class)).isEqualTo(5);
    assertThat(map.getValue("n", Number.class)).isEqualTo(5);
    assertThat(map.getValue("absent", String.class)).isNull();
    assertThatThrownBy(() -> map.getValue("n", String.class))
      .isInstanceOf(ClassCastException.class).hasMessageContaining("'n'");
  }

  @Test
  public void getValueTypedRejectsNullName() {
    store.put("n", 5);
    assertThatThrownBy(() -> map.getValueTyped(null)).isInstanceOf(IllegalArgumentException.class);
    TypedValue typed = map.getValueTyped("n");
    assertThat(typed.getValue()).isEqualTo(5);
  }

  @Test
  public void putReturnsPreviousValueAndRejectsNullKey() {
    assertThat(map.put("k", "v1")).isNull();
    assertThat(map.put("k", "v2")).isEqualTo("v1");
    assertThat(store).containsEntry("k", "v2");
    assertThatThrownBy(() -> map.put(null, "x")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void putAllWritesEveryEntry() {
    Map<String, Object> source = new HashMap<>();
    source.put("a", 1);
    source.put("b", 2);
    map.putAll(source);
    assertThat(store).containsEntry("a", 1).containsEntry("b", 2);
  }

  @Test
  public void putValueReturnsSelf() {
    VariableMap result = map.putValue("a", 1);
    assertThat(result).isSameAs(map);
    assertThat(store).containsEntry("a", 1);
  }

  @Test
  public void putValueTypedStoresTypedValueAndRejectsNullName() {
    TypedValue typed = Variables.stringValue("s");
    assertThat(map.putValueTyped("a", typed)).isSameAs(map);
    assertThat(store).containsEntry("a", typed);
    assertThatThrownBy(() -> map.putValueTyped(null, typed)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void unsupportedOperationsSayWhichOperation() {
    assertThatThrownBy(map::size).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("size()");
    assertThatThrownBy(map::isEmpty).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("isEmpty()");
    assertThatThrownBy(() -> map.containsKey("a")).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("containsKey()");
    assertThatThrownBy(() -> map.containsValue("a")).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("containsValue()");
    assertThatThrownBy(() -> map.remove("a")).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("remove");
    assertThatThrownBy(map::clear).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("clear()");
    assertThatThrownBy(map::keySet).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("keySet()");
    assertThatThrownBy(map::values).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("values()");
    assertThatThrownBy(map::entrySet).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("entrySet()");
    assertThatThrownBy(map::asVariableContext).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("asVariableContext()");
  }
}
