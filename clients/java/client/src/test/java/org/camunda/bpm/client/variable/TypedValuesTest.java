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
package org.camunda.bpm.client.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Date;
import java.util.Map;

import org.camunda.bpm.client.exception.ExternalTaskClientException;
import org.camunda.bpm.client.impl.EngineClient;
import org.camunda.bpm.client.task.impl.ExternalTaskImpl;
import org.camunda.bpm.client.variable.impl.DefaultValueMappers;
import org.camunda.bpm.client.variable.impl.TypedValueField;
import org.camunda.bpm.client.variable.impl.TypedValues;
import org.camunda.bpm.client.variable.impl.VariableValue;
import org.camunda.bpm.client.variable.impl.format.json.JacksonJsonDataFormat;
import org.camunda.bpm.client.variable.impl.mapper.BooleanValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.ByteArrayValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.DateValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.DoubleValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.FileValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.IntegerValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.JsonValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.LongValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.NullValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.ObjectValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.ShortValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.StringValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.XmlValueMapper;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.impl.value.UntypedValueImpl;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class TypedValuesTest {

  protected DefaultValueMappers mappers;
  protected TypedValues typedValues;

  @Before
  public void setUp() {
    mappers = new DefaultValueMappers("application/json");
    mappers.addMapper(new NullValueMapper());
    mappers.addMapper(new BooleanValueMapper());
    mappers.addMapper(new StringValueMapper());
    mappers.addMapper(new DateValueMapper("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
    mappers.addMapper(new ByteArrayValueMapper());
    mappers.addMapper(new IntegerValueMapper());
    mappers.addMapper(new LongValueMapper());
    mappers.addMapper(new ShortValueMapper());
    mappers.addMapper(new DoubleValueMapper());
    mappers.addMapper(new ObjectValueMapper("application/json", new JacksonJsonDataFormat("application/json")));
    mappers.addMapper(new JsonValueMapper());
    mappers.addMapper(new XmlValueMapper());
    mappers.addMapper(new FileValueMapper(mock(EngineClient.class)));
    typedValues = new TypedValues(mappers);
  }

  @Test
  public void shouldSerializePrimitiveUntypedValuesWithCapitalizedTypeNames() {
    VariableMap variables = null;
    Map<String, Object> plain = new java.util.HashMap<>();
    plain.put("s", "text");
    plain.put("i", 5);
    plain.put("l", 6L);
    plain.put("d", 1.5d);
    plain.put("b", true);
    plain.put("n", null);
    plain.put("bytes", new byte[] { 1 });

    Map<String, TypedValueField> result = typedValues.serializeVariables(plain);

    assertThat(result.get("s").getType()).isEqualTo("String");
    assertThat(result.get("s").getValue()).isEqualTo("text");
    assertThat(result.get("i").getType()).isEqualTo("Integer");
    assertThat(result.get("i").getValue()).isEqualTo(5);
    assertThat(result.get("l").getType()).isEqualTo("Long");
    assertThat(result.get("d").getType()).isEqualTo("Double");
    assertThat(result.get("b").getValue()).isEqualTo(true);
    assertThat(result.get("n").getType()).isEqualTo("Null");
    assertThat(result.get("bytes").getType()).isEqualTo("Bytes");
    assertThat(variables).isNull();
  }

  @Test
  public void shouldSerializeVariableMapWithTypedValues() {
    VariableMap variables = Variables.createVariables()
        .putValueTyped("short", Variables.shortValue((short) 3))
        .putValueTyped("date", Variables.dateValue(new Date(0L)))
        .putValueTyped("json", Variables.objectValue("obj").serializationDataFormat("application/json").create());

    Map<String, TypedValueField> result = typedValues.serializeVariables(variables);

    assertThat(result.get("short").getType()).isEqualTo("Short");
    assertThat(result.get("date").getType()).isEqualTo("Date");
    assertThat(result.get("json").getType()).isEqualTo("Object");
    assertThat(result.get("json").getValue()).isEqualTo("\"obj\"");
  }

  @Test
  public void shouldReturnEmptyResultForNullVariables() {
    assertThat(typedValues.serializeVariables(null)).isEmpty();
  }

  @Test
  public void shouldNameVariableWhenSerializationFails() {
    Map<String, Object> variables = Map.<String, Object>of("bad", Variables.objectValue(new Object()).serializationDataFormat("application/xml").create());

    assertThatThrownBy(() -> typedValues.serializeVariables(variables)).isInstanceOf(ExternalTaskClientException.class)
        .hasMessageContaining("bad");
  }

  @Test
  public void shouldRejectAbstractValueTypes() {
    assertThatThrownBy(() -> mappers.findMapperForTypedValue(new UntypedValueImpl(null) {
      private static final long serialVersionUID = 1L;
      @Override
      public org.camunda.bpm.engine.variable.type.ValueType getType() {
        return org.camunda.bpm.engine.variable.type.ValueType.NUMBER;
      }
    })).isInstanceOf(ExternalTaskClientException.class);
  }

  @Test
  public void shouldFailForUnmappableValuesAndFields() {
    DefaultValueMappers empty = new DefaultValueMappers("application/json");
    assertThatThrownBy(() -> empty.findMapperForTypedValue(new UntypedValueImpl("x")))
        .isInstanceOf(ExternalTaskClientException.class);

    TypedValueField unknown = new TypedValueField();
    unknown.setType("nonexistent");
    unknown.setValue("x");
    assertThatThrownBy(() -> mappers.findMapperForTypedValueField(unknown)).isInstanceOf(ExternalTaskClientException.class);
  }

  @Test
  public void shouldWrapAndReadReceivedVariables() {
    ExternalTaskImpl task = new ExternalTaskImpl();
    task.setExecutionId("exec");
    TypedValueField text = new TypedValueField();
    text.setType("String");
    text.setValue("hello");
    TypedValueField number = new TypedValueField();
    number.setType("Integer");
    number.setValue(12);
    TypedValueField object = new TypedValueField();
    object.setType("Object");
    object.setValue("\"payload\"");
    object.setValueInfo(Map.<String, Object>of("serializationDataFormat", "application/json",
        "objectTypeName", "java.lang.String"));

    Map<String, TypedValueField> fields = new java.util.LinkedHashMap<>();
    fields.put("text", text);
    fields.put("number", number);
    fields.put("object", object);
    Map<String, VariableValue> wrapped = typedValues.wrapVariables(task, fields);
    task.setReceivedVariableMap(wrapped);

    assertThat(wrapped).containsOnlyKeys("text", "number", "object");
    assertThat(text.getType()).isEqualTo("string");
    assertThat((Object) task.getVariable("text")).isEqualTo("hello");
    assertThat((Object) task.getVariable("number")).isEqualTo(12);
    assertThat((Object) task.getVariable("object")).isEqualTo("payload");
    assertThat(task.getAllVariables()).containsEntry("text", "hello").containsEntry("number", 12);
    ExternalTaskImpl freshTask = new ExternalTaskImpl();
    freshTask.setReceivedVariableMap(typedValues.wrapVariables(freshTask, Map.of("object", object)));
    ObjectValue serialized = freshTask.getVariableTyped("object", false);
    assertThat(serialized.isDeserialized()).isFalse();
    assertThat(serialized.getValueSerialized()).isEqualTo("\"payload\"");
    assertThat(freshTask.getAllVariablesTyped(false)).containsKey("object");
    assertThat(wrapped.get("text").toString()).contains("variableName=text").contains("executionId=exec");
    assertThat(typedValues.wrapVariables(task, null)).isEmpty();
  }

}
