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
package org.camunda.bpm.engine.impl.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.impl.json.JsonObjectConverter;
import org.junit.Test;

/**
 * {@link JsonUtil} is written to never throw at its callers - a malformed or wrongly typed value
 * yields an empty result and a log line instead. These tests pin both halves of every guard: the
 * value that goes through, and the null, missing member or wrong type that falls back.
 */
public class JsonUtilTest {

  /** Converts between a JSON object and its "name" member. */
  protected static class NameConverter extends JsonObjectConverter<String> {

    @Override
    public JsonObject toJsonObject(String object) {
      JsonObject json = JsonUtil.createObject();
      json.addProperty("name", object);
      return json;
    }

    @Override
    public String toObject(JsonObject json) {
      return JsonUtil.getString(json, "name");
    }
  }

  /** Always refuses to convert, to reach the "converter returned null" branches. */
  protected static class NullConverter extends JsonObjectConverter<String> {

    @Override
    public JsonObject toJsonObject(String object) {
      return null;
    }

    @Override
    public String toObject(JsonObject json) {
      return null;
    }
  }

  protected final JsonObjectConverter<String> converter = new NameConverter();

  // adding fields -----------------------------------------------------------------------------

  @Test
  public void shouldAddTypedFields() {
    JsonObject json = JsonUtil.createObject();

    JsonUtil.addField(json, "string", "text");
    JsonUtil.addField(json, "boolean", Boolean.TRUE);
    JsonUtil.addField(json, "integer", 1);
    JsonUtil.addField(json, "short", (short) 2);
    JsonUtil.addField(json, "long", 3L);
    JsonUtil.addField(json, "double", 4.5d);
    JsonUtil.addNullField(json, "null");

    assertThat(JsonUtil.getString(json, "string")).isEqualTo("text");
    assertThat(JsonUtil.getBoolean(json, "boolean")).isTrue();
    assertThat(JsonUtil.getInt(json, "integer")).isEqualTo(1);
    assertThat(JsonUtil.getInt(json, "short")).isEqualTo(2);
    assertThat(JsonUtil.getLong(json, "long")).isEqualTo(3L);
    assertThat(json.get("double").getAsDouble()).isEqualTo(4.5d);
    assertThat(JsonUtil.isNull(json, "null")).isTrue();
  }

  @Test
  public void shouldIgnoreNullTargetNameOrValueWhenAddingFields() {
    JsonObject json = JsonUtil.createObject();

    JsonUtil.addField(null, "string", "text");
    JsonUtil.addField(json, null, "text");
    JsonUtil.addField(json, "string", (String) null);
    JsonUtil.addField(json, "boolean", (Boolean) null);
    JsonUtil.addField(json, "integer", (Integer) null);
    JsonUtil.addField(json, "short", (Short) null);
    JsonUtil.addField(json, "long", (Long) null);
    JsonUtil.addField(json, "double", (Double) null);
    JsonUtil.addField(json, "array", (JsonArray) null);
    JsonUtil.addNullField(null, "null");
    JsonUtil.addNullField(json, null);

    assertThat(json.entrySet()).isEmpty();
  }

  @Test
  public void shouldAddRawValueOnlyWhenPresent() {
    JsonObject json = JsonUtil.createObject();

    JsonUtil.addFieldRawValue(json, "raw", Collections.singletonMap("k", "v"));
    JsonUtil.addFieldRawValue(json, "skipped", null);

    assertThat(JsonUtil.getObject(json, "raw").entrySet()).hasSize(1);
    assertThat(json.has("skipped")).isFalse();
  }

  @Test
  public void shouldAddListAndArrayFields() {
    JsonObject json = JsonUtil.createObject();

    JsonUtil.addListField(json, "list", Arrays.asList("a", "b"));
    JsonUtil.addArrayField(json, "array", new String[] { "c" });

    assertThat(JsonUtil.asStringList(JsonUtil.getArray(json, "list"))).containsExactly("a", "b");
    assertThat(JsonUtil.asStringList(JsonUtil.getArray(json, "array"))).containsExactly("c");
  }

