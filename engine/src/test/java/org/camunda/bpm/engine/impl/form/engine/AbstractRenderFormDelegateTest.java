package org.camunda.bpm.engine.impl.form.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.form.FormData;
import org.camunda.bpm.engine.form.FormField;
import org.camunda.bpm.engine.form.FormFieldValidationConstraint;
import org.camunda.bpm.engine.form.FormProperty;
import org.camunda.bpm.engine.impl.form.FormFieldValidationConstraintImpl;
import org.camunda.bpm.engine.impl.form.type.BooleanFormType;
import org.camunda.bpm.engine.impl.form.type.DateFormType;
import org.camunda.bpm.engine.impl.form.type.EnumFormType;
import org.camunda.bpm.engine.impl.form.type.StringFormType;
import org.junit.Test;

public class AbstractRenderFormDelegateTest {

  static class Renderer extends AbstractRenderFormDelegate {
    String render(FormData data) {
      return renderFormData(data);
    }
  }

  private final Renderer renderer = new Renderer();

  private FormField field(String id, String label, String typeName, Object defaultValue,
      FormFieldValidationConstraint... constraints) {
    FormField field = mock(FormField.class);
    when(field.getId()).thenReturn(id);
    when(field.getLabel()).thenReturn(label);
    when(field.getTypeName()).thenReturn(typeName);
    when(field.getDefaultValue()).thenReturn(defaultValue);
    when(field.getValidationConstraints()).thenReturn(Arrays.asList(constraints));
    return field;
  }

  private FormData data(List<FormField> fields, List<FormProperty> properties) {
    FormData data = mock(FormData.class);
    when(data.getFormFields()).thenReturn(fields);
    when(data.getFormProperties()).thenReturn(properties);
    return data;
  }

  private FormFieldValidationConstraint constraint(String name, String configuration) {
    return new FormFieldValidationConstraintImpl(name, configuration);
  }

  @Test
  public void shouldReturnNullForNullFormData() {
    assertThat(renderer.render(null)).isNull();
  }

  @Test
  public void shouldReturnNullForEmptyFieldsAndProperties() {
    assertThat(renderer.render(data(new ArrayList<FormField>(), new ArrayList<FormProperty>()))).isNull();
  }

  @Test
  public void shouldReturnNullForNullFieldsAndProperties() {
    assertThat(renderer.render(data(null, null))).isNull();
  }

  @Test
  public void shouldRenderStringFieldWithLabelDefaultValueAndConstraints() {
    FormField name = field("name", "Your name", StringFormType.TYPE_NAME, 42,
        constraint("required", null), constraint("maxlength", "10"));

    String html = renderer.render(data(Collections.singletonList(name), Collections.<FormProperty>emptyList()));

    assertThat(html)
        .contains("<form name=\"generatedForm\" role=\"form\">")
        .containsPattern("<label for=\"name\">\\s*Your name\\s*</label>")
        .contains("type=\"text\"")
        .contains("cam-variable-type=\"String\"")
        .contains("cam-variable-name=\"name\"")
        .contains("value=\"42\"")
        .contains("maxlength=\"10\"")
        .contains("required")
        .contains("this.generatedForm.name.$invalid && this.generatedForm.name.$dirty")
        .contains("Required field")
        .contains("Only a string value is allowed");
  }

  @Test
  public void shouldSkipLabelAndDefaultValueWhenAbsent() {
    FormField noLabel = field("a", null, StringFormType.TYPE_NAME, null);
    FormField emptyLabel = field("b", "", StringFormType.TYPE_NAME, null);

    String html = renderer.render(data(Arrays.asList(noLabel, emptyLabel), Collections.<FormProperty>emptyList()));

    assertThat(html).doesNotContain("<label").doesNotContain("value=");
  }

  @Test
  public void shouldRenderBooleanAsCheckbox() {
    FormField flag = field("flag", "Flag", BooleanFormType.TYPE_NAME, Boolean.TRUE);

    String html = renderer.render(data(Collections.singletonList(flag), Collections.<FormProperty>emptyList()));

    assertThat(html).contains("type=\"checkbox\"").contains("cam-variable-type=\"Boolean\"").contains("value=\"true\"");
  }

