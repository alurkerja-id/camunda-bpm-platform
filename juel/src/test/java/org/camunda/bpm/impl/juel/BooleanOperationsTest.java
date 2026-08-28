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
import java.util.Collections;
import java.util.Date;
import org.junit.Test;

/**
 * Covers the relational and equality operators of EL 2.1 section 1.8.
 *
 * <p>Both comparison paths pick a common type in a fixed order - BigDecimal, float, BigInteger,
 * integral, String, then Comparable - so each test walks that order deliberately.
 */
public class BooleanOperationsTest {

  protected enum Color {
    RED, GREEN
  }

  /** Comparable only through the second operand, to reach the mirrored branch. */
  protected static class Point implements Comparable<Object> {

    protected final int value;

    protected Point(int value) {
      this.value = value;
    }

    @Override
    public int compareTo(Object other) {
      return Integer.compare(value, ((Point) other).value);
    }
  }

  /** Neither Comparable nor coercible - the operators must refuse it. */
  protected static class Opaque {
  }

  protected final TypeConverter converter = new TypeConverterImpl();

  // null and identity -----------------------------------------------------------------------

  @Test
  public void shouldTreatSameReferenceAsEqualAndNeitherLessNorGreater() {
    Object value = "a";

    assertThat(BooleanOperations.lt(converter, value, value)).isFalse();
    assertThat(BooleanOperations.gt(converter, value, value)).isFalse();
    assertThat(BooleanOperations.le(converter, value, value)).isTrue();
    assertThat(BooleanOperations.ge(converter, value, value)).isTrue();
    assertThat(BooleanOperations.eq(converter, value, value)).isTrue();
    assertThat(BooleanOperations.ne(converter, value, value)).isFalse();
  }

  @Test
  public void shouldReturnFalseWhenEitherOperandIsNull() {
    assertThat(BooleanOperations.lt(converter, null, 1)).isFalse();
    assertThat(BooleanOperations.lt(converter, 1, null)).isFalse();
    assertThat(BooleanOperations.gt(converter, null, 1)).isFalse();
    assertThat(BooleanOperations.gt(converter, 1, null)).isFalse();
    assertThat(BooleanOperations.le(converter, null, 1)).isFalse();
    assertThat(BooleanOperations.le(converter, 1, null)).isFalse();
    assertThat(BooleanOperations.ge(converter, null, 1)).isFalse();
    assertThat(BooleanOperations.ge(converter, 1, null)).isFalse();
    assertThat(BooleanOperations.eq(converter, null, 1)).isFalse();
    assertThat(BooleanOperations.eq(converter, 1, null)).isFalse();
  }

  // comparison, one test per common type ----------------------------------------------------

  @Test
  public void shouldCompareAsBigDecimalWhenEitherOperandIsOne() {
    assertThat(BooleanOperations.lt(converter, new BigDecimal("1.5"), 2)).isTrue();
    assertThat(BooleanOperations.gt(converter, 2, new BigDecimal("1.5"))).isTrue();
    assertThat(BooleanOperations.le(converter, new BigDecimal("2"), 2)).isTrue();
    assertThat(BooleanOperations.ge(converter, new BigDecimal("2"), 2)).isTrue();
  }

  @Test
  public void shouldCompareAsDoubleWhenEitherOperandIsFloatingPoint() {
    assertThat(BooleanOperations.lt(converter, 1.5d, 2)).isTrue();
    assertThat(BooleanOperations.gt(converter, 2, 1.5f)).isTrue();
    assertThat(BooleanOperations.le(converter, 1.5d, 1.5d)).isTrue();
    assertThat(BooleanOperations.ge(converter, 2.5f, 1)).isTrue();
  }

  @Test
  public void shouldCompareAsBigIntegerWhenEitherOperandIsOne() {
    assertThat(BooleanOperations.lt(converter, BigInteger.ONE, 2)).isTrue();
    assertThat(BooleanOperations.gt(converter, 2, BigInteger.ONE)).isTrue();
    assertThat(BooleanOperations.le(converter, BigInteger.ONE, BigInteger.ONE)).isTrue();
    assertThat(BooleanOperations.ge(converter, BigInteger.TEN, 2)).isTrue();
  }

  @Test
  public void shouldCompareAsLongWhenOperandsAreIntegral() {
    assertThat(BooleanOperations.lt(converter, 1, 2L)).isTrue();
    assertThat(BooleanOperations.gt(converter, (short) 5, (byte) 2)).isTrue();
    assertThat(BooleanOperations.le(converter, 2, 2)).isTrue();
    assertThat(BooleanOperations.ge(converter, 2, 2)).isTrue();
  }

  @Test
  public void shouldCompareAsStringWhenEitherOperandIsOne() {
    assertThat(BooleanOperations.lt(converter, "a", "b")).isTrue();
    assertThat(BooleanOperations.gt(converter, "b", "a")).isTrue();
    assertThat(BooleanOperations.le(converter, "a", "a")).isTrue();
    assertThat(BooleanOperations.ge(converter, "b", "a")).isTrue();
  }

  /**
   * Every type test is an or over both operands, so the same comparison has to be driven from the
   * right-hand side as well, otherwise half of each condition is never evaluated.
   */
  @Test
  public void shouldPickTheCommonTypeFromEitherSide() {
    assertThat(BooleanOperations.lt(converter, 2, new BigDecimal("2.5"))).isTrue();
    assertThat(BooleanOperations.gt(converter, new BigDecimal("2.5"), 2)).isTrue();
    assertThat(BooleanOperations.lt(converter, 1, 2.5d)).isTrue();
    assertThat(BooleanOperations.gt(converter, 2.5d, 1)).isTrue();
    assertThat(BooleanOperations.lt(converter, 1, BigInteger.TEN)).isTrue();
    assertThat(BooleanOperations.gt(converter, BigInteger.TEN, 1)).isTrue();
    assertThat(BooleanOperations.lt(converter, 'a', 2)).isFalse();
    assertThat(BooleanOperations.gt(converter, 'a', 2)).isTrue();
    assertThat(BooleanOperations.lt(converter, 'a', "b")).isTrue();
    assertThat(BooleanOperations.gt(converter, "b", 'a')).isTrue();
  }

