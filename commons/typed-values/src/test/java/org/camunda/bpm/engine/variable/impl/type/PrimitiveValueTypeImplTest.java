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
package org.camunda.bpm.engine.variable.impl.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.type.PrimitiveValueType;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.Test;

/**
 * Covers the primitive value types.
 *
 * <p>The four numeric ones also convert from the generic NUMBER type, and that conversion refuses
 * anything that would change the value - a long that does not fit into an int, for one. Those
 * guards are what most of the branches here are about.
 */
public class PrimitiveValueTypeImplTest {

  protected static final Map<String, Object> TRANSIENT =
      Collections.singletonMap(ValueType.VALUE_INFO_TRANSIENT, (Object) true);

  // the plain types ---------------------------------------------------------------------------

  @Test
  public void shouldDescribeItself() {
    PrimitiveValueType type = ValueType.STRING;

    assertThat(type.getName()).isEqualTo("string");
    assertThat(type.getJavaType()).isEqualTo(String.class);
    assertThat(type.isPrimitiveValueType()).isTrue();
    assertThat(type.toString()).isEqualTo("PrimitiveValueType[string]");
  }

  @Test
  public void shouldNameTheTypesThatDoNotFollowTheJavaClass() {
    assertThat(ValueType.BYTES.getName()).isEqualTo("bytes");
    assertThat(ValueType.BYTES.getJavaType()).isEqualTo(byte[].class);
    assertThat(ValueType.NULL.getName()).isEqualTo("null");
  }

  @Test
  public void shouldCreateAValueForEveryType() {
    assertThat(ValueType.BOOLEAN.createValue(true, null).getValue()).isEqualTo(true);
    assertThat(ValueType.BYTES.createValue(new byte[] { 1 }, null).getValue())
        .isEqualTo(new byte[] { 1 });
    assertThat(ValueType.DATE.createValue(new Date(1L), null).getValue()).isEqualTo(new Date(1L));
    assertThat(ValueType.DOUBLE.createValue(1.5d, null).getValue()).isEqualTo(1.5d);
    assertThat(ValueType.INTEGER.createValue(1, null).getValue()).isEqualTo(1);
    assertThat(ValueType.LONG.createValue(1L, null).getValue()).isEqualTo(1L);
    assertThat(ValueType.SHORT.createValue((short) 1, null).getValue()).isEqualTo((short) 1);
    assertThat(ValueType.STRING.createValue("text", null).getValue()).isEqualTo("text");
    assertThat(ValueType.NULL.createValue(null, null).getValue()).isNull();
  }

  @Test
  public void shouldCarryTheTransientFlagFromValueInfo() {
    assertThat(ValueType.STRING.createValue("text", TRANSIENT).isTransient()).isTrue();
    assertThat(ValueType.STRING.createValue("text", null).isTransient()).isFalse();
    assertThat(ValueType.NULL.createValue(null, TRANSIENT).isTransient()).isTrue();
  }

  @Test
  public void shouldReportTransientOnlyWhenItIsSet() {
    assertThat(ValueType.STRING.getValueInfo(Variables.stringValue("text"))).isEmpty();
    assertThat(ValueType.STRING.getValueInfo(Variables.stringValue("text", true)))
        .containsEntry(ValueType.VALUE_INFO_TRANSIENT, true);
  }

  // the numeric types -------------------------------------------------------------------------

  @Test
  public void shouldHangTheNumericTypesUnderNumber() {
    assertThat(ValueType.DOUBLE.getParent()).isEqualTo(ValueType.NUMBER);
    assertThat(ValueType.INTEGER.getParent()).isEqualTo(ValueType.NUMBER);
    assertThat(ValueType.LONG.getParent()).isEqualTo(ValueType.NUMBER);
    assertThat(ValueType.SHORT.getParent()).isEqualTo(ValueType.NUMBER);
    assertThat(ValueType.STRING.getParent()).isNull();
  }