  @Test
  public void shouldRenderEnumAsSelectWithOptionsAndStringTypeMessage() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("k1", "One");
    values.put("k2", "Two");
    FormField choice = field("choice", "Choice", EnumFormType.TYPE_NAME, null);
    when(choice.getType()).thenReturn(new EnumFormType(values));

    String html = renderer.render(data(Collections.singletonList(choice), Collections.<FormProperty>emptyList()));

    assertThat(html)
        .contains("<select")
        .containsPattern("<option value=\"k1\">\\s*One\\s*</option>")
        .containsPattern("<option value=\"k2\">\\s*Two\\s*</option>")
        .contains("cam-variable-type=\"String\"")
        .contains("Only a string value is allowed");
  }

  @Test
  public void shouldRenderEditableDatePickerWithOpenButtonAndScript() {
    FormField date = field("dob", "Birth date", DateFormType.TYPE_NAME, null);

    String html = renderer.render(data(Collections.singletonList(date), Collections.<FormProperty>emptyList()));

    assertThat(html)
        .contains("class=\"input-group\"")
        .contains("datepicker-popup=\"dd/MM/yyyy\"")
        .contains("is-open=\"dateFieldOpeneddob\"")
        .contains("ng-click=\"opendob($event)\"")
        .contains("glyphicon glyphicon-calendar")
        .contains("text/form-script")
        .contains("$scope.opendob = function ($event)")
        .contains("Invalid date format: the date should have the pattern 'dd/MM/yyyy'")
        .contains("cam-variable-type=\"String\"");
  }

  @Test
  public void shouldRenderReadOnlyDateWithoutPicker() {
    FormField date = field("dob", "Birth date", DateFormType.TYPE_NAME, null, constraint("readonly", null));

    String html = renderer.render(data(Collections.singletonList(date), Collections.<FormProperty>emptyList()));

    assertThat(html)
        .contains("class=\"input-group\"")
        .doesNotContain("datepicker-popup")
        .doesNotContain("ng-click")
        .doesNotContain("text/form-script")
        .contains("Invalid date format");
  }

  @Test
  public void shouldRenderDeprecatedFormProperties() {
    FormProperty property = mock(FormProperty.class);
    when(property.getId()).thenReturn("legacy");
    when(property.getName()).thenReturn("Legacy field");
    when(property.getType()).thenReturn(new StringFormType());
    when(property.isRequired()).thenReturn(true);
    when(property.isWritable()).thenReturn(false);

    String html = renderer.render(data(Collections.<FormField>emptyList(), Collections.singletonList(property)));

    assertThat(html)
        .containsPattern("<label for=\"legacy\">\\s*Legacy field\\s*</label>")
        .contains("cam-variable-name=\"legacy\"")
        .contains("required")
        .contains("readonly");
  }

  @Test
  public void shouldDetectFieldKinds() {
    assertThat(renderer.isEnum(field("e", null, EnumFormType.TYPE_NAME, null))).isTrue();
    assertThat(renderer.isEnum(field("e", null, StringFormType.TYPE_NAME, null))).isFalse();
    assertThat(renderer.isDate(field("d", null, DateFormType.TYPE_NAME, null))).isTrue();
    assertThat(renderer.isDate(field("d", null, StringFormType.TYPE_NAME, null))).isFalse();
    assertThat(renderer.isBoolean(field("b", null, BooleanFormType.TYPE_NAME, null))).isTrue();
    assertThat(renderer.isBoolean(field("b", null, StringFormType.TYPE_NAME, null))).isFalse();
  }

  @Test
  public void shouldDetectReadOnly() {
    assertThat(renderer.isReadOnly(field("r", null, StringFormType.TYPE_NAME, null, constraint("readonly", null)))).isTrue();
    assertThat(renderer.isReadOnly(field("r", null, StringFormType.TYPE_NAME, null, constraint("required", null)))).isFalse();

    FormField withoutConstraints = mock(FormField.class);
    when(withoutConstraints.getValidationConstraints()).thenReturn(null);
    assertThat(renderer.isReadOnly(withoutConstraints)).isFalse();
  }
}