  @Test
  public void shouldPickTheCommonTypeFromEitherSideForEquality() {
    assertThat(BooleanOperations.eq(converter, 2, new BigDecimal("2"))).isTrue();
    assertThat(BooleanOperations.eq(converter, 2, 2.0d)).isTrue();
    assertThat(BooleanOperations.eq(converter, 10, BigInteger.TEN)).isTrue();
    assertThat(BooleanOperations.eq(converter, 2L, 2)).isTrue();
    assertThat(BooleanOperations.eq(converter, "true", Boolean.TRUE)).isTrue();
    assertThat(BooleanOperations.eq(converter, 'a', "a")).isTrue();
  }

  @Test
  public void shouldCompareThroughComparableFirstOperand() {
    Date earlier = new Date(1000L);
    Date later = new Date(2000L);

    assertThat(BooleanOperations.lt(converter, earlier, later)).isTrue();
    assertThat(BooleanOperations.gt(converter, later, earlier)).isTrue();
    assertThat(BooleanOperations.le(converter, earlier, later)).isTrue();
    assertThat(BooleanOperations.ge(converter, later, earlier)).isTrue();
  }

  @Test
  public void shouldCompareThroughComparableSecondOperandWhenFirstIsNot() {
    Object plain = new Opaque();
    // the mirrored branch compares the other way round: lt asks compareTo(o1) > 0, gt asks < 0
    Point smaller = new Point(5) {
      @Override
      public int compareTo(Object other) {
        return -1;
      }
    };
    Point larger = new Point(5) {
      @Override
      public int compareTo(Object other) {
        return 1;
      }
    };

    assertThat(BooleanOperations.lt(converter, plain, larger)).isTrue();
    assertThat(BooleanOperations.lt(converter, plain, smaller)).isFalse();
    assertThat(BooleanOperations.gt(converter, plain, smaller)).isTrue();
    assertThat(BooleanOperations.gt(converter, plain, larger)).isFalse();
  }

  @Test
  public void shouldRefuseComparingTwoIncomparableValues() {
    Object left = new Opaque();
    Object right = new Opaque();

    assertThatThrownBy(() -> BooleanOperations.lt(converter, left, right))
        .isInstanceOf(ELException.class);
    assertThatThrownBy(() -> BooleanOperations.gt(converter, left, right))
        .isInstanceOf(ELException.class);
  }

  // equality --------------------------------------------------------------------------------

  @Test
  public void shouldCompareEqualityPerCommonType() {
    assertThat(BooleanOperations.eq(converter, new BigDecimal("2.0"), new BigDecimal("2.0")))
        .isTrue();
    assertThat(BooleanOperations.eq(converter, 2.0d, 2)).isTrue();
    assertThat(BooleanOperations.eq(converter, BigInteger.TEN, 10)).isTrue();
    assertThat(BooleanOperations.eq(converter, 2, 2L)).isTrue();
    assertThat(BooleanOperations.eq(converter, Boolean.TRUE, "true")).isTrue();
    assertThat(BooleanOperations.eq(converter, "a", "a")).isTrue();
    assertThat(BooleanOperations.eq(converter, 1, 2)).isFalse();
  }

  @Test
  public void shouldCompareEnumAgainstItsName() {
    assertThat(BooleanOperations.eq(converter, Color.RED, "RED")).isTrue();
    assertThat(BooleanOperations.eq(converter, "GREEN", Color.GREEN)).isTrue();
    assertThat(BooleanOperations.eq(converter, Color.RED, "GREEN")).isFalse();
  }

  @Test
  public void shouldFallBackToEqualsForEverythingElse() {
    Date value = new Date(1000L);

    assertThat(BooleanOperations.eq(converter, value, new Date(1000L))).isTrue();
    assertThat(BooleanOperations.eq(converter, value, new Date(2000L))).isFalse();
  }

  @Test
  public void shouldNegateEquality() {
    assertThat(BooleanOperations.ne(converter, 1, 2)).isTrue();
    assertThat(BooleanOperations.ne(converter, 1, 1)).isFalse();
  }

  // empty -----------------------------------------------------------------------------------

  @Test
  public void shouldRecogniseEmptyValues() {
    assertThat(BooleanOperations.empty(converter, null)).isTrue();
    assertThat(BooleanOperations.empty(converter, "")).isTrue();
    assertThat(BooleanOperations.empty(converter, new Object[0])).isTrue();
    assertThat(BooleanOperations.empty(converter, Collections.emptyMap())).isTrue();
    assertThat(BooleanOperations.empty(converter, Collections.emptyList())).isTrue();
  }

  @Test
  public void shouldRecogniseNonEmptyValues() {
    assertThat(BooleanOperations.empty(converter, "a")).isFalse();
    assertThat(BooleanOperations.empty(converter, new Object[] { 1 })).isFalse();
    assertThat(BooleanOperations.empty(converter, Collections.singletonMap("k", "v"))).isFalse();
    assertThat(BooleanOperations.empty(converter, Collections.singletonList(1))).isFalse();
    assertThat(BooleanOperations.empty(converter, 1)).isFalse();
  }
}
