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
package org.camunda.bpm.impl.juel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.el.ELException;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.Test;

/**
 * Covers the coercion rules of EL 2.1 section 1.17 as implemented by {@link TypeConverterImpl}.
 *
 * <p>Every coercion has the same shape - null, empty string, exact type, Number, String, Character,
 * anything else - so the tests are grouped per target type and walk that shape.
 */
public class TypeConverterImplTest {

  protected enum Color {
    RED, GREEN
  }

  /** A type the JDK has no PropertyEditor for. */
  protected static class Unconvertible {
  }

  protected final TypeConverterImpl converter = new TypeConverterImpl();

  // boolean ---------------------------------------------------------------------------------

  @Test
  public void shouldCoerceToBoolean() {
    assertThat(converter.coerceToBoolean(null)).isFalse();
    assertThat(converter.coerceToBoolean("")).isFalse();
    assertThat(converter.coerceToBoolean(Boolean.TRUE)).isTrue();
    assertThat(converter.coerceToBoolean("true")).isTrue();
    assertThat(converter.coerceToBoolean("no")).isFalse();
  }

  @Test
  public void shouldRejectBooleanCoercionOfUnsupportedType() {
    assertThatThrownBy(() -> converter.coerceToBoolean(1))
        .isInstanceOf(ELException.class);
  }

  // character -------------------------------------------------------------------------------

  @Test
  public void shouldCoerceToCharacter() {
    assertThat(converter.coerceToCharacter(null)).isEqualTo((char) 0);
    assertThat(converter.coerceToCharacter("")).isEqualTo((char) 0);
    assertThat(converter.coerceToCharacter('x')).isEqualTo('x');
    assertThat(converter.coerceToCharacter(65)).isEqualTo('A');
    assertThat(converter.coerceToCharacter("abc")).isEqualTo('a');
  }

  @Test
  public void shouldRejectCharacterCoercionOfUnsupportedType() {
    assertThatThrownBy(() -> converter.coerceToCharacter(Boolean.TRUE))
        .isInstanceOf(ELException.class);
  }

  // BigDecimal ------------------------------------------------------------------------------

  @Test
  public void shouldCoerceToBigDecimal() {
    assertThat(converter.coerceToBigDecimal(null)).isEqualByComparingTo("0");
    assertThat(converter.coerceToBigDecimal("")).isEqualByComparingTo("0");
    assertThat(converter.coerceToBigDecimal(new BigDecimal("2.5"))).isEqualByComparingTo("2.5");
    assertThat(converter.coerceToBigDecimal(BigInteger.TEN)).isEqualByComparingTo("10");
    assertThat(converter.coerceToBigDecimal(3)).isEqualByComparingTo("3");
    assertThat(converter.coerceToBigDecimal("1.25")).isEqualByComparingTo("1.25");
    assertThat(converter.coerceToBigDecimal('A')).isEqualByComparingTo("65");
  }

  @Test
  public void shouldRejectBigDecimalCoercionOfMalformedStringAndUnsupportedType() {
    assertThatThrownBy(() -> converter.coerceToBigDecimal("x"))
        .isInstanceOf(ELException.class);
    assertThatThrownBy(() -> converter.coerceToBigDecimal(Boolean.TRUE))
        .isInstanceOf(ELException.class);
  }

  // BigInteger ------------------------------------------------------------------------------

  @Test
  public void shouldCoerceToBigInteger() {
    assertThat(converter.coerceToBigInteger(null)).isEqualTo(BigInteger.ZERO);
    assertThat(converter.coerceToBigInteger("")).isEqualTo(BigInteger.ZERO);
    assertThat(converter.coerceToBigInteger(BigInteger.TEN)).isEqualTo(BigInteger.TEN);
    assertThat(converter.coerceToBigInteger(new BigDecimal("2.9"))).isEqualTo(BigInteger.valueOf(2));
    assertThat(converter.coerceToBigInteger(7L)).isEqualTo(BigInteger.valueOf(7));
    assertThat(converter.coerceToBigInteger("12")).isEqualTo(BigInteger.valueOf(12));
    assertThat(converter.coerceToBigInteger('A')).isEqualTo(BigInteger.valueOf(65));
  }

  @Test
  public void shouldRejectBigIntegerCoercionOfMalformedStringAndUnsupportedType() {
    assertThatThrownBy(() -> converter.coerceToBigInteger("x"))
        .isInstanceOf(ELException.class);
    assertThatThrownBy(() -> converter.coerceToBigInteger(Boolean.TRUE))
        .isInstanceOf(ELException.class);
  }

  // floating point --------------------------------------------------------------------------

  @Test
  public void shouldCoerceToDouble() {
    assertThat(converter.coerceToDouble(null)).isEqualTo(0d);
    assertThat(converter.coerceToDouble("")).isEqualTo(0d);
    assertThat(converter.coerceToDouble(1.5d)).isEqualTo(1.5d);
    assertThat(converter.coerceToDouble(2)).isEqualTo(2d);
    assertThat(converter.coerceToDouble("3.5")).isEqualTo(3.5d);
    assertThat(converter.coerceToDouble('A')).isEqualTo(65d);
  }

