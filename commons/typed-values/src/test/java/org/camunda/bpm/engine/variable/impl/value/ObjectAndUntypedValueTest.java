package org.camunda.bpm.engine.variable.impl.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.junit.Test;

/**
 * Covers {@link ObjectValueImpl}, {@link UntypedValueImpl} and the object value builders.
 */
public class ObjectAndUntypedValueTest {

  @Test
  public void deserializedObjectValueExposesValueAndType() {
    ObjectValueImpl value = new ObjectValueImpl("text");

    assertThat(value.isDeserialized()).isTrue();
    assertThat(value.getValue()).isEqualTo("text");
    assertThat(value.getValue(String.class)).isEqualTo("text");
    assertThat(value.getValue(CharSequence.class)).isEqualTo("text");
    assertThat(value.getObjectType()).isEqualTo(String.class);
    assertThat(value.getType()).isEqualTo(ValueType.OBJECT);
    assertThat(value.isTransient()).isFalse();
  }

  @Test
  public void getValueWithIncompatibleTypeFails() {
    ObjectValueImpl value = new ObjectValueImpl("text");

    assertThatThrownBy(() -> value.getValue(Integer.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not of type");
  }

  @Test
  public void nullObjectHasNoObjectType() {
    assertThat(new ObjectValueImpl(null).getObjectType()).isNull();
  }

  @Test
  public void serializedObjectValueCannotBeReadAsObject() {
    ObjectValueImpl value = new ObjectValueImpl(null, "{}", "application/json", "my.Type", false);

    assertThat(value.isDeserialized()).isFalse();
    assertThat(value.getValueSerialized()).isEqualTo("{}");
    assertThat(value.getSerializationDataFormat()).isEqualTo("application/json");
    assertThat(value.getObjectTypeName()).isEqualTo("my.Type");
    assertThatThrownBy(value::getValue).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> value.getValue(String.class)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(value::getObjectType).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void settersAndTransientFlagAreReflected() {
    ObjectValueImpl value = new ObjectValueImpl("x", true);
    assertThat(value.isTransient()).isTrue();

    value.setTransient(false);
    value.setSerializationDataFormat("fmt");
    value.setObjectTypeName("type");
    value.setSerializedValue("1234");

    assertThat(value.isTransient()).isFalse();
    assertThat(value.getSerializationDataFormat()).isEqualTo("fmt");
    assertThat(value.getObjectTypeName()).isEqualTo("type");
    assertThat(value.getValueSerialized()).isEqualTo("1234");
    assertThat(value.toString())
        .contains("serializationDataFormat=fmt")
        .contains("objectTypeName=type")
        .contains("serializedValue=4 chars")
        .contains("isTransient=false");
    assertThat(new ObjectValueImpl("x").toString()).contains("serializedValue=null");
  }

  @Test
  public void serializedBuilderPopulatesAllProperties() {
    ObjectValue value = Variables.serializedObjectValue()
        .serializedValue("<a/>")
        .serializationDataFormat("application/xml")
        .objectTypeName("my.Type")
        .setTransient(true)
        .create();

    assertThat(value.isDeserialized()).isFalse();
    assertThat(value.getValueSerialized()).isEqualTo("<a/>");
    assertThat(value.getSerializationDataFormat()).isEqualTo("application/xml");
    assertThat(value.getObjectTypeName()).isEqualTo("my.Type");
    assertThat(value.isTransient()).isTrue();
  }

  @Test
  public void serializedBuilderAcceptsDataFormatConstant() {
    ObjectValue value = Variables.serializedObjectValue("{}")
        .serializationDataFormat(Variables.SerializationDataFormats.JSON)
        .create();

    assertThat(value.getSerializationDataFormat()).isEqualTo("application/json");
  }

  @Test
  public void serializedBuilderWrapsExistingValue() {
    ObjectValue existing = Variables.serializedObjectValue("x", true).create();

    assertThat(Variables.serializedObjectValue("y").create().isTransient()).isFalse();
    assertThat(existing.isTransient()).isTrue();
    assertThat(new org.camunda.bpm.engine.variable.impl.value.builder.SerializedObjectValueBuilderImpl(existing).create())
        .isSameAs(existing);
  }

  @Test
  public void objectBuilderAppliesFormatAndTransient() {
    ObjectValue json = Variables.objectValue("v").serializationDataFormat(Variables.SerializationDataFormats.JSON).create();
    ObjectValue custom = Variables.objectValue("v", true).serializationDataFormat("custom").create();
    ObjectValue toggled = Variables.objectValue("v").setTransient(true).create();

    assertThat(json.getSerializationDataFormat()).isEqualTo("application/json");
    assertThat(custom.getSerializationDataFormat()).isEqualTo("custom");
    assertThat(custom.isTransient()).isTrue();
    assertThat(toggled.isTransient()).isTrue();
  }

  @Test
  public void objectBuilderWrapsExistingValue() {
    ObjectValue existing = new ObjectValueImpl("v");

    assertThat(new org.camunda.bpm.engine.variable.impl.value.builder.ObjectVariableBuilderImpl(existing).create())
        .isSameAs(existing);
  }

  @Test
  public void untypedValueBasics() {
    UntypedValueImpl value = new UntypedValueImpl("v");

    assertThat(value.getValue()).isEqualTo("v");
    assertThat(value.getType()).isNull();
    assertThat(value.isTransient()).isFalse();
    assertThat(value.toString()).isEqualTo("Untyped value 'v', isTransient = false");

    value.setTransient(true);
    assertThat(value.isTransient()).isTrue();
  }

  @Test
  public void untypedValueEqualsAndHashCode() {
    UntypedValueImpl value = new UntypedValueImpl("v", false);

    assertThat(value).isEqualTo(value);
    assertThat(value).isEqualTo(new UntypedValueImpl("v", false));
    assertThat(value.hashCode()).isEqualTo(new UntypedValueImpl("v", false).hashCode());
    assertThat(value).isNotEqualTo(null);
    assertThat(value).isNotEqualTo("v");
    assertThat(value).isNotEqualTo(new UntypedValueImpl("other", false));
    assertThat(value).isNotEqualTo(new UntypedValueImpl("v", true));
    assertThat(value.hashCode()).isNotEqualTo(new UntypedValueImpl("v", true).hashCode());
    assertThat(new UntypedValueImpl(null)).isEqualTo(new UntypedValueImpl(null));
    assertThat(new UntypedValueImpl(null)).isNotEqualTo(value);
    assertThat(value).isNotEqualTo(new UntypedValueImpl(null));
    assertThat(new UntypedValueImpl(null).hashCode()).isEqualTo(new UntypedValueImpl(null).hashCode());
  }
}
