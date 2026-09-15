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
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

import org.camunda.bpm.impl.juel.Builder.Feature;
import org.junit.Test;

/**
 * Drives {@link Scanner} and {@link Parser} through the grammar of EL 2.1 by parsing expressions,
 * rather than by poking at the two classes directly.
 *
 * <p>The assertions stay on what the parser is for - an expression is accepted or refused, and the
 * identifiers and functions it found - so the tests do not freeze the shape of the syntax tree.
 */
public class ExpressionParsingTest {

  protected final Builder builder = new Builder();

  protected Tree parse(String expression) {
    return builder.build(expression);
  }

  // literals and plain text -----------------------------------------------------------------

  @Test
  public void shouldParseTextWithoutAnyExpression() {
    assertThat(parse("plain text").getRoot()).isNotNull();
    assertThat(parse("").getRoot()).isNotNull();
    assertThat(parse("$ and # on their own").getRoot()).isNotNull();
  }

  @Test
  public void shouldParseEveryLiteralType() {
    assertThat(parse("${1}").getRoot()).isNotNull();
    assertThat(parse("${1.5}").getRoot()).isNotNull();
    assertThat(parse("${1e3}").getRoot()).isNotNull();
    assertThat(parse("${1.5E-3}").getRoot()).isNotNull();
    assertThat(parse("${'single'}").getRoot()).isNotNull();
    assertThat(parse("${\"double\"}").getRoot()).isNotNull();
    assertThat(parse("${'escaped \\' quote'}").getRoot()).isNotNull();
    assertThat(parse("${\"escaped \\\" quote\"}").getRoot()).isNotNull();
    assertThat(parse("${true}").getRoot()).isNotNull();
    assertThat(parse("${false}").getRoot()).isNotNull();
    assertThat(parse("${null}").getRoot()).isNotNull();
  }

  @Test
  public void shouldParseDeferredAndCompositeExpressions() {
    assertThat(parse("#{a}").isDeferred()).isTrue();
    assertThat(parse("${a}").isDeferred()).isFalse();
    assertThat(parse("before ${a} between ${b} after").getRoot()).isNotNull();
    assertThat(parse("\\${not an expression}").getRoot()).isNotNull();
  }

  // operators ---------------------------------------------------------------------------------

  @Test
  public void shouldParseArithmeticOperators() {
    assertThat(parse("${1 + 2 - 3}").getRoot()).isNotNull();
    assertThat(parse("${2 * 3 / 4 % 5}").getRoot()).isNotNull();
    assertThat(parse("${2 div 3 mod 4}").getRoot()).isNotNull();
    assertThat(parse("${-1}").getRoot()).isNotNull();
  }

  @Test
  public void shouldParseRelationalAndEqualityOperators() {
    assertThat(parse("${1 < 2}").getRoot()).isNotNull();
    assertThat(parse("${1 > 2}").getRoot()).isNotNull();
    assertThat(parse("${1 <= 2}").getRoot()).isNotNull();
    assertThat(parse("${1 >= 2}").getRoot()).isNotNull();
    assertThat(parse("${1 lt 2}").getRoot()).isNotNull();
    assertThat(parse("${1 gt 2}").getRoot()).isNotNull();
    assertThat(parse("${1 le 2}").getRoot()).isNotNull();
    assertThat(parse("${1 ge 2}").getRoot()).isNotNull();
    assertThat(parse("${1 == 2}").getRoot()).isNotNull();
    assertThat(parse("${1 != 2}").getRoot()).isNotNull();
    assertThat(parse("${1 eq 2}").getRoot()).isNotNull();
    assertThat(parse("${1 ne 2}").getRoot()).isNotNull();
  }

  @Test
  public void shouldParseLogicalOperators() {
    assertThat(parse("${true && false}").getRoot()).isNotNull();
    assertThat(parse("${true || false}").getRoot()).isNotNull();
    assertThat(parse("${true and false}").getRoot()).isNotNull();
    assertThat(parse("${true or false}").getRoot()).isNotNull();
    assertThat(parse("${!true}").getRoot()).isNotNull();
    assertThat(parse("${not true}").getRoot()).isNotNull();
    assertThat(parse("${empty a}").getRoot()).isNotNull();
  }

  @Test
  public void shouldParseTernaryAndParentheses() {
    assertThat(parse("${a ? 1 : 2}").getRoot()).isNotNull();
    assertThat(parse("${(1 + 2) * 3}").getRoot()).isNotNull();
    assertThat(parse("${a ? b ? 1 : 2 : 3}").getRoot()).isNotNull();
  }

  // properties, indices, identifiers ----------------------------------------------------------

  @Test
  public void shouldParsePropertyAndIndexAccess() {
    assertThat(parse("${a.b.c}").getRoot()).isNotNull();
    assertThat(parse("${a['b']}").getRoot()).isNotNull();
    assertThat(parse("${a[0][1]}").getRoot()).isNotNull();
    assertThat(parse("${a[b].c['d']}").getRoot()).isNotNull();
  }