  @Test
  public void shouldOnlyConvertFromNumber() {
    TypedValue notANumber = Variables.stringValue("text");

    assertThat(ValueType.DOUBLE.canConvertFromTypedValue(notANumber)).isFalse();
    assertThat(ValueType.INTEGER.canConvertFromTypedValue(notANumber)).isFalse();
    assertThat(ValueType.LONG.canConvertFromTypedValue(notANumber)).isFalse();
    assertThat(ValueType.SHORT.canConvertFromTypedValue(notANumber)).isFalse();

    assertThatThrownBy(() -> ValueType.DOUBLE.convertFromTypedValue(notANumber))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ValueType.INTEGER.convertFromTypedValue(notANumber))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ValueType.LONG.convertFromTypedValue(notANumber))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ValueType.SHORT.convertFromTypedValue(notANumber))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldConvertANumberThatFits() {
    TypedValue number = Variables.numberValue(42);

    assertThat(ValueType.DOUBLE.canConvertFromTypedValue(number)).isTrue();
    assertThat(ValueType.INTEGER.canConvertFromTypedValue(number)).isTrue();
    assertThat(ValueType.LONG.canConvertFromTypedValue(number)).isTrue();
    assertThat(ValueType.SHORT.canConvertFromTypedValue(number)).isTrue();

    assertThat(ValueType.DOUBLE.convertFromTypedValue(number).getValue()).isEqualTo(42d);
    assertThat(ValueType.INTEGER.convertFromTypedValue(number).getValue()).isEqualTo(42);
    assertThat(ValueType.LONG.convertFromTypedValue(number).getValue()).isEqualTo(42L);
    assertThat(ValueType.SHORT.convertFromTypedValue(number).getValue()).isEqualTo((short) 42);
  }

  /** A value that would change when narrowed is refused rather than silently truncated. */
  @Test
  public void shouldRefuseANumberThatWouldNotSurviveNarrowing() {
    TypedValue tooBigForInt = Variables.numberValue(Long.MAX_VALUE);
    TypedValue tooBigForShort = Variables.numberValue(Integer.MAX_VALUE);

    assertThat(ValueType.INTEGER.canConvertFromTypedValue(tooBigForInt)).isFalse();
    assertThat(ValueType.SHORT.canConvertFromTypedValue(tooBigForShort)).isFalse();
    assertThat(ValueType.LONG.canConvertFromTypedValue(tooBigForInt)).isTrue();
  }

  @Test
  public void shouldConvertANullNumber() {
    TypedValue nullNumber = Variables.numberValue(null);

    assertThat(ValueType.DOUBLE.canConvertFromTypedValue(nullNumber)).isTrue();
    assertThat(ValueType.INTEGER.canConvertFromTypedValue(nullNumber)).isTrue();
    assertThat(ValueType.LONG.canConvertFromTypedValue(nullNumber)).isTrue();
    assertThat(ValueType.SHORT.canConvertFromTypedValue(nullNumber)).isTrue();

    assertThat(ValueType.DOUBLE.convertFromTypedValue(nullNumber).getValue()).isNull();
    assertThat(ValueType.INTEGER.convertFromTypedValue(nullNumber).getValue()).isNull();
    assertThat(ValueType.LONG.convertFromTypedValue(nullNumber).getValue()).isNull();
    assertThat(ValueType.SHORT.convertFromTypedValue(nullNumber).getValue()).isNull();
  }

  @Test
  public void shouldKeepTheTransientFlagThroughConversion() {
    TypedValue transientNumber = Variables.numberValue(42, true);

    assertThat(ValueType.DOUBLE.convertFromTypedValue(transientNumber).isTransient()).isTrue();
    assertThat(ValueType.INTEGER.convertFromTypedValue(transientNumber).isTransient()).isTrue();
    assertThat(ValueType.LONG.convertFromTypedValue(transientNumber).isTransient()).isTrue();
    assertThat(ValueType.SHORT.convertFromTypedValue(transientNumber).isTransient()).isTrue();
  }
}
