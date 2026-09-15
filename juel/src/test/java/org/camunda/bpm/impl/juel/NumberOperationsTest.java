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
 * Covers the arithmetic operations of EL 2.1 section 1.7.
 *
 * <p>Each operator picks a result type in a fixed order - BigDecimal, floating point (which a
 * string containing '.', 'e' or 'E' also counts as), BigInteger, then Long - and every test drives
 * that choice from both operands, because each rule is an or over the two.
 */
public class NumberOperationsTest {

  protected final TypeConverter converter = new TypeConverterImpl();

  // add -------------------------------------------------------------------------------------

  @Test
  public void shouldAddAsLongWhenBothOperandsAreNullOrIntegral() {
    assertThat(NumberOperations.add(converter, null, null)).isEqualTo(0L);
    assertThat(NumberOperations.add(converter, 1, 2)).isEqualTo(3L);
    assertThat(NumberOperations.add(converter, "1", 2)).isEqualTo(3L);
    assertThat(NumberOperations.add(converter, null, 2)).isEqualTo(2L);
  }

  @Test
  public void shouldAddAsBigDecimalWhenEitherOperandIsOne() {
    assertThat(NumberOperations.add(converter, new BigDecimal("1.5"), 1))
        .isEqualTo(new BigDecimal("2.5"));
    assertThat(NumberOperations.add(converter, 1, new BigDecimal("1.5")))
        .isEqualTo(new BigDecimal("2.5"));
  }

  @Test
  public void shouldAddAsDoubleWhenEitherOperandIsFloatingPointOrLooksLikeIt() {
    assertThat(NumberOperations.add(converter, 1.5d, 1)).isEqualTo(2.5d);
    assertThat(NumberOperations.add(converter, 1, 1.5f)).isEqualTo(2.5d);
    assertThat(NumberOperations.add(converter, "1.5", 1)).isEqualTo(2.5d);
    assertThat(NumberOperations.add(converter, 1, "1e0")).isEqualTo(2.0d);
    assertThat(NumberOperations.add(converter, "1E0", 1)).isEqualTo(2.0d);
  }

  @Test
  public void shouldAddAsBigDecimalWhenFloatingPointMeetsBigInteger() {
    assertThat(NumberOperations.add(converter, 1.5d, BigInteger.ONE))
        .isEqualTo(new BigDecimal("2.5"));
    assertThat(NumberOperations.add(converter, BigInteger.ONE, 1.5d))
        .isEqualTo(new BigDecimal("2.5"));
  }

  @Test
  public void shouldAddAsBigIntegerWhenEitherOperandIsOne() {
    assertThat(NumberOperations.add(converter, BigInteger.ONE, 2)).isEqualTo(BigInteger.valueOf(3));
    assertThat(NumberOperations.add(converter, 2, BigInteger.ONE)).isEqualTo(BigInteger.valueOf(3));
  }

  // sub -------------------------------------------------------------------------------------

  @Test
  public void shouldSubtractPerCommonType() {
    assertThat(NumberOperations.sub(converter, null, null)).isEqualTo(0L);
    assertThat(NumberOperations.sub(converter, 3, 1)).isEqualTo(2L);
    assertThat(NumberOperations.sub(converter, new BigDecimal("2.5"), 1))
        .isEqualTo(new BigDecimal("1.5"));
    assertThat(NumberOperations.sub(converter, 1, new BigDecimal("2.5")))
        .isEqualTo(new BigDecimal("-1.5"));
    assertThat(NumberOperations.sub(converter, 2.5d, 1)).isEqualTo(1.5d);
    assertThat(NumberOperations.sub(converter, 1, "2.5")).isEqualTo(-1.5d);
    assertThat(NumberOperations.sub(converter, 2.5d, BigInteger.ONE))
        .isEqualTo(new BigDecimal("1.5"));
    assertThat(NumberOperations.sub(converter, BigInteger.ONE, 2.5d))
        .isEqualTo(new BigDecimal("-1.5"));
    assertThat(NumberOperations.sub(converter, BigInteger.TEN, 1)).isEqualTo(BigInteger.valueOf(9));
    assertThat(NumberOperations.sub(converter, 10, BigInteger.ONE)).isEqualTo(BigInteger.valueOf(9));
  }

  // mul -------------------------------------------------------------------------------------

