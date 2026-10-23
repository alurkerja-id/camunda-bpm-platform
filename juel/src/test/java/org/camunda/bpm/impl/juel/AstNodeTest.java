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
import org.junit.Test;

/** Drives the literal AST nodes directly, the way the parser hands them to the evaluator. */
public class AstNodeTest {

  protected final SimpleContext context = new SimpleContext(new SimpleResolver());

  protected String structure(AstNode node) {
    StringBuilder b = new StringBuilder();
    node.appendStructure(b, null);
    return b.toString();
  }

  @Test
  public void shouldBehaveAsAReadOnlyTextLiteral() {
    AstText text = new AstText("a ${b}");

    assertThat(text.isLiteralText()).isTrue();
    assertThat(text.isLeftValue()).isFalse();
    assertThat(text.isMethodInvocation()).isFalse();
    assertThat(text.getType(null, context)).isNull();
    assertThat(text.isReadOnly(null, context)).isTrue();
    assertThat(text.getValueReference(null, context)).isNull();
    assertThat(text.getMethodInfo(null, context, String.class, null)).isNull();
    assertThat(text.eval(null, context)).isEqualTo("a ${b}");
    assertThat(text.getCardinality()).isZero();
    assertThat(text.getChild(0)).isNull();
    assertThat(text.toString()).isEqualTo("\"a ${b}\"");
    assertThatThrownBy(() -> text.setValue(null, context, "x")).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldInvokeATextLiteralWithOrWithoutAReturnType() {
    AstText text = new AstText("42");
    Bindings bindings = new Bindings(null, null);

    assertThat(text.invoke(bindings, context, null, null, null)).isEqualTo("42");
    assertThat(text.invoke(bindings, context, Integer.class, null, null)).isEqualTo(42);
  }

  @Test
  public void shouldEscapeExpressionMarkersInTheStructureOfText() {
    assertThat(structure(new AstText("a ${b} #{c} $d"))).isEqualTo("a \\${b} \\#{c} $d");
    assertThat(structure(new AstText(""))).isEmpty();
  }

  @Test
  public void shouldEscapeQuotesInTheStructureOfAString() {
    AstString string = new AstString("it's a \\ path");

    assertThat(structure(string)).isEqualTo("'it\\'s a \\\\ path'");
    assertThat(string.eval(null, context)).isEqualTo("it's a \\ path");
  }

  @Test
  public void shouldPrintBinaryOperatorsAndEvaluateThemDirectly() {
    Bindings bindings = new Bindings(null, null);
    AstNumber one = new AstNumber(1);
    AstNumber two = new AstNumber(2);

    assertThat(new AstBinary(one, two, AstBinary.ADD).eval(bindings, context)).isEqualTo(3L);
    assertThat(new AstBinary(one, two, AstBinary.LT).eval(bindings, context)).isEqualTo(true);
    assertThat(new AstBinary(one, two, AstBinary.EQ).eval(bindings, context)).isEqualTo(false);
    assertThatThrownBy(() -> new AstBinary(one, two, AstBinary.OR).eval(bindings, context))
        .isInstanceOf(ELException.class);
    assertThat(AstBinary.ADD).hasToString("+");
    assertThat(AstBinary.AND).hasToString("&&");
    assertThat(AstBinary.OR).hasToString("||");
    assertThat(AstBinary.MOD).hasToString("%");
    assertThat(AstBinary.DIV).hasToString("/");
    assertThat(AstBinary.MUL).hasToString("*");
    assertThat(AstBinary.SUB).hasToString("-");
    assertThat(AstBinary.NE).hasToString("!=");
    assertThat(AstBinary.LE).hasToString("<=");
    assertThat(AstBinary.GE).hasToString(">=");
    assertThat(AstBinary.GT).hasToString(">");
    assertThat(new AstBinary(one, two, AstBinary.ADD).getCardinality()).isEqualTo(2);
    assertThat(new AstBinary(one, two, AstBinary.ADD).getChild(1)).isSameAs(two);
  }
}
