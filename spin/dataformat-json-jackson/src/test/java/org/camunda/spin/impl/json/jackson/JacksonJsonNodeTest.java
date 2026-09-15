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
package org.camunda.spin.impl.json.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.spin.Spin.JSON;

import com.fasterxml.jackson.databind.node.JsonNodeType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.spin.json.SpinJsonDataFormatException;
import org.camunda.spin.json.SpinJsonException;
import org.camunda.spin.json.SpinJsonNode;
import org.camunda.spin.json.SpinJsonPathException;
import org.camunda.spin.json.SpinJsonPropertyException;
import org.junit.Test;

/**
 * Covers {@link JacksonJsonNode} through the Spin entry point.
 *
 * <p>Most methods have two shapes: the happy path on the right kind of node, and a refusal when
 * called on the wrong kind - reading a property off an array, indexing into an object. Both are
 * driven here, along with the negative-index arithmetic that array access does.
 */
public class JacksonJsonNodeTest {

  protected static final String OBJECT = "{\"name\":\"ana\",\"age\":30,\"tall\":true,"
      + "\"nothing\":null,\"score\":1.5,\"tags\":[\"a\",\"b\",\"c\"],\"nested\":{\"k\":\"v\"}}";

  protected static final String ARRAY = "[\"a\",\"b\",\"c\"]";

  protected SpinJsonNode object() {
    return JSON(OBJECT);
  }

  protected SpinJsonNode array() {
    return JSON(ARRAY);
  }

  // node kind ------------------------------------------------------------------------------------

  @Test
  public void shouldReportItsKind() {
    assertThat(object().isObject()).isTrue();
    assertThat(object().isArray()).isFalse();
    assertThat(array().isArray()).isTrue();
    assertThat(object().prop("name").isString()).isTrue();
    assertThat(object().prop("age").isNumber()).isTrue();
    assertThat(object().prop("tall").isBoolean()).isTrue();
    assertThat(object().prop("nothing").isNull()).isTrue();
    assertThat(object().prop("name").isValue()).isTrue();
    assertThat(object().isValue()).isFalse();
  }

  @Test
  public void shouldExposeTheJacksonNodeType() {
    assertThat(((JacksonJsonNode) object()).getNodeType()).isEqualTo(JsonNodeType.OBJECT);
    assertThat(((JacksonJsonNode) array()).getNodeType()).isEqualTo(JsonNodeType.ARRAY);
    assertThat(((JacksonJsonNode) object().prop("name")).getNodeType())
        .isEqualTo(JsonNodeType.STRING);
  }

  @Test
  public void shouldExposeTheDataFormatName() {
    assertThat(object().getDataFormatName()).isEqualTo("application/json");
  }

  // reading values ---------------------------------------------------------------------------------

  @Test
  public void shouldReadTypedValues() {
    SpinJsonNode json = object();

    assertThat(json.prop("name").stringValue()).isEqualTo("ana");
    assertThat(json.prop("age").numberValue()).isEqualTo(30);
    assertThat(json.prop("tall").boolValue()).isTrue();
    assertThat(json.prop("score").numberValue()).isEqualTo(1.5);
  }

  @Test
  public void shouldReadValueWhicheverTypeItIs() {
    assertThat(object().prop("name").value()).isEqualTo("ana");
    assertThat(object().prop("age").value()).isEqualTo(30);
    assertThat(object().prop("tall").value()).isEqualTo(true);
    assertThat(object().prop("nothing").value()).isNull();
  }

  @Test
  public void shouldRefuseReadingAValueOffAContainer() {
    assertThatThrownBy(() -> object().value()).isInstanceOf(SpinJsonDataFormatException.class);
    assertThatThrownBy(() -> array().value()).isInstanceOf(SpinJsonDataFormatException.class);
  }

  @Test
  public void shouldRefuseReadingTheWrongType() {
    SpinJsonNode json = object();

    assertThatThrownBy(() -> json.prop("name").numberValue())
        .isInstanceOf(SpinJsonDataFormatException.class);
    assertThatThrownBy(() -> json.prop("age").stringValue())
        .isInstanceOf(SpinJsonDataFormatException.class);
    assertThatThrownBy(() -> json.prop("name").boolValue())
        .isInstanceOf(SpinJsonDataFormatException.class);
  }

  // properties ---------------------------------------------------------------------------------------

  @Test
  public void shouldTellWhetherAPropertyIsThere() {
    assertThat(object().hasProp("name")).isTrue();
    assertThat(object().hasProp("absent")).isFalse();
  }

