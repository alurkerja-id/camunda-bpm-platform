package org.camunda.bpm.engine.variable.impl.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.type.FileValueType;
import org.camunda.bpm.engine.variable.type.SerializableValueType;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.engine.variable.value.NumberValue;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.camunda.bpm.engine.variable.value.SerializableValue;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Covers {@link ObjectTypeImpl}, {@link FileValueTypeImpl}, {@link AbstractValueTypeImpl} and
 * the number types of {@link PrimitiveValueTypeImpl}.
 */
public class ValueTypeImplsTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  protected ObjectTypeImpl objectType = new ObjectTypeImpl();
  protected FileValueTypeImpl fileType = new FileValueTypeImpl();

  // ObjectTypeImpl

  @Test
  public void objectTypeMetadata() {
    assertThat(objectType.getName()).isEqualTo("object");
    assertThat(objectType.isPrimitiveValueType()).isFalse();
    assertThat(objectType.isAbstract()).isFalse();
    assertThat(objectType.getParent()).isNull();
    assertThat(objectType.toString()).isEqualTo("object");
  }

  @Test
  public void objectTypeCreatesValueWithoutValueInfo() {
    ObjectValue value = (ObjectValue) objectType.createValue("v", null);

    assertThat(value.getValue()).isEqualTo("v");
    assertThat(value.isTransient()).isFalse();
  }

  @Test
  public void objectTypeCreatesValueWithValueInfo() {
    Map<String, Object> info = new HashMap<>();
    info.put(SerializableValueType.VALUE_INFO_SERIALIZATION_DATA_FORMAT, "application/json");
    info.put(ValueType.VALUE_INFO_TRANSIENT, true);

    ObjectValue value = (ObjectValue) objectType.createValue("v", info);

    assertThat(value.getSerializationDataFormat()).isEqualTo("application/json");
    assertThat(value.isTransient()).isTrue();
  }

  @Test
  public void objectTypeCreatesSerializedValue() {
    Map<String, Object> info = new HashMap<>();
    info.put(SerializableValueType.VALUE_INFO_OBJECT_TYPE_NAME, "my.Type");
    info.put(SerializableValueType.VALUE_INFO_SERIALIZATION_DATA_FORMAT, "application/xml");

    SerializableValue value = objectType.createValueFromSerialized("<a/>", info);

    assertThat(value.isDeserialized()).isFalse();
    assertThat(value.getValueSerialized()).isEqualTo("<a/>");
    assertThat(value.getSerializationDataFormat()).isEqualTo("application/xml");
    assertThat(((ObjectValue) value).getObjectTypeName()).isEqualTo("my.Type");
  }

  @Test
  public void objectTypeCreatesSerializedValueWithoutValueInfo() {
    SerializableValue value = objectType.createValueFromSerialized("{}", null);

    assertThat(value.getValueSerialized()).isEqualTo("{}");
    assertThat(value.getSerializationDataFormat()).isNull();
  }

  @Test
  public void objectTypeValueInfoContainsOnlySetProperties() {
    ObjectValue plain = Variables.objectValue("v").create();
    ObjectValue full = Variables.serializedObjectValue("x")
        .serializationDataFormat("fmt").objectTypeName("T").setTransient(true).create();

    assertThat(objectType.getValueInfo(plain)).isEmpty();
    assertThat(objectType.getValueInfo(full))
        .containsEntry(SerializableValueType.VALUE_INFO_SERIALIZATION_DATA_FORMAT, "fmt")
        .containsEntry(SerializableValueType.VALUE_INFO_OBJECT_TYPE_NAME, "T")
        .containsEntry(ValueType.VALUE_INFO_TRANSIENT, true);
  }

  @Test
  public void objectTypeValueInfoRejectsOtherValues() {
    assertThatThrownBy(() -> objectType.getValueInfo(Variables.stringValue("s")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void objectTypeRejectsNonBooleanTransientFlag() {
    Map<String, Object> info = Collections.<String, Object>singletonMap(ValueType.VALUE_INFO_TRANSIENT, "yes");

    assertThatThrownBy(() -> objectType.createValue("v", info))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("transient");
  }

  // AbstractValueTypeImpl

  @Test
  public void abstractTypeEqualsAndHashCodeUseNameAndClass() {
    assertThat(objectType).isEqualTo(objectType);
    assertThat(objectType).isEqualTo(new ObjectTypeImpl());
    assertThat(objectType.hashCode()).isEqualTo(new ObjectTypeImpl().hashCode());
    assertThat(objectType).isNotEqualTo(null);
    assertThat(objectType).isNotEqualTo("object");
    assertThat(objectType).isNotEqualTo(fileType);
    assertThat(new AbstractValueTypeImpl(null) {
      private static final long serialVersionUID = 1L;
      public boolean isPrimitiveValueType() { return false; }
      public TypedValue createValue(Object v, Map<String, Object> i) { return null; }
      public Map<String, Object> getValueInfo(TypedValue t) { return null; }
    }.hashCode()).isEqualTo(31);
  }

  @Test
  public void abstractTypeEqualsHandlesNullNames() {
    AbstractValueTypeImpl nullNamed = new TestType(null);

    assertThat(nullNamed).isEqualTo(new TestType(null));
    assertThat(nullNamed).isNotEqualTo(new TestType("n"));
    assertThat(new TestType("n")).isNotEqualTo(nullNamed);
    assertThat(new TestType("n")).isEqualTo(new TestType("n"));
  }

  @Test
  public void abstractTypeDoesNotSupportConversionByDefault() {
    assertThat(objectType.canConvertFromTypedValue(Variables.stringValue("s"))).isFalse();
    assertThatThrownBy(() -> objectType.convertFromTypedValue(Variables.stringValue("s")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("supports no conversion from type: string");
  }

  @Test
  public void transientFlagDefaultsToFalseWithoutValueInfo() {
    assertThat(objectType.isTransient(null)).isFalse();
    assertThat(objectType.isTransient(new HashMap<String, Object>())).isFalse();
  }

  // FileValueTypeImpl

  @Test
  public void fileTypeRequiresValueInfoAndFilename() {
    assertThatThrownBy(() -> fileType.createValue(new byte[0], null))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("without valueInfo");
    assertThatThrownBy(() -> fileType.createValue(new byte[0], new HashMap<String, Object>()))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("without filename");
  }

  @Test
  public void fileTypeCreatesFromBytesStreamAndFile() throws Exception {
    Map<String, Object> info = new HashMap<>();
    info.put(FileValueType.VALUE_INFO_FILE_NAME, "f.txt");
    info.put(FileValueType.VALUE_INFO_FILE_MIME_TYPE, "text/plain");
    info.put(FileValueType.VALUE_INFO_FILE_ENCODING, "UTF-8");
    info.put(ValueType.VALUE_INFO_TRANSIENT, true);
    byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
    File file = folder.newFile("f.txt");
    Files.write(file.toPath(), bytes);

    for (Object source : new Object[] {bytes, new ByteArrayInputStream(bytes), file}) {
      FileValue value = (FileValue) fileType.createValue(source, info);
      assertThat(value.getFilename()).isEqualTo("f.txt");
      assertThat(value.getMimeType()).isEqualTo("text/plain");
      assertThat(value.getEncoding()).isEqualTo("UTF-8");
      assertThat(value.getEncodingAsCharset()).isEqualTo(StandardCharsets.UTF_8);
      assertThat(value.isTransient()).isTrue();
      assertThat(org.camunda.commons.utils.IoUtil.inputStreamAsByteArray(value.getValue())).isEqualTo(bytes);
    }
  }

  @Test
  public void fileTypeRejectsUnsupportedSourceAndNullProperties() {
    Map<String, Object> info = new HashMap<>();
    info.put(FileValueType.VALUE_INFO_FILE_NAME, "f");

    assertThatThrownBy(() -> fileType.createValue("string", info))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("File, InputStream or byte[]");

    Map<String, Object> nullMime = new HashMap<>(info);
    nullMime.put(FileValueType.VALUE_INFO_FILE_MIME_TYPE, null);
    assertThatThrownBy(() -> fileType.createValue(new byte[0], nullMime))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mime type is null");

    Map<String, Object> nullEncoding = new HashMap<>(info);
    nullEncoding.put(FileValueType.VALUE_INFO_FILE_ENCODING, null);
    assertThatThrownBy(() -> fileType.createValue(new byte[0], nullEncoding))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("encoding is null");
  }

  @Test
  public void fileTypeValueInfo() {
    FileValue minimal = Variables.fileValue("a").create();
    FileValue full = Variables.fileValue("b", true).mimeType("text/plain").encoding("UTF-8").create();

    assertThat(fileType.getValueInfo(minimal)).containsOnlyKeys(FileValueType.VALUE_INFO_FILE_NAME);
    assertThat(fileType.getValueInfo(full))
        .containsEntry(FileValueType.VALUE_INFO_FILE_NAME, "b")
        .containsEntry(FileValueType.VALUE_INFO_FILE_MIME_TYPE, "text/plain")
        .containsEntry(FileValueType.VALUE_INFO_FILE_ENCODING, "UTF-8")
        .containsEntry(ValueType.VALUE_INFO_TRANSIENT, true);
    assertThatThrownBy(() -> fileType.getValueInfo(Variables.stringValue("s")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(fileType.isPrimitiveValueType()).isTrue();
  }

  // Number types

  @Test
  public void numberTypeIsAbstractAndCreatesNumberValues() {
    PrimitiveValueTypeImpl.NumberTypeImpl numberType = new PrimitiveValueTypeImpl.NumberTypeImpl();

    assertThat(numberType.isAbstract()).isTrue();
    NumberValue value = numberType.createValue(5, Collections.<String, Object>singletonMap(ValueType.VALUE_INFO_TRANSIENT, true));
    assertThat(value.getValue()).isEqualTo(5);
    assertThat(value.isTransient()).isTrue();
  }

  @Test
  public void longTypeConvertsFromNumbersOnlyWithoutLoss() {
    PrimitiveValueTypeImpl.LongTypeImpl longType = new PrimitiveValueTypeImpl.LongTypeImpl();

    assertThat(longType.getParent()).isEqualTo(ValueType.NUMBER);
    assertThat(longType.canConvertFromTypedValue(Variables.stringValue("1"))).isFalse();
    assertThat(longType.canConvertFromTypedValue(Variables.numberValue(5))).isTrue();
    assertThat(longType.canConvertFromTypedValue(Variables.numberValue(1.5))).isFalse();
    assertThat(longType.canConvertFromTypedValue(Variables.numberValue(null))).isTrue();
    assertThat(longType.convertFromTypedValue(Variables.numberValue(5)).getValue()).isEqualTo(5L);
    assertThat(longType.convertFromTypedValue(Variables.numberValue(null)).getValue()).isNull();
  }

  protected static class TestType extends AbstractValueTypeImpl {
    private static final long serialVersionUID = 1L;
    TestType(String name) { super(name); }
    public boolean isPrimitiveValueType() { return false; }
    public TypedValue createValue(Object v, Map<String, Object> i) { return null; }
    public Map<String, Object> getValueInfo(TypedValue t) { return null; }
  }
}