  @Test
  public void shouldIgnoreNullListAndArrayFields() {
    JsonObject json = JsonUtil.createObject();

    JsonUtil.addListField(null, "list", Collections.<String>emptyList());
    JsonUtil.addListField(json, null, Collections.<String>emptyList());
    JsonUtil.addListField(json, "list", (List<String>) null);
    JsonUtil.addArrayField(null, "array", new String[0]);
    JsonUtil.addArrayField(json, null, new String[0]);
    JsonUtil.addArrayField(json, "array", null);

    assertThat(json.entrySet()).isEmpty();
  }

  @Test
  public void shouldAddDateFieldAsTimestamp() {
    JsonObject json = JsonUtil.createObject();
    Date date = new Date(1234L);

    JsonUtil.addDateField(json, "date", date);
    JsonUtil.addDateField(json, "skipped", null);
    JsonUtil.addDateField(null, "date", date);
    JsonUtil.addDateField(json, null, date);

    assertThat(JsonUtil.getLong(json, "date")).isEqualTo(1234L);
    assertThat(json.entrySet()).hasSize(1);
  }

  @Test
  public void shouldAddDefaultFieldOnlyWhenItDiffersFromTheDefault() {
    JsonObject json = JsonUtil.createObject();

    JsonUtil.addDefaultField(json, "changed", false, Boolean.TRUE);
    JsonUtil.addDefaultField(json, "unchanged", true, Boolean.TRUE);
    JsonUtil.addDefaultField(json, "missing", false, null);
    JsonUtil.addDefaultField(null, "changed", false, Boolean.TRUE);
    JsonUtil.addDefaultField(json, null, false, Boolean.TRUE);

    assertThat(json.entrySet()).hasSize(1);
    assertThat(JsonUtil.getBoolean(json, "changed")).isTrue();
  }

  // converters --------------------------------------------------------------------------------

  @Test
  public void shouldAddAndReadFieldThroughConverter() {
    JsonObject json = JsonUtil.createObject();

    JsonUtil.addField(json, "person", converter, "ana");

    assertThat(JsonUtil.asJavaObject(JsonUtil.getObject(json, "person"), converter))
        .isEqualTo("ana");
  }

  @Test
  public void shouldIgnoreConverterFieldWhenAnythingIsNull() {
    JsonObject json = JsonUtil.createObject();

    JsonUtil.addField(null, "person", converter, "ana");
    JsonUtil.addField(json, null, converter, "ana");
    JsonUtil.addField(json, "person", (JsonObjectConverter<String>) null, "ana");
    JsonUtil.addField(json, "person", converter, null);

    assertThat(json.entrySet()).isEmpty();
    assertThat(JsonUtil.asJavaObject(null, converter)).isNull();
    assertThat(JsonUtil.<String>asJavaObject(JsonUtil.createObject(), null)).isNull();
  }

  @Test
  public void shouldAddElementsToArrayThroughConverter() {
    JsonArray array = JsonUtil.createArray();

    JsonUtil.addElement(array, converter, "ana");
    JsonUtil.addElement(null, converter, "ana");
    JsonUtil.addElement(array, null, "ana");
    JsonUtil.addElement(array, converter, null);
    JsonUtil.addElement(array, new NullConverter(), "ana");

    assertThat(array.size()).isEqualTo(1);
  }

  @Test
  public void shouldAddListFieldThroughConverterSkippingNulls() {
    JsonObject json = JsonUtil.createObject();
    List<String> withHole = new LinkedList<>(Arrays.asList("ana", null, "budi"));

    JsonUtil.addListField(json, "people", converter, withHole);

    assertThat(JsonUtil.getArray(json, "people").size()).isEqualTo(2);
  }

  @Test
  public void shouldSkipEntriesTheConverterRefuses() {
    JsonObject json = JsonUtil.createObject();

    JsonUtil.addListField(json, "people", new NullConverter(), Arrays.asList("ana"));

    assertThat(JsonUtil.getArray(json, "people").size()).isZero();
  }

