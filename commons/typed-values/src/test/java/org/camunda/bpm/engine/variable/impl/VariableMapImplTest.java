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
package org.camunda.bpm.engine.variable.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.Test;

/**
 * Covers {@link VariableMapImpl}.
 *
 * <p>The map stores {@link TypedValue} internally but presents plain objects, so values(),
 * entrySet() and the entry wrapper are hand written views over the backing map rather than copies.
 * The tests drive those views, including writing back through them.
 */
public class VariableMapImplTest {

  protected VariableMapImpl map() {
    VariableMapImpl map = new VariableMapImpl();
    map.putValue("name", "ana");
    map.putValue("age", 30);
    return map;
  }

  // construction ----------------------------------------------------------------------------

  @Test
  public void shouldCopyAnotherVariableMap() {
    VariableMapImpl copy = new VariableMapImpl(map());

    assertThat(copy).containsEntry("name", "ana").containsEntry("age", 30);
  }

  @Test
  public void shouldBuildFromAPlainMapOrNothing() {
    Map<String, Object> source = new HashMap<>();
    source.put("name", "ana");

    assertThat(new VariableMapImpl(source)).containsEntry("name", "ana");
    assertThat(new VariableMapImpl((Map<String, Object>) null)).isEmpty();
    assertThat(new VariableMapImpl()).isEmpty();
  }

  // typed access ----------------------------------------------------------------------------

  @Test
  public void shouldReadValueOfTheRequestedType() {
    VariableMapImpl map = map();

    assertThat(map.getValue("name", String.class)).isEqualTo("ana");
    assertThat(map.getValue("age", Integer.class)).isEqualTo(30);
    assertThat(map.getValue("name", Object.class)).isEqualTo("ana");
    assertThat(map.getValue("absent", String.class)).isNull();
  }

  @Test
  public void shouldRefuseReadingValueAsAnUnrelatedType() {
    assertThatThrownBy(() -> map().getValue("name", Integer.class))
        .isInstanceOf(ClassCastException.class)
        .hasMessageContaining("name");
  }

  @Test
  public void shouldKeepTheTypedValueThatWasPut() {
    VariableMapImpl map = new VariableMapImpl();
    TypedValue typed = Variables.stringValue("ana");

    map.putValueTyped("name", typed);

    assertThat(map.<TypedValue>getValueTyped("name")).isSameAs(typed);
    assertThat(map.get("name")).isEqualTo("ana");
  }

  // map behaviour ---------------------------------------------------------------------------

  @Test
  public void shouldBehaveLikeAMap() {
    VariableMapImpl map = map();

    assertThat(map).hasSize(2);
    assertThat(map.isEmpty()).isFalse();
    assertThat(map.containsKey("name")).isTrue();
    assertThat(map.containsKey("absent")).isFalse();
    assertThat(map.keySet()).containsExactlyInAnyOrder("name", "age");

    map.clear();
    assertThat(map.isEmpty()).isTrue();
  }

  @Test
  public void shouldFindValuesByEqualityAndByIdentity() {
    VariableMapImpl map = map();
    map.putValue("nothing", null);

    assertThat(map.containsValue("ana")).isTrue();
    assertThat(map.containsValue(new String("ana"))).isTrue();
    assertThat(map.containsValue(null)).isTrue();
    assertThat(map.containsValue("absent")).isFalse();
  }

  @Test
  public void shouldReturnThePreviousValueOnPutAndRemove() {
    VariableMapImpl map = map();

    assertThat(map.put("name", "budi")).isEqualTo("ana");
    assertThat(map.put("fresh", "value")).isNull();
    assertThat(map.remove("name")).isEqualTo("budi");
    assertThat(map.remove("absent")).isNull();
  }

  @Test
  public void shouldPutAllFromEitherKindOfMap() {
    VariableMapImpl target = new VariableMapImpl();

    target.putAll(map());
    assertThat(target).hasSize(2);

    target.putAll(Collections.<String, Object>singletonMap("extra", 1));
    assertThat(target).containsEntry("extra", 1);

    target.putAll(null);
    assertThat(target).hasSize(3);
  }

  // the backing views -----------------------------------------------------------------------

  @Test
  public void shouldExposeValuesAsAViewOverTheBackingMap() {
    VariableMapImpl map = map();

    assertThat(map.values()).containsExactlyInAnyOrder("ana", 30);
    assertThat(map.values()).hasSize(2);

    Iterator<Object> iterator = map.values().iterator();
    iterator.next();
    iterator.remove();

    assertThat(map).hasSize(1);
  }

  @Test
  public void shouldExposeEntriesAsAViewOverTheBackingMap() {
    VariableMapImpl map = map();

    assertThat(map.entrySet()).hasSize(2);

    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if ("name".equals(entry.getKey())) {
        entry.setValue("budi");
      }
    }

    assertThat(map.get("name")).isEqualTo("budi");
  }

  @Test
  public void shouldRemoveThroughTheEntryIterator() {
    VariableMapImpl map = map();

    Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
    iterator.next();
    iterator.remove();

    assertThat(map).hasSize(1);
  }

  @Test
  public void shouldCompareEntriesByKeyAndValue() {
    VariableMapImpl map = new VariableMapImpl();
    map.putValue("name", "ana");
    Map.Entry<String, Object> entry = map.entrySet().iterator().next();

    assertThat(entry).isEqualTo(Collections.singletonMap("name", (Object) "ana")
        .entrySet().iterator().next());
    assertThat(entry.equals(Collections.singletonMap("name", (Object) "budi")
        .entrySet().iterator().next())).isFalse();
    assertThat(entry.equals(Collections.singletonMap("other", (Object) "ana")
        .entrySet().iterator().next())).isFalse();
    assertThat(entry.equals("not an entry")).isFalse();
    assertThat(entry.hashCode()).isEqualTo("name".hashCode() ^ "ana".hashCode());
  }

  @Test
  public void shouldHashAnEntryHoldingNulls() {
    VariableMapImpl map = new VariableMapImpl();
    map.putValue("nothing", null);
    Map.Entry<String, Object> entry = map.entrySet().iterator().next();

    assertThat(entry.hashCode()).isEqualTo("nothing".hashCode());
  }

  // identity and conversions ------------------------------------------------------------------

  @Test
  public void shouldCompareByPlainValues() {
    assertThat(map()).isEqualTo(map());
    assertThat(map().hashCode()).isEqualTo(map().hashCode());
    assertThat(map().equals(new VariableMapImpl())).isFalse();
  }

  @Test
  public void shouldConvertToAPlainValueMap() {
    assertThat(map().asValueMap()).containsEntry("name", "ana").containsEntry("age", 30);
  }

  @Test
  public void shouldRenderEveryVariableInToString() {
    String rendered = map().toString();

    assertThat(rendered).startsWith("{").endsWith("}");
    assertThat(rendered).contains("name =>").contains("age =>");
  }

  // the VariableContext side --------------------------------------------------------------------

  @Test
  public void shouldServeAsItsOwnVariableContext() {
    VariableMapImpl map = map();

    assertThat(map.asVariableContext()).isSameAs(map);
    assertThat(map.containsVariable("name")).isTrue();
    assertThat(map.containsVariable("absent")).isFalse();
    assertThat(map.resolve("name").getValue()).isEqualTo("ana");
    assertThat(map.resolve("absent")).isNull();
  }
}