  @Test
  public void shouldRefuseReadingAnAbsentProperty() {
    assertThatThrownBy(() -> object().prop("absent"))
        .isInstanceOf(SpinJsonPropertyException.class);
  }

  @Test
  public void shouldListFieldNames() {
    assertThat(object().fieldNames())
        .containsExactly("name", "age", "tall", "nothing", "score", "tags", "nested");
  }

  @Test
  public void shouldSetPropertyOfEveryType() {
    SpinJsonNode json = object();

    json.prop("string", "text");
    json.prop("int", 1);
    json.prop("long", 2L);
    json.prop("float", 1.5f);
    json.prop("number", (Number) 3);
    json.prop("primitiveBoolean", true);
    json.prop("boxedBoolean", Boolean.FALSE);

    assertThat(json.prop("string").stringValue()).isEqualTo("text");
    assertThat(json.prop("int").numberValue()).isEqualTo(1);
    assertThat(json.prop("long").numberValue()).isEqualTo(2L);
    assertThat(json.prop("float").numberValue()).isEqualTo(1.5f);
    assertThat(json.prop("number").numberValue()).isEqualTo(3);
    assertThat(json.prop("primitiveBoolean").boolValue()).isTrue();
    assertThat(json.prop("boxedBoolean").boolValue()).isFalse();
  }

  @Test
  public void shouldSetNullForANullValue() {
    SpinJsonNode json = object();

    json.prop("nulledString", (String) null);
    json.prop("nulledNumber", (Number) null);
    json.prop("nulledBoolean", (Boolean) null);

    assertThat(json.prop("nulledString").isNull()).isTrue();
    assertThat(json.prop("nulledNumber").isNull()).isTrue();
    assertThat(json.prop("nulledBoolean").isNull()).isTrue();
  }

  @Test
  public void shouldSetListMapAndNodeProperties() {
    SpinJsonNode json = object();
    Map<String, Object> map = new HashMap<>();
    map.put("k", "v");

    json.prop("list", Arrays.asList((Object) "a", "b"));
    ((JacksonJsonNode) json).propList("otherList", Arrays.asList((Object) 1, 2));
    json.prop("map", map);
    json.prop("node", JSON("{\"deep\":1}"));

    assertThat(json.prop("list").elements()).hasSize(2);
    assertThat(json.prop("otherList").elements()).hasSize(2);
    assertThat(json.prop("map").prop("k").stringValue()).isEqualTo("v");
    assertThat(json.prop("node").prop("deep").numberValue()).isEqualTo(1);
  }

  /** Jackson has no put(Number), so anything outside long, int and float lands as a float. */
  @Test
  public void shouldStoreAnyOtherNumberAsFloat() {
    SpinJsonNode json = object();

    json.prop("decimal", new java.math.BigDecimal("1.5"));

    assertThat(json.prop("decimal").numberValue()).isEqualTo(1.5f);
  }

  @Test
  public void shouldDeleteProperties() {
    SpinJsonNode json = object();

    json.deleteProp("name");
    assertThat(json.hasProp("name")).isFalse();

    json.deleteProp(Arrays.asList("age", "tall"));
    assertThat(json.hasProp("age")).isFalse();
    assertThat(json.hasProp("tall")).isFalse();
  }

  @Test
  public void shouldRefuseDeletingAnAbsentProperty() {
    assertThatThrownBy(() -> object().deleteProp("absent"))
        .isInstanceOf(SpinJsonPropertyException.class);
  }

  // arrays ---------------------------------------------------------------------------------------------

  @Test
  public void shouldReadElementsAndIndices() {
    SpinJsonNode json = array();

    assertThat(json.elements()).hasSize(3);
    assertThat(json.indexOf("b")).isEqualTo(1);
    assertThat(json.lastIndexOf("b")).isEqualTo(1);
    assertThat(json.contains("b")).isTrue();
    assertThat(json.contains("z")).isFalse();
  }

  @Test
  public void shouldRefuseIndexOfSomethingThatIsNotThere() {
    assertThatThrownBy(() -> array().indexOf("z")).isInstanceOf(SpinJsonException.class);
    assertThatThrownBy(() -> array().lastIndexOf("z")).isInstanceOf(SpinJsonException.class);
  }

