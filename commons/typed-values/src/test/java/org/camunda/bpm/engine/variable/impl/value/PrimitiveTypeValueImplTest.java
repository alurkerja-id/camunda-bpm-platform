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
package org.camunda.bpm.engine.variable.impl.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.BooleanValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.BytesValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.DateValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.DoubleValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.IntegerValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.LongValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.ShortValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl.StringValueImpl;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.junit.Test;

/**
 * Covers {@link PrimitiveTypeValueImpl}.
 *
 * <p>Nearly every branch here sits in equals: type and value are each compared through a null
 * guard, and the transient flag takes part too, so two values that look alike but differ in that
 * flag are not equal. The tests walk each guard from both sides.
 */
public class PrimitiveTypeValueImplTest {

  protected PrimitiveTypeValueImpl<String> value(String value) {
    return new PrimitiveTypeValueImpl<>(value, ValueType.STRING);
  }

  // the concrete subclasses -------------------------------------------------------------------

  @Test
  public void shouldCarryValueAndTypeForEverySubclass() {
    assertThat(new BooleanValueImpl(true).getType()).isEqualTo(ValueType.BOOLEAN);
    assertThat(new BytesValueImpl(new byte[] { 1 }).getType()).isEqualTo(ValueType.BYTES);
    assertThat(new DateValueImpl(new Date(1L)).getType()).isEqualTo(ValueType.DATE);
    assertThat(new DoubleValueImpl(1.5d).getType()).isEqualTo(ValueType.DOUBLE);
    assertThat(new IntegerValueImpl(1).getType()).isEqualTo(ValueType.INTEGER);
    assertThat(new LongValueImpl(1L).getType()).isEqualTo(ValueType.LONG);
    assertThat(new ShortValueImpl((short) 1).getType()).isEqualTo(ValueType.SHORT);
    assertThat(new StringValueImpl("text").getType()).isEqualTo(ValueType.STRING);

    assertThat(new StringValueImpl("text").getValue()).isEqualTo("text");
    assertThat(new IntegerValueImpl(1).getValue()).isEqualTo(1);
  }

  @Test
  public void shouldAcceptTheTransientFlagInEverySubclass() {
    assertThat(new BooleanValueImpl(true, true).isTransient()).isTrue();
    assertThat(new BytesValueImpl(new byte[] { 1 }, true).isTransient()).isTrue();
    assertThat(new DateValueImpl(new Date(1L), true).isTransient()).isTrue();
    assertThat(new DoubleValueImpl(1.5d, true).isTransient()).isTrue();
    assertThat(new IntegerValueImpl(1, true).isTransient()).isTrue();
    assertThat(new LongValueImpl(1L, true).isTransient()).isTrue();
    assertThat(new ShortValueImpl((short) 1, true).isTransient()).isTrue();
    assertThat(new StringValueImpl("text", true).isTransient()).isTrue();

    assertThat(new StringValueImpl("text").isTransient()).isFalse();
  }

  // equals ------------------------------------------------------------------------------------

  @Test
  public void shouldEqualItselfAndAnEquivalentValue() {
    PrimitiveTypeValueImpl<String> value = value("text");

    assertThat(value.equals(value)).isTrue();
    assertThat(value).isEqualTo(value("text"));
  }

  @Test
  public void shouldNotEqualNullOrAnotherClass() {
    PrimitiveTypeValueImpl<String> value = value("text");

    assertThat(value.equals(null)).isFalse();
    assertThat(value.equals("text")).isFalse();
    assertThat(value.equals(new StringValueImpl("text"))).isFalse();
  }

  @Test
  public void shouldCompareTheTypeThroughItsNullGuard() {
    PrimitiveTypeValueImpl<String> noType = new PrimitiveTypeValueImpl<>("text", null);
    PrimitiveTypeValueImpl<String> otherNoType = new PrimitiveTypeValueImpl<>("text", null);

    assertThat(noType).isEqualTo(otherNoType);
    assertThat(noType.equals(value("text"))).isFalse();
    assertThat(value("text").equals(noType)).isFalse();
    assertThat(value("text").equals(new PrimitiveTypeValueImpl<>("text", ValueType.NUMBER)))
        .isFalse();
  }

  @Test
  public void shouldCompareTheValueThroughItsNullGuard() {
    PrimitiveTypeValueImpl<String> noValue = value(null);
    PrimitiveTypeValueImpl<String> otherNoValue = value(null);

    assertThat(noValue).isEqualTo(otherNoValue);
    assertThat(noValue.equals(value("text"))).isFalse();
    assertThat(value("text").equals(noValue)).isFalse();
    assertThat(value("text").equals(value("other"))).isFalse();
  }

  /** Two values that match in type and value are still different if one of them is transient. */
  @Test
  public void shouldTakeTheTransientFlagIntoAccount() {
    assertThat(new StringValueImpl("text", true).equals(new StringValueImpl("text", false)))
        .isFalse();
    assertThat(new StringValueImpl("text", true)).isEqualTo(new StringValueImpl("text", true));
  }

  // hashCode ----------------------------------------------------------------------------------

  @Test
  public void shouldHashEqualValuesAlike() {
    assertThat(value("text").hashCode()).isEqualTo(value("text").hashCode());
  }

  @Test
  public void shouldHashNullTypeAndNullValue() {
    assertThat(value(null).hashCode())
        .isEqualTo(value(null).hashCode());
    assertThat(new PrimitiveTypeValueImpl<String>(null, null).hashCode())
        .isEqualTo(new PrimitiveTypeValueImpl<String>(null, null).hashCode());
  }

  @Test
  public void shouldRenderValueAndTypeInToString() {
    String rendered = value("text").toString();

    assertThat(rendered).contains("text");
  }
}