  @Test
  public void shouldReportTheIdentifiersItFound() {
    Tree tree = parse("${first + second.property + first}");

    assertThat(tree.getIdentifierNodes()).extracting("name")
        .containsExactlyInAnyOrder("first", "second", "first");
  }

  @Test
  public void shouldReportTheFunctionsItFound() {
    Tree tree = parse("${fn:length(a)}");

    assertThat(tree.getFunctionNodes()).extracting("name").containsExactly("fn:length");
  }

  @Test
  public void shouldParseFunctionCallsWithAnyNumberOfArguments() {
    assertThat(parse("${f()}").getRoot()).isNotNull();
    assertThat(parse("${f(1)}").getRoot()).isNotNull();
    assertThat(parse("${f(1, 2, 3)}").getRoot()).isNotNull();
    assertThat(parse("${ns:f(a.b, 'c')}").getRoot()).isNotNull();
  }

  // malformed input -----------------------------------------------------------------------------

  @Test
  public void shouldRefuseUnterminatedExpression() {
    assertThatThrownBy(() -> parse("${a")).isInstanceOf(TreeBuilderException.class);
  }

  @Test
  public void shouldRefuseUnterminatedString() {
    assertThatThrownBy(() -> parse("${'a}")).isInstanceOf(TreeBuilderException.class);
  }

  @Test
  public void shouldRefuseUnbalancedParentheses() {
    assertThatThrownBy(() -> parse("${(1 + 2}")).isInstanceOf(TreeBuilderException.class);
  }

  @Test
  public void shouldRefuseMissingOperand() {
    assertThatThrownBy(() -> parse("${1 +}")).isInstanceOf(TreeBuilderException.class);
    assertThatThrownBy(() -> parse("${* 2}")).isInstanceOf(TreeBuilderException.class);
  }

  @Test
  public void shouldRefuseIncompleteTernary() {
    assertThatThrownBy(() -> parse("${a ? 1}")).isInstanceOf(TreeBuilderException.class);
  }

  @Test
  public void shouldRefuseIllegalCharacter() {
    assertThatThrownBy(() -> parse("${a @ b}")).isInstanceOf(TreeBuilderException.class);
  }

  @Test
  public void shouldRefuseUnbalancedBracket() {
    assertThatThrownBy(() -> parse("${a[}")).isInstanceOf(TreeBuilderException.class);
    assertThatThrownBy(() -> parse("${a[0}")).isInstanceOf(TreeBuilderException.class);
  }

  @Test
  public void shouldRefuseTwoOperandsWithoutAnOperator() {
    assertThatThrownBy(() -> parse("${a b}")).isInstanceOf(TreeBuilderException.class);
  }

  // features ------------------------------------------------------------------------------------

  @Test
  public void shouldRefuseMethodInvocationUnlessTheFeatureIsEnabled() {
    assertThatThrownBy(() -> new Builder().build("${a.b(1)}"))
        .isInstanceOf(TreeBuilderException.class);

    assertThat(new Builder(Feature.METHOD_INVOCATIONS).build("${a.b(1)}").getRoot()).isNotNull();
  }

  @Test
  public void shouldReportWhichFeaturesAreEnabled() {
    Builder none = new Builder();
    Builder one = new Builder(Feature.VARARGS);
    Builder several = new Builder(Feature.VARARGS, Feature.NULL_PROPERTIES);

    assertThat(none.isEnabled(Feature.VARARGS)).isFalse();
    assertThat(one.isEnabled(Feature.VARARGS)).isTrue();
    assertThat(one.isEnabled(Feature.NULL_PROPERTIES)).isFalse();
    assertThat(several.isEnabled(Feature.VARARGS)).isTrue();
    assertThat(several.isEnabled(Feature.NULL_PROPERTIES)).isTrue();
  }

  @Test
  public void shouldTreatNullAndEmptyFeatureListAsNoFeatures() {
    assertThat(new Builder((Feature[]) null).isEnabled(Feature.VARARGS)).isFalse();
    assertThat(new Builder(new Feature[0]).isEnabled(Feature.VARARGS)).isFalse();
  }

  @Test
  public void shouldCompareBuildersByTheirFeatures() {
    assertThat(new Builder()).isEqualTo(new Builder());
    assertThat(new Builder().hashCode()).isEqualTo(new Builder().hashCode());
    assertThat(new Builder(Feature.VARARGS)).isEqualTo(new Builder(Feature.VARARGS));
    assertThat(new Builder(Feature.VARARGS)).isNotEqualTo(new Builder());
    assertThat(new Builder().equals(null)).isFalse();
    assertThat(new Builder().equals("text")).isFalse();
  }

  // the exception carries the position ------------------------------------------------------------

  @Test
  public void shouldDescribeWhereTheExpressionBroke() {
    try {
      parse("${1 +}");
      failBecauseExceptionWasNotThrown(TreeBuilderException.class);
    } catch (TreeBuilderException e) {
      assertThat(e.getExpression()).isEqualTo("${1 +}");
      assertThat(e.getPosition()).isPositive();
      assertThat(e.getEncountered()).isNotEmpty();
    }
  }
}