  @Test
  public void shouldRejectDoubleCoercionOfMalformedStringAndUnsupportedType() {
    assertThatThrownBy(() -> converter.coerceToDouble("x")).isInstanceOf(ELException.class);
    assertThatThrownBy(() -> converter.coerceToDouble(Boolean.TRUE)).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldCoerceToFloat() {
    assertThat(converter.coerceToFloat(null)).isEqualTo(0f);
    assertThat(converter.coerceToFloat("")).isEqualTo(0f);
    assertThat(converter.coerceToFloat(1.5f)).isEqualTo(1.5f);
    assertThat(converter.coerceToFloat(2)).isEqualTo(2f);
    assertThat(converter.coerceToFloat("3.5")).isEqualTo(3.5f);
    assertThat(converter.coerceToFloat('A')).isEqualTo(65f);
  }

  @Test
  public void shouldRejectFloatCoercionOfMalformedStringAndUnsupportedType() {
    assertThatThrownBy(() -> converter.coerceToFloat("x")).isInstanceOf(ELException.class);
    assertThatThrownBy(() -> converter.coerceToFloat(Boolean.TRUE)).isInstanceOf(ELException.class);
  }

  // integral --------------------------------------------------------------------------------

  @Test
  public void shouldCoerceToLong() {
    assertThat(converter.coerceToLong(null)).isZero();
    assertThat(converter.coerceToLong("")).isZero();
    assertThat(converter.coerceToLong(5L)).isEqualTo(5L);
    assertThat(converter.coerceToLong(2.9d)).isEqualTo(2L);
    assertThat(converter.coerceToLong("12")).isEqualTo(12L);
    assertThat(converter.coerceToLong('A')).isEqualTo(65L);
  }

  @Test
  public void shouldRejectLongCoercionOfMalformedStringAndUnsupportedType() {
    assertThatThrownBy(() -> converter.coerceToLong("x")).isInstanceOf(ELException.class);
    assertThatThrownBy(() -> converter.coerceToLong(Boolean.TRUE)).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldCoerceToInteger() {
    assertThat(converter.coerceToInteger(null)).isZero();
    assertThat(converter.coerceToInteger("")).isZero();
    assertThat(converter.coerceToInteger(5)).isEqualTo(5);
    assertThat(converter.coerceToInteger(2.9d)).isEqualTo(2);
    assertThat(converter.coerceToInteger("12")).isEqualTo(12);
    assertThat(converter.coerceToInteger('A')).isEqualTo(65);
  }

  @Test
  public void shouldRejectIntegerCoercionOfMalformedStringAndUnsupportedType() {
    assertThatThrownBy(() -> converter.coerceToInteger("x")).isInstanceOf(ELException.class);
    assertThatThrownBy(() -> converter.coerceToInteger(Boolean.TRUE))
        .isInstanceOf(ELException.class);
  }

  @Test
  public void shouldCoerceToShort() {
    assertThat(converter.coerceToShort(null)).isEqualTo((short) 0);
    assertThat(converter.coerceToShort("")).isEqualTo((short) 0);
    assertThat(converter.coerceToShort((short) 5)).isEqualTo((short) 5);
    assertThat(converter.coerceToShort(2.9d)).isEqualTo((short) 2);
    assertThat(converter.coerceToShort("12")).isEqualTo((short) 12);
    assertThat(converter.coerceToShort('A')).isEqualTo((short) 65);
  }

  @Test
  public void shouldRejectShortCoercionOfMalformedStringAndUnsupportedType() {
    assertThatThrownBy(() -> converter.coerceToShort("x")).isInstanceOf(ELException.class);
    assertThatThrownBy(() -> converter.coerceToShort(Boolean.TRUE)).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldCoerceToByte() {
    assertThat(converter.coerceToByte(null)).isEqualTo((byte) 0);
    assertThat(converter.coerceToByte("")).isEqualTo((byte) 0);
    assertThat(converter.coerceToByte((byte) 5)).isEqualTo((byte) 5);
    assertThat(converter.coerceToByte(2.9d)).isEqualTo((byte) 2);
    assertThat(converter.coerceToByte("12")).isEqualTo((byte) 12);
    assertThat(converter.coerceToByte('A')).isEqualTo((byte) 65);
  }

  @Test
  public void shouldRejectByteCoercionOfMalformedStringAndUnsupportedType() {
    assertThatThrownBy(() -> converter.coerceToByte("x")).isInstanceOf(ELException.class);
    assertThatThrownBy(() -> converter.coerceToByte(Boolean.TRUE)).isInstanceOf(ELException.class);
  }

  // string and enum -------------------------------------------------------------------------

  @Test
  public void shouldCoerceToString() {
    assertThat(converter.coerceToString(null)).isEmpty();
    assertThat(converter.coerceToString("text")).isEqualTo("text");
    assertThat(converter.coerceToString(Color.RED)).isEqualTo("RED");
    assertThat(converter.coerceToString(12)).isEqualTo("12");
  }

  @Test
  public void shouldCoerceToEnum() {
    assertThat(converter.coerceToEnum(null, Color.class)).isNull();
    assertThat(converter.coerceToEnum("", Color.class)).isNull();
    assertThat(converter.coerceToEnum(Color.GREEN, Color.class)).isEqualTo(Color.GREEN);
    assertThat(converter.coerceToEnum("RED", Color.class)).isEqualTo(Color.RED);
  }

  @Test
  public void shouldRejectEnumCoercionOfUnknownNameAndUnsupportedType() {
    assertThatThrownBy(() -> converter.coerceToEnum("PURPLE", Color.class))
        .isInstanceOf(ELException.class);
    assertThatThrownBy(() -> converter.coerceToEnum(1, Color.class))
        .isInstanceOf(ELException.class);
  }

  // property editor fallback ----------------------------------------------------------------

  @Test
  public void shouldCoerceStringWithPropertyEditor() {
    assertThat(converter.coerceStringToType("12", Integer.class)).isEqualTo(12);
  }

  @Test
  public void shouldReturnNullWhenEmptyStringIsRejectedByEditor() {
    assertThat(converter.coerceStringToType("", Integer.class)).isNull();
  }

  @Test
  public void shouldRejectStringTheEditorCannotParse() {
    assertThatThrownBy(() -> converter.coerceStringToType("x", Integer.class))
        .isInstanceOf(ELException.class);
  }

  @Test
  public void shouldReturnNullForEmptyStringWithoutEditor() {
    assertThat(converter.coerceStringToType("", Unconvertible.class)).isNull();
  }

  @Test
  public void shouldRejectNonEmptyStringWithoutEditor() {
    assertThatThrownBy(() -> converter.coerceStringToType("x", Unconvertible.class))
        .isInstanceOf(ELException.class);
  }

  // dispatch through the public API ---------------------------------------------------------

  @Test
  public void shouldDispatchEveryTargetType() {
    assertThat(converter.convert(1, String.class)).isEqualTo("1");
    assertThat(converter.convert("2", Long.class)).isEqualTo(2L);
    assertThat(converter.convert("2", long.class)).isEqualTo(2L);
    assertThat(converter.convert("2", Double.class)).isEqualTo(2d);
    assertThat(converter.convert("2", double.class)).isEqualTo(2d);
    assertThat(converter.convert("true", Boolean.class)).isEqualTo(Boolean.TRUE);
    assertThat(converter.convert("true", boolean.class)).isEqualTo(Boolean.TRUE);
    assertThat(converter.convert("2", Integer.class)).isEqualTo(2);
    assertThat(converter.convert("2", int.class)).isEqualTo(2);
    assertThat(converter.convert("2", Float.class)).isEqualTo(2f);
    assertThat(converter.convert("2", float.class)).isEqualTo(2f);
    assertThat(converter.convert("2", Short.class)).isEqualTo((short) 2);
    assertThat(converter.convert("2", short.class)).isEqualTo((short) 2);
    assertThat(converter.convert("2", Byte.class)).isEqualTo((byte) 2);
    assertThat(converter.convert("2", byte.class)).isEqualTo((byte) 2);
    assertThat(converter.convert("x", Character.class)).isEqualTo('x');
    assertThat(converter.convert("x", char.class)).isEqualTo('x');
    assertThat(converter.convert("2", BigDecimal.class)).isEqualByComparingTo("2");
    assertThat(converter.convert("2", BigInteger.class)).isEqualTo(BigInteger.valueOf(2));
    assertThat(converter.convert("RED", Color.class)).isEqualTo(Color.RED);
  }

  @Test
  public void shouldPassThroughValueThatAlreadyHasTheTargetType() {
    Unconvertible value = new Unconvertible();

    assertThat(converter.convert(null, Unconvertible.class)).isNull();
    assertThat(converter.convert(value, Unconvertible.class)).isSameAs(value);
    assertThat(converter.convert(value, Object.class)).isSameAs(value);
  }

  @Test
  public void shouldRejectValueThatIsNeitherStringNorTargetType() {
    assertThatThrownBy(() -> converter.convert(1, Unconvertible.class))
        .isInstanceOf(ELException.class);
  }

  // identity --------------------------------------------------------------------------------

  @Test
  public void shouldBeEqualToAnotherInstanceOfTheSameClass() {
    assertThat(converter).isEqualTo(new TypeConverterImpl());
    assertThat(converter.hashCode()).isEqualTo(new TypeConverterImpl().hashCode());
  }

  @Test
  public void shouldNotBeEqualToNullOrOtherType() {
    assertThat(converter.equals(null)).isFalse();
    assertThat(converter.equals("text")).isFalse();
  }
}
