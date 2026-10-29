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
package org.camunda.bpm.client.variable.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.client.exception.ValueMapperException;
import org.camunda.bpm.client.spi.DataFormat;
import org.camunda.bpm.client.variable.impl.TypedValueField;
import org.camunda.bpm.client.variable.impl.mapper.ObjectValueMapper;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.impl.value.UntypedValueImpl;
import org.camunda.bpm.engine.variable.type.SerializableValueType;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.junit.Before;
import org.junit.Test;

public class ObjectValueMapperTest {

  protected DataFormat dataFormat;
  protected ObjectValueMapper mapper;

  @Before
  public void setUp() {
    dataFormat = mock(DataFormat.class);
    mapper = new ObjectValueMapper("application/json", dataFormat);
  }

  protected TypedValueField field(Object value, String format, String typeName) {
    TypedValueField field = new TypedValueField();
    field.setType(ValueType.OBJECT.getName());
    field.setValue(value);
    Map<String, Object> info = new HashMap<>();
    info.put(SerializableValueType.VALUE_INFO_SERIALIZATION_DATA_FORMAT, format);
    info.put(SerializableValueType.VALUE_INFO_OBJECT_TYPE_NAME, typeName);
    field.setValueInfo(info);
    return field;
  }

  @Test
  public void shouldSerializeDeserializedValueAndUpdateTypeInfo() {
    when(dataFormat.writeValue("obj")).thenReturn("\"obj\"");
    when(dataFormat.getCanonicalTypeName("obj")).thenReturn("java.lang.String");
    ObjectValue value = Variables.objectValue("obj").create();
    TypedValueField field = new TypedValueField();

    mapper.writeValue(value, field);

    assertThat(field.getValue()).isEqualTo("\"obj\"");
    assertThat(value.getObjectTypeName()).isEqualTo("java.lang.String");
    assertThat(value.getSerializationDataFormat()).isEqualTo("application/json");
    assertThat(value.getValueSerialized()).isEqualTo("\"obj\"");
  }

  @Test
  public void shouldWrapSerializationFailure() {
    when(dataFormat.writeValue("obj")).thenThrow(new IllegalStateException("bad"));

    assertThatThrownBy(() -> mapper.writeValue(Variables.objectValue("obj").create(), new TypedValueField()))
        .isInstanceOf(ValueMapperException.class);
  }

  @Test
  public void shouldKeepSerializedValueAsIs() {
    ObjectValue value = Variables.serializedObjectValue("{}")
        .serializationDataFormat("application/json").objectTypeName("x.Y").create();
    TypedValueField field = new TypedValueField();

    mapper.writeValue(value, field);

    assertThat(field.getValue()).isEqualTo("{}");
    assertThat(value.getObjectTypeName()).isEqualTo("x.Y");
  }

  @Test
  public void shouldRejectSerializedValueWithoutTypeName() {
    ObjectValue value = Variables.serializedObjectValue("{}").serializationDataFormat("application/json").create();

    assertThatThrownBy(() -> mapper.writeValue(value, new TypedValueField())).isInstanceOf(ValueMapperException.class);
  }

  @Test
  public void shouldReadDeserializedValue() {
    when(dataFormat.readValue("{}", "x.Y")).thenReturn("result");

    ObjectValue value = mapper.readValue(field("{}", "application/json", "x.Y"), true);

    assertThat(value.isDeserialized()).isTrue();
    assertThat(value.getValue()).isEqualTo("result");
    assertThat(value.getObjectTypeName()).isEqualTo("x.Y");
  }

  @Test
  public void shouldReadNullValueWithoutCallingDataFormat() {
    ObjectValue value = mapper.readValue(field(null, "application/json", "x.Y"), true);

    assertThat(value.getValue()).isNull();
  }

  @Test
  public void shouldReadSerializedValueWithoutDeserializing() {
    ObjectValue value = mapper.readValue(field("{}", "application/json", "x.Y"), false);

    assertThat(value.isDeserialized()).isFalse();
    assertThat(value.getValueSerialized()).isEqualTo("{}");
    assertThat(value.getObjectTypeName()).isEqualTo("x.Y");
  }

  @Test
  public void shouldWrapDeserializationFailure() {
    when(dataFormat.readValue(anyString(), anyString())).thenThrow(new IllegalStateException("bad"));

    assertThatThrownBy(() -> mapper.readValue(field("{}", "application/json", "x.Y"), true))
        .isInstanceOf(ValueMapperException.class);
  }

  @Test
  public void shouldConvertUntypedValue() {
    ObjectValue value = mapper.convertToTypedValue(new UntypedValueImpl("payload"));

    assertThat(value.getValue()).isEqualTo("payload");
  }

  @Test
  public void shouldDecideWhetherItCanWriteValues() {
    when(dataFormat.canMap("obj")).thenReturn(true);

    assertThat(mapper.canHandleTypedValue(Variables.objectValue("obj").create())).isTrue();
    assertThat(mapper.canHandleTypedValue(Variables.objectValue("obj").serializationDataFormat("application/json").create())).isTrue();
    assertThat(mapper.canHandleTypedValue(Variables.objectValue("obj").serializationDataFormat("application/xml").create())).isFalse();
    assertThat(mapper.canHandleTypedValue(Variables.objectValue(null).create())).isTrue();
    assertThat(mapper.canHandleTypedValue(Variables.serializedObjectValue("{}").serializationDataFormat("application/json").create())).isTrue();
    assertThat(mapper.canHandleTypedValue(Variables.serializedObjectValue("{}").serializationDataFormat("application/xml").create())).isFalse();
    assertThat(mapper.canHandleTypedValue(new UntypedValueImpl("obj"))).isTrue();
    assertThat(mapper.canHandleTypedValue(new UntypedValueImpl(null))).isTrue();
    assertThat(mapper.canHandleTypedValue(new UntypedValueImpl("unmappable"))).isFalse();
    assertThat(mapper.canHandleTypedValue(Variables.stringValue("s"))).isFalse();
  }

  @Test
  public void shouldDecideWhetherItCanReadFields() {
    assertThat(mapper.canHandleTypedValueField(field("{}", "application/json", "x"))).isTrue();
    assertThat(mapper.canHandleTypedValueField(field(null, "application/json", "x"))).isTrue();
    assertThat(mapper.canHandleTypedValueField(field("{}", "application/xml", "x"))).isFalse();
    assertThat(mapper.canHandleTypedValueField(field(5, "application/json", "x"))).isFalse();
    assertThat(mapper.getSerializationDataformat()).isEqualTo("application/json");
  }

}