  @Test
  public void shouldRefuseArrayOperationsOnAnObject() {
    SpinJsonNode json = object();

    assertThatThrownBy(() -> json.elements()).isInstanceOf(SpinJsonDataFormatException.class);
    assertThatThrownBy(() -> json.indexOf("a")).isInstanceOf(SpinJsonException.class);
    assertThatThrownBy(() -> json.lastIndexOf("a")).isInstanceOf(SpinJsonException.class);
    assertThatThrownBy(() -> json.append("a")).isInstanceOf(SpinJsonException.class);
    assertThatThrownBy(() -> json.removeAt(0)).isInstanceOf(SpinJsonException.class);
  }

  /** fieldNames accepts any container, so an array answers with an empty list rather than refusing. */
  @Test
  public void shouldRefusePropertyReadOnAnArrayButStillListItsFieldNames() {
    SpinJsonNode json = array();

    assertThatThrownBy(() -> json.prop("name")).isInstanceOf(SpinJsonPropertyException.class);
    assertThat(json.fieldNames()).isEmpty();
    assertThatThrownBy(() -> object().prop("name").fieldNames())
        .isInstanceOf(SpinJsonDataFormatException.class);
  }

  @Test
  public void shouldAppendAndInsert() {
    SpinJsonNode json = array();

    json.append("d");
    assertThat(json.elements()).hasSize(4);

    json.insertAt(0, "z");
    assertThat(json.elements().get(0).stringValue()).isEqualTo("z");

    json.insertBefore("a", "y");
    assertThat(json.indexOf("y")).isEqualTo(1);

    json.insertAfter("c", "x");
    assertThat(json.indexOf("x")).isEqualTo(json.indexOf("c") + 1);
  }

  /** A negative index counts back from the end, and is refused once it walks past the start. */
  @Test
  public void shouldInsertAtANegativeIndex() {
    SpinJsonNode json = array();

    json.insertAt(-1, "z");

    assertThat(json.indexOf("z")).isEqualTo(2);
  }

  @Test
  public void shouldRefuseAnIndexOutsideTheArray() {
    assertThatThrownBy(() -> array().insertAt(-10, "z"))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> array().insertAt(10, "z"))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  public void shouldRemoveElements() {
    SpinJsonNode json = JSON("[\"a\",\"b\",\"a\"]");

    json.remove("a");
    assertThat(json.elements()).hasSize(2);
    assertThat(json.indexOf("a")).isEqualTo(1);

    json.removeLast("a");
    assertThat(json.contains("a")).isFalse();
  }

  @Test
  public void shouldRemoveAtIndexIncludingFromTheEnd() {
    SpinJsonNode json = array();

    json.removeAt(0);
    assertThat(json.elements().get(0).stringValue()).isEqualTo("b");

    json.removeAt(-1);
    assertThat(json.elements()).hasSize(1);
  }

  @Test
  public void shouldRefuseInsertingAnUnsupportedType() {
    assertThatThrownBy(() -> array().append(new Object()))
        .isInstanceOf(SpinJsonException.class);
  }

  // json path ----------------------------------------------------------------------------------------------

  @Test
  public void shouldQueryWithJsonPath() {
    assertThat(object().jsonPath("$.name").stringValue()).isEqualTo("ana");
    assertThat(object().jsonPath("$.tags").elementList()).hasSize(3);
  }

  @Test
  public void shouldRefuseAnInvalidJsonPath() {
    assertThatThrownBy(() -> object().jsonPath("$.."))
        .isInstanceOf(SpinJsonPathException.class);
  }

  // mapping ------------------------------------------------------------------------------------------------

  @Test
  public void shouldMapToJavaType() {
    SpinJsonNode json = JSON("[\"a\",\"b\"]");

    List<Object> mapped = json.mapTo(List.class);
    Object byTypeName = json.mapTo("java.util.ArrayList<java.lang.String>");

    assertThat(mapped).containsExactly("a", "b");
    assertThat(byTypeName).isInstanceOf(ArrayList.class);
  }

  @Test
  public void shouldRefuseAnUnknownTypeName() {
    assertThatThrownBy(() -> object().mapTo("no.such.Type"))
        .isInstanceOf(SpinJsonDataFormatException.class);
  }

  @Test
  public void shouldRefuseMappingToAnIncompatibleType() {
    assertThatThrownBy(() -> object().mapTo(Integer.class))
        .isInstanceOf(SpinJsonException.class);
  }

  // rendering ------------------------------------------------------------------------------------------------

  @Test
  public void shouldRenderBackToJson() {
    assertThat(JSON("{\"k\":\"v\"}").toString()).isEqualTo("{\"k\":\"v\"}");
  }

  @Test
  public void shouldExposeTheUnderlyingJacksonNode() {
    assertThat(((JacksonJsonNode) object()).unwrap()).isNotNull();
  }
}