  @Test
  public void shouldIgnoreConverterListFieldWhenAnythingIsNull() {
    JsonObject json = JsonUtil.createObject();

    JsonUtil.addListField(null, "people", converter, Arrays.asList("ana"));
    JsonUtil.addListField(json, null, converter, Arrays.asList("ana"));
    JsonUtil.addListField(json, "people", (JsonObjectConverter<String>) null, Arrays.asList("ana"));
    JsonUtil.addListField(json, "people", converter, null);

    assertThat(json.entrySet()).isEmpty();
  }

  @Test
  public void shouldReadListThroughConverter() {
    JsonArray array = JsonUtil.createArray();
    JsonUtil.addElement(array, converter, "ana");
    JsonUtil.addElement(array, converter, "budi");
    array.add(new JsonPrimitive("not an object"));

    assertThat(JsonUtil.asList(array, converter)).containsExactly("ana", "budi");
    LinkedList<String> linked = JsonUtil.asList(array, converter, LinkedList::new);
    assertThat(linked).containsExactly("ana", "budi");
    assertThat(JsonUtil.asList(null, converter)).isEmpty();
    assertThat(JsonUtil.asList(array, (JsonObjectConverter<String>) null)).isEmpty();
  }

  @Test
  public void shouldSkipObjectsTheConverterTurnsIntoNull() {
    JsonArray array = JsonUtil.createArray();
    array.add(JsonUtil.createObject());

    assertThat(JsonUtil.asList(array, new NullConverter())).isEmpty();
  }

  // bytes and strings ---------------------------------------------------------------------------

  /**
   * asBytes and asObject(byte[]) go through StringUtil, which reads the engine configuration for
   * the charset, so they are out of reach of a plain unit test and covered by the engine suite.
   */
  @Test
  public void shouldReturnEmptyObjectForUnparseableInput() {
    assertThat(JsonUtil.asObject("not json").entrySet()).isEmpty();
    assertThat(JsonUtil.asObject((String) null).entrySet()).isEmpty();
    assertThat(JsonUtil.asObject("[1,2]").entrySet()).isEmpty();
  }

  @Test
  public void shouldParseJsonString() {
    JsonObject json = JsonUtil.asObject("{\"name\":\"ana\",\"age\":30}");

    assertThat(JsonUtil.getString(json, "name")).isEqualTo("ana");
    assertThat(JsonUtil.getInt(json, "age")).isEqualTo(30);
  }

  // maps and lists ------------------------------------------------------------------------------

  @Test
  public void shouldConvertMapToObjectAndBack() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("name", "ana");
    properties.put("age", 30);
    properties.put("nothing", null);
    properties.put("nested", Collections.singletonMap("k", "v"));
    properties.put("list", Arrays.asList("a", "b"));

    JsonObject json = JsonUtil.asObject(properties);
    Map<String, Object> roundTripped = JsonUtil.asMap(json);

