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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.client.exception.ExternalTaskClientException;
import org.camunda.bpm.client.impl.EngineClient;
import org.camunda.bpm.client.variable.impl.TypedValueField;
import org.camunda.bpm.client.variable.impl.mapper.ByteArrayValueMapper;
import org.camunda.bpm.client.variable.impl.mapper.FileValueMapper;
import org.camunda.bpm.client.variable.impl.value.DeferredFileValueImpl;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.impl.value.UntypedValueImpl;
import org.camunda.bpm.engine.variable.type.FileValueType;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.BytesValue;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.junit.Test;

public class FileAndBytesValueMapperTest {

  protected EngineClient engineClient = mock(EngineClient.class);
  protected FileValueMapper fileMapper = new FileValueMapper(engineClient);
  protected ByteArrayValueMapper bytesMapper = new ByteArrayValueMapper();

  @Test
  public void shouldWriteFileValueWithInfoAndBase64Content() {
    FileValue file = Variables.fileValue("a.txt").file("hi".getBytes(StandardCharsets.UTF_8))
        .mimeType("text/plain").encoding("UTF-8").create();
    TypedValueField field = new TypedValueField();

    fileMapper.writeValue(file, field);

    assertThat(field.getValue()).isEqualTo("aGk=");
    assertThat(field.getValueInfo()).containsEntry(FileValueType.VALUE_INFO_FILE_NAME, "a.txt").containsEntry(FileValueType.VALUE_INFO_FILE_MIME_TYPE, "text/plain")
        .containsEntry(FileValueType.VALUE_INFO_FILE_ENCODING, "UTF-8");
  }

  @Test
  public void shouldWriteFileValueWithoutOptionalInfoOrContent() {
    FileValue file = Variables.fileValue("empty.bin").create();
    TypedValueField field = new TypedValueField();

    fileMapper.writeValue(file, field);

    assertThat(field.getValue()).isNull();
    assertThat(field.getValueInfo()).containsOnlyKeys(FileValueType.VALUE_INFO_FILE_NAME);
  }

  @Test
  public void shouldReadFileValueAsDeferredValueWithInfo() {
    TypedValueField field = new TypedValueField();
    Map<String, Object> info = new HashMap<>();
    info.put(FileValueType.VALUE_INFO_FILE_NAME, "a.txt");
    info.put(FileValueType.VALUE_INFO_FILE_MIME_TYPE, "text/plain");
    info.put(FileValueType.VALUE_INFO_FILE_ENCODING, "UTF-8");
    field.setValueInfo(info);

    FileValue file = fileMapper.readValue(field, false);

    assertThat(file).isInstanceOf(DeferredFileValueImpl.class);
    assertThat(file.getFilename()).isEqualTo("a.txt");
    assertThat(file.getMimeType()).isEqualTo("text/plain");
    assertThat(file.getEncoding()).isEqualTo("UTF-8");
    assertThat(((DeferredFileValueImpl) file).isLoaded()).isFalse();
  }

  @Test
  public void shouldReadFileValueWithoutOptionalInfo() {
    TypedValueField field = new TypedValueField();
    field.setValueInfo(Map.<String, Object>of(FileValueType.VALUE_INFO_FILE_NAME, "a.txt"));

    FileValue file = fileMapper.readValue(field, true);

    assertThat(file.getMimeType()).isNull();
    assertThat(file.getEncoding()).isNull();
  }

  @Test
  public void shouldRefuseConversionFromUntypedValue() {
    assertThatThrownBy(() -> fileMapper.convertToTypedValue(new UntypedValueImpl("x")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void shouldWriteOnlyLoadedOrPlainFileValues() throws Exception {
    DeferredFileValueImpl deferred = new DeferredFileValueImpl("a.txt", engineClient);
    deferred.setVariableName("var");
    deferred.setExecutionId("exec");

    assertThat(fileMapper.canHandleTypedValue(Variables.fileValue("a.txt").create())).isTrue();
    assertThat(fileMapper.canHandleTypedValue(deferred)).isFalse();
    assertThat(fileMapper.canHandleTypedValue(Variables.stringValue("s"))).isFalse();
    assertThat(fileMapper.canHandleTypedValue(new UntypedValueImpl("x"))).isFalse();

    when(engineClient.getLocalBinaryVariable("var", "exec")).thenReturn(new byte[] { 1, 2 });
    try (InputStream in = deferred.getValue()) {
      assertThat(in.readAllBytes()).containsExactly(1, 2);
    }
    assertThat(deferred.isLoaded()).isTrue();
    assertThat(deferred.getExecutionId()).isEqualTo("exec");
    assertThat(deferred.toString()).contains("a.txt").contains("isLoaded=true");
    assertThat(fileMapper.canHandleTypedValue(deferred)).isTrue();
    deferred.getValue();
    verify(engineClient).getLocalBinaryVariable("var", "exec");
  }

  @Test
  public void shouldDecideWhetherFileFieldCanBeRead() {
    TypedValueField field = new TypedValueField();
    field.setType(ValueType.FILE.getName());
    field.setValue("abc");
    assertThat(fileMapper.canHandleTypedValueField(field)).isTrue();
    field.setValue(null);
    assertThat(fileMapper.canHandleTypedValueField(field)).isTrue();
    field.setValue(3);
    assertThat(fileMapper.canHandleTypedValueField(field)).isFalse();
  }

  @Test
  public void shouldRoundTripBytes() {
    TypedValueField field = new TypedValueField();
    bytesMapper.writeValue(Variables.byteArrayValue(new byte[] { 1, 2, 3 }), field);

    assertThat(field.getValue()).isEqualTo("AQID");
    assertThat(bytesMapper.readValue(field).getValue()).containsExactly(1, 2, 3);
  }

  @Test
  public void shouldHandleNullBytes() {
    TypedValueField field = new TypedValueField();
    bytesMapper.writeValue(Variables.byteArrayValue(null), field);

    assertThat(field.getValue()).isNull();
    assertThat(bytesMapper.readValue(field).getValue()).isNull();
  }

  @Test
  public void shouldConvertUntypedByteArrayAndStream() {
    BytesValue fromArray = bytesMapper.convertToTypedValue(new UntypedValueImpl(new byte[] { 9 }));
    BytesValue fromStream = bytesMapper.convertToTypedValue(new UntypedValueImpl(new ByteArrayInputStream(new byte[] { 7, 8 })));

    assertThat(fromArray.getValue()).containsExactly(9);
    assertThat(fromStream.getValue()).containsExactly(7, 8);
  }

  @Test
  public void shouldDecideWhetherBytesCanBeHandled() {
    assertThat(bytesMapper.canHandleTypedValue(Variables.byteArrayValue(new byte[0]))).isTrue();
    assertThat(bytesMapper.canHandleTypedValue(new UntypedValueImpl(new ByteArrayInputStream(new byte[0])))).isTrue();
    assertThat(bytesMapper.canHandleTypedValue(Variables.stringValue("s"))).isFalse();

    TypedValueField field = new TypedValueField();
    field.setType(ValueType.BYTES.getName());
    field.setValue("AQID");
    assertThat(bytesMapper.canHandleTypedValueField(field)).isTrue();
    field.setValue(1);
    assertThat(bytesMapper.canHandleTypedValueField(field)).isFalse();
  }

}