  @Test
  public void shouldMultiplyPerCommonType() {
    assertThat(NumberOperations.mul(converter, null, null)).isEqualTo(0L);
    assertThat(NumberOperations.mul(converter, 3, 2)).isEqualTo(6L);
    assertThat(NumberOperations.mul(converter, new BigDecimal("1.5"), 2))
        .isEqualTo(new BigDecimal("3.0"));
    assertThat(NumberOperations.mul(converter, 2, new BigDecimal("1.5")))
        .isEqualTo(new BigDecimal("3.0"));
    assertThat(NumberOperations.mul(converter, 1.5d, 2)).isEqualTo(3.0d);
    assertThat(NumberOperations.mul(converter, 2, "1.5")).isEqualTo(3.0d);
    assertThat(NumberOperations.mul(converter, 1.5d, BigInteger.TWO))
        .isEqualTo(new BigDecimal("3.0"));
    assertThat(NumberOperations.mul(converter, BigInteger.TWO, 1.5d))
        .isEqualTo(new BigDecimal("3.0"));
    assertThat(NumberOperations.mul(converter, BigInteger.TEN, 2)).isEqualTo(BigInteger.valueOf(20));
    assertThat(NumberOperations.mul(converter, 10, BigInteger.TWO)).isEqualTo(BigInteger.valueOf(20));
  }

  // div -------------------------------------------------------------------------------------

  /**
   * The BigDecimal division rounds HALF_UP at the scale of the dividend, so an unscaled dividend
   * gives a whole number - 3 divided by 2 is 2, not 1.5.
   */
  @Test
  public void shouldDivideAsBigDecimalWhenEitherOperandIsBigDecimalOrBigInteger() {
    assertThat(NumberOperations.div(converter, new BigDecimal("3.0"), 2))
        .isEqualTo(new BigDecimal("1.5"));
    assertThat(NumberOperations.div(converter, new BigDecimal("3"), 2))
        .isEqualTo(new BigDecimal("2"));
    assertThat(NumberOperations.div(converter, 3, new BigDecimal("2")))
        .isEqualTo(new BigDecimal("2"));
    assertThat(NumberOperations.div(converter, BigInteger.valueOf(3), 2))
        .isEqualTo(new BigDecimal("2"));
    assertThat(NumberOperations.div(converter, 3, BigInteger.TWO)).isEqualTo(new BigDecimal("2"));
  }

  @Test
  public void shouldDivideAsDoubleOtherwise() {
    assertThat(NumberOperations.div(converter, null, null)).isEqualTo(0L);
    assertThat(NumberOperations.div(converter, 3, 2)).isEqualTo(1.5d);
    assertThat(NumberOperations.div(converter, "3", 2)).isEqualTo(1.5d);
  }

  // mod -------------------------------------------------------------------------------------

  @Test
  public void shouldTakeRemainderPerCommonType() {
    assertThat(NumberOperations.mod(converter, null, null)).isEqualTo(0L);
    assertThat(NumberOperations.mod(converter, 7, 3)).isEqualTo(1L);
    assertThat(NumberOperations.mod(converter, new BigDecimal("7.5"), 3)).isEqualTo(1.5d);
    assertThat(NumberOperations.mod(converter, 3, new BigDecimal("2.5"))).isEqualTo(0.5d);
    assertThat(NumberOperations.mod(converter, 7.5d, 3)).isEqualTo(1.5d);
    assertThat(NumberOperations.mod(converter, 3, "2.5")).isEqualTo(0.5d);
    assertThat(NumberOperations.mod(converter, BigInteger.valueOf(7), 3))
        .isEqualTo(BigInteger.ONE);
    assertThat(NumberOperations.mod(converter, 7, BigInteger.valueOf(3)))
        .isEqualTo(BigInteger.ONE);
  }

  // neg -------------------------------------------------------------------------------------

  @Test
  public void shouldNegateEveryNumericType() {
    assertThat(NumberOperations.neg(converter, null)).isEqualTo(0L);
    assertThat(NumberOperations.neg(converter, new BigDecimal("1.5")))
        .isEqualTo(new BigDecimal("-1.5"));
    assertThat(NumberOperations.neg(converter, BigInteger.TEN)).isEqualTo(BigInteger.valueOf(-10));
    assertThat(NumberOperations.neg(converter, 1.5d)).isEqualTo(-1.5d);
    assertThat(NumberOperations.neg(converter, 1.5f)).isEqualTo(-1.5f);
    assertThat(NumberOperations.neg(converter, 5L)).isEqualTo(-5L);
    assertThat(NumberOperations.neg(converter, 5)).isEqualTo(-5);
    assertThat(NumberOperations.neg(converter, (short) 5)).isEqualTo((short) -5);
    assertThat(NumberOperations.neg(converter, (byte) 5)).isEqualTo((byte) -5);
  }

  @Test
  public void shouldNegateStringAsDoubleOnlyWhenItLooksFloatingPoint() {
    assertThat(NumberOperations.neg(converter, "1.5")).isEqualTo(-1.5d);
    assertThat(NumberOperations.neg(converter, "1e1")).isEqualTo(-10.0d);
    assertThat(NumberOperations.neg(converter, "5")).isEqualTo(-5L);
  }

  @Test
  public void shouldRefuseNegatingSomethingThatIsNotANumber() {
    assertThatThrownBy(() -> NumberOperations.neg(converter, Boolean.TRUE))
        .isInstanceOf(ELException.class);
  }
}