    assertThat(roundTripped).containsEntry("name", "ana");
    assertThat(roundTripped).containsEntry("age", 30);
    assertThat(roundTripped).containsEntry("nothing", null);
    assertThat(roundTripped.get("nested")).isEqualTo(Collections.singletonMap("k", "v"));
    assertThat(roundTripped.get("list")).isEqualTo(Arrays.asList("a", "b"));
  }

  @Test
  public void shouldReturnEmptyMapForNullOrNonObject() {
    assertThat(JsonUtil.asMap(null)).isEmpty();
    assertThat(JsonUtil.asMap(JsonUtil.createArray())).isEmpty();
    assertThat(JsonUtil.asObject((Map<String, Object>) null).entrySet()).isEmpty();
  }

  @Test
  public void shouldConvertArrayToListOfRawValues() {
    JsonArray array = JsonUtil.createArray();
    array.add("text");
    array.add(1);
    array.add(com.google.gson.JsonNull.INSTANCE);
    array.add(JsonUtil.createObject());
    array.add(JsonUtil.createArray());

    List<Object> list = JsonUtil.asList(array);

    assertThat(list).hasSize(5);
    assertThat(list.get(0)).isEqualTo("text");
    assertThat(list.get(1)).isEqualTo(1);
    assertThat(list.get(2)).isNull();
    assertThat(list.get(3)).isEqualTo(Collections.emptyMap());
    assertThat(list.get(4)).isEqualTo(Collections.emptyList());
  }

  @Test
  public void shouldReturnEmptyListForNullOrNonArray() {
    assertThat(JsonUtil.asList((com.google.gson.JsonElement) null)).isEmpty();
    assertThat(JsonUtil.asList(JsonUtil.createObject())).isEmpty();
  }

  /**
   * The entry that cannot become a string is a two element array: JsonUtil catches
   * IllegalStateException, which is what Gson raises there. A JsonObject would raise
   * UnsupportedOperationException instead and escape the guard.
   */
  @Test
  public void shouldConvertToStringList() {
    JsonArray unreadable = JsonUtil.createArray();
    unreadable.add(1);
    unreadable.add(2);

    JsonArray array = JsonUtil.createArray();
    array.add("a");
    array.add(unreadable);

    assertThat(JsonUtil.asStringList(array)).containsExactly("a");
    assertThat(JsonUtil.asStringList(null)).isEmpty();
    assertThat(JsonUtil.asStringList(JsonUtil.createObject())).isEmpty();
  }

  @Test
  public void shouldRenderMapAsString() {
    assertThat(JsonUtil.asString(Collections.singletonMap("k", "v"))).isEqualTo("{\"k\":\"v\"}");
    assertThat(JsonUtil.asString((Map<String, Object>) null)).isEqualTo("{}");
    assertThat(JsonUtil.asString((Object) Collections.singletonList("a"))).isEqualTo("[\"a\"]");
  }

  @Test
  public void shouldBuildArrayFromStringList() {
    assertThat(JsonUtil.asArray(Arrays.asList("a", "b")).size()).isEqualTo(2);
    assertThat(JsonUtil.asArray(null).size()).isZero();
  }

  // reading raw values --------------------------------------------------------------------------

  @Test
  public void shouldReadRawObject() {
    JsonObject json = JsonUtil.asObject("{\"text\":\"a\",\"number\":1,\"nested\":{}}");

    assertThat(JsonUtil.getRawObject(json, "text")).isEqualTo("a");
    assertThat(JsonUtil.getRawObject(json, "number")).isEqualTo(1);
    assertThat(JsonUtil.getRawObject(json, "nested")).isNull();
    assertThat(JsonUtil.getRawObject(json, "absent")).isNull();
    assertThat(JsonUtil.getRawObject(null, "text")).isNull();
    assertThat(JsonUtil.getRawObject(json, null)).isNull();
  }

  @Test
  public void shouldReadPrimitiveOfEveryType() {
    assertThat(JsonUtil.asPrimitiveObject(null)).isNull();
    assertThat(JsonUtil.asPrimitiveObject(new JsonPrimitive("text"))).isEqualTo("text");
    assertThat(JsonUtil.asPrimitiveObject(new JsonPrimitive(true))).isEqualTo(true);
    assertThat(JsonUtil.asPrimitiveObject(new JsonPrimitive(1))).isEqualTo(1);
  }

  /** Numbers arrive lazily parsed from a parsed document, which is the branch worth pinning. */
  @Test
  public void shouldNarrowLazilyParsedNumbersToTheSmallestType() {
    JsonObject json = JsonUtil.asObject("{\"i\":1,\"l\":9999999999,\"d\":1.5}");

    assertThat(JsonUtil.getRawObject(json, "i")).isEqualTo(1);
    assertThat(JsonUtil.getRawObject(json, "l")).isEqualTo(9999999999L);
    assertThat(JsonUtil.getRawObject(json, "d")).isEqualTo(1.5d);
  }

  @Test
  public void shouldParseNumberToTheSmallestFittingType() {
    assertThat(JsonUtil.parseNumber("1")).isEqualTo(1);
    assertThat(JsonUtil.parseNumber("9999999999")).isEqualTo(9999999999L);
    assertThat(JsonUtil.parseNumber("1.5")).isEqualTo(1.5d);
    assertThat(JsonUtil.parseNumber("not a number")).isNull();
    assertThat(JsonUtil.parseNumber(null)).isNull();
  }

  // typed getters ---------------------------------------------------------------------------------

  @Test
  public void shouldReadTypedMembersOrFallBack() {
    JsonObject json = JsonUtil.asObject("{\"b\":true,\"s\":\"text\",\"i\":1,\"l\":2,\"o\":{},\"a\":[]}");

    assertThat(JsonUtil.getBoolean(json, "b")).isTrue();
    assertThat(JsonUtil.getString(json, "s")).isEqualTo("text");
    assertThat(JsonUtil.getString(json, "absent", "fallback")).isEqualTo("fallback");
    assertThat(JsonUtil.getInt(json, "i")).isEqualTo(1);
    assertThat(JsonUtil.getLong(json, "l")).isEqualTo(2L);
    assertThat(JsonUtil.getObject(json, "o").entrySet()).isEmpty();
    assertThat(JsonUtil.getArray(json, "a").size()).isZero();
  }

  @Test
  public void shouldFallBackWhenMemberIsMissingOrNullOrWronglyTyped() {
    // "n" is a two element array - reading it as a scalar raises IllegalStateException, the one
    // Gson exception JsonUtil guards against
    JsonObject json = JsonUtil.asObject("{\"o\":{},\"a\":[],\"n\":[1,2]}");

    assertThat(JsonUtil.getBoolean(null, "b")).isFalse();
    assertThat(JsonUtil.getBoolean(json, null)).isFalse();
    assertThat(JsonUtil.getBoolean(json, "absent")).isFalse();
    assertThat(JsonUtil.getBoolean(json, "n")).isFalse();

    assertThat(JsonUtil.getString(null, "s")).isEmpty();
    assertThat(JsonUtil.getString(json, (String) null)).isEmpty();
    assertThat(JsonUtil.getString(json, "n")).isEmpty();
    assertThat(JsonUtil.getString((com.google.gson.JsonElement) null)).isEmpty();

    assertThat(JsonUtil.getInt(null, "i")).isZero();
    assertThat(JsonUtil.getInt(json, null)).isZero();
    assertThat(JsonUtil.getInt(json, "n")).isZero();

    assertThat(JsonUtil.getLong(null, "l")).isZero();
    assertThat(JsonUtil.getLong(json, null)).isZero();
    assertThat(JsonUtil.getLong(json, "n")).isZero();

    assertThat(JsonUtil.getObject(null, "o").entrySet()).isEmpty();
    assertThat(JsonUtil.getObject(json, (String) null).entrySet()).isEmpty();
    assertThat(JsonUtil.getObject(json, "a").entrySet()).isEmpty();
    assertThat(JsonUtil.getObject((com.google.gson.JsonElement) null).entrySet()).isEmpty();

    assertThat(JsonUtil.getArray(null, "a").size()).isZero();
    assertThat(JsonUtil.getArray(json, (String) null).size()).isZero();
    assertThat(JsonUtil.getArray(json, "o").size()).isZero();
    assertThat(JsonUtil.getArray((com.google.gson.JsonElement) null).size()).isZero();
  }

  @Test
  public void shouldReportNullMembers() {
    JsonObject json = JsonUtil.createObject();
    JsonUtil.addNullField(json, "nothing");
    JsonUtil.addField(json, "something", "a");

    assertThat(JsonUtil.isNull(json, "nothing")).isTrue();
    assertThat(JsonUtil.isNull(json, "something")).isFalse();
    assertThat(JsonUtil.isNull(json, "absent")).isFalse();
    assertThat(JsonUtil.isNull(null, "nothing")).isFalse();
    assertThat(JsonUtil.isNull(json, null)).isFalse();
  }

  @Test
  public void shouldExposeTheSharedMapper() {
    assertThat(JsonUtil.getGsonMapper()).isNotNull();
    assertThat(JsonUtil.createGsonMapper()).isNotNull();
  }
}
