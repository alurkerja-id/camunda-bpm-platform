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
import jakarta.el.MethodExpression;
import jakarta.el.MethodNotFoundException;
import jakarta.el.PropertyNotFoundException;
import jakarta.el.ValueExpression;
import jakarta.el.ValueReference;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;

/**
 * Covers how identifiers and functions bind: to variables mapped in the context, to the resolver,
 * and to static methods - including the failure branches each of them has.
 */
public class ExpressionBindingsTest {

  public static int twice(int value) {
    return value * 2;
  }

  public static String concat(String first, Object... rest) {
    StringBuilder b = new StringBuilder(first);
    for (Object o : rest) {
      b.append('/').append(o);
    }
    return b.toString();
  }

  public static String sum(int... values) {
    int total = 0;
    for (int v : values) {
      total += v;
    }
    return String.valueOf(total);
  }

  public static String noArgs() {
    return "none";
  }

  public static String failing() {
    throw new IllegalStateException("broken");
  }

  public static class Holder {

    protected String value = "v";

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }

  protected ExpressionFactoryImpl factory;
  protected SimpleContext context;

  @Before
  public void setUp() {
    Properties properties = new Properties();
    properties.setProperty(ExpressionFactoryImpl.PROP_VAR_ARGS, "true");
    properties.setProperty(ExpressionFactoryImpl.PROP_METHOD_INVOCATIONS, "true");
    factory = new ExpressionFactoryImpl(properties);
    context = new SimpleContext(new SimpleResolver());
  }

  protected Method method(String name, Class<?>... types) throws NoSuchMethodException {
    return ExpressionBindingsTest.class.getMethod(name, types);
  }

  protected ValueExpression value(String expression) {
    return factory.createValueExpression(context, expression, Object.class);
  }

  // identifiers bound to a mapped variable -------------------------------------------------------

  @Test
  public void shouldDelegateAMappedIdentifierToItsVariable() {
    Holder holder = new Holder();
    context.setVariable("h", factory.createValueExpression(holder, Holder.class));
    context.setVariable("lazy", factory.createValueExpression(context, "${h.value}", Object.class));

    ValueExpression expression = value("${lazy}");

    assertThat(expression.getValue(context)).isEqualTo("v");
    assertThat(expression.getType(context)).isEqualTo(String.class);
    assertThat(expression.isReadOnly(context)).isFalse();
    ValueReference reference = expression.getValueReference(context);
    assertThat(reference.getBase()).isSameAs(holder);
    assertThat(reference.getProperty()).isEqualTo("value");

    expression.setValue(context, "changed");
    assertThat(holder.getValue()).isEqualTo("changed");
  }

  @Test
  public void shouldTellAnIdentifierFromAVariableInTheStructure() {
    context.setVariable("a", factory.createValueExpression(1, Integer.class));
    ValueExpression bound = value("${a + b}");
    ValueExpression sameShape = factory.createValueExpression(new SimpleContext(), "${x + y}", Object.class);

    assertThat(bound).isNotEqualTo(sameShape);
    assertThat(bound.toString()).contains("${a + b}");
  }

  // identifiers bound to the resolver ------------------------------------------------------------

  @Test
  public void shouldResolveAnIdentifierThroughTheResolver() {
    RootPropertyResolver resolver = new RootPropertyResolver();
    resolver.setProperty("x", "one");
    SimpleContext ctx = new SimpleContext(resolver);
    ValueExpression expression = factory.createValueExpression(ctx, "${x}", Object.class);

    assertThat(expression.getType(ctx)).isEqualTo(Object.class);
    assertThat(expression.isReadOnly(ctx)).isFalse();
    assertThat(expression.getValueReference(ctx).getBase()).isNull();
    assertThat(expression.getValueReference(ctx).getProperty()).isEqualTo("x");
  }

  @Test
  public void shouldFailWhenNoResolverKnowsTheIdentifier() {
    SimpleContext empty = new SimpleContext(new jakarta.el.CompositeELResolver());
    ValueExpression expression = factory.createValueExpression(empty, "${nothing}", Object.class);

    assertThatThrownBy(() -> expression.getValue(empty)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.getType(empty)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.isReadOnly(empty)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.setValue(empty, 1)).isInstanceOf(PropertyNotFoundException.class);
  }

  // identifiers holding a method -----------------------------------------------------------------

  @Test
  public void shouldInvokeAMethodHeldByAVariable() throws NoSuchMethodException {
    context.setVariable("m", factory.createValueExpression(method("twice", int.class), Method.class));
    MethodExpression expression =
        factory.createMethodExpression(context, "${m}", int.class, new Class<?>[] {int.class});

    assertThat(expression.invoke(context, new Object[] {21})).isEqualTo(42);
    assertThat(expression.getMethodInfo(context).getName()).isEqualTo("twice");
  }

  @Test
  public void shouldRefuseAVariableHoldingAMethodOfTheWrongShape() throws NoSuchMethodException {
    context.setVariable("m", factory.createValueExpression(method("twice", int.class), Method.class));
    MethodExpression wrongReturn =
        factory.createMethodExpression(context, "${m}", String.class, new Class<?>[] {int.class});
    MethodExpression wrongParams =
        factory.createMethodExpression(context, "${m}", int.class, new Class<?>[] {String.class});

    assertThatThrownBy(() -> wrongReturn.invoke(context, new Object[] {1}))
        .isInstanceOf(MethodNotFoundException.class);
    assertThatThrownBy(() -> wrongParams.invoke(context, new Object[] {"1"}))
        .isInstanceOf(MethodNotFoundException.class);
  }

  @Test
  public void shouldRefuseAVariableThatIsNotAMethod() {
    context.setVariable("text", factory.createValueExpression("text", String.class));
    context.setVariable("nothing", factory.createValueExpression(null, Object.class));
    MethodExpression notAMethod =
        factory.createMethodExpression(context, "${text}", Object.class, new Class<?>[0]);
    MethodExpression nullValue =
        factory.createMethodExpression(context, "${nothing}", Object.class, new Class<?>[0]);

    assertThatThrownBy(() -> notAMethod.invoke(context, null)).isInstanceOf(MethodNotFoundException.class);
    assertThatThrownBy(() -> nullValue.invoke(context, null)).isInstanceOf(MethodNotFoundException.class);
  }

  @Test
  public void shouldReportAFailureInsideAnInvokedMethod() throws NoSuchMethodException {
    context.setVariable("m", factory.createValueExpression(method("failing"), Method.class));
    MethodExpression expression =
        factory.createMethodExpression(context, "${m}", String.class, new Class<?>[0]);

    assertThatThrownBy(() -> expression.invoke(context, null))
        .isInstanceOf(ELException.class)
        .hasMessageContaining("Error invoking method")
        .hasMessageContaining("broken");
  }

  // functions ------------------------------------------------------------------------------------

  @Test
  public void shouldCoerceFunctionArguments() throws NoSuchMethodException {
    context.setFunction("", "twice", method("twice", int.class));

    assertThat(value("${twice('4')}").getValue(context)).isEqualTo(8);
    assertThat(value("${twice(4.0)}").getValue(context)).isEqualTo(8);
  }

  @Test
  public void shouldCallAFunctionWithoutArguments() throws NoSuchMethodException {
    context.setFunction("f", "noArgs", method("noArgs"));

    assertThat(value("${f:noArgs()}").getValue(context)).isEqualTo("none");
  }

  @Test
  public void shouldSpreadFunctionArgumentsOverVarArgs() throws NoSuchMethodException {
    context.setFunction("", "concat", method("concat", String.class, Object[].class));
    context.setFunction("", "sum", method("sum", int[].class));
    context.setVariable("numbers", factory.createValueExpression(new int[] {1, 2, 3}, int[].class));
    context.setVariable("strings", factory.createValueExpression(new String[] {"b", "c"}, String[].class));
    context.setVariable("nothing", factory.createValueExpression(null, Object.class));

    assertThat(value("${concat('a')}").getValue(context)).isEqualTo("a");
    assertThat(value("${concat('a', 1, 'x')}").getValue(context)).isEqualTo("a/1/x");
    assertThat(value("${concat('a', strings)}").getValue(context)).isEqualTo("a/b/c");
    assertThat(value("${sum(1, 2, '3')}").getValue(context)).isEqualTo("6");
    assertThat(value("${sum(numbers)}").getValue(context)).isEqualTo("6");
    assertThat(value("${sum(5)}").getValue(context)).isEqualTo("5");
    assertThat(value("${sum()}").getValue(context)).isEqualTo("0");
    assertThat(value("${concat('a', nothing)}").getValue(context)).isEqualTo("a/null");
  }

  @Test
  public void shouldCoerceAnArrayPassedAsVarArgs() throws NoSuchMethodException {
    context.setFunction("", "sum", method("sum", int[].class));
    context.setVariable("longs", factory.createValueExpression(new long[] {4, 5}, long[].class));

    assertThat(value("${sum(longs)}").getValue(context)).isEqualTo("9");
  }

  @Test
  public void shouldReportAFailureInsideAFunction() throws NoSuchMethodException {
    context.setFunction("", "failing", method("failing"));
    ValueExpression expression = value("${failing()}");

    assertThatThrownBy(() -> expression.getValue(context)).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldRefuseAFunctionUsedAsALeftValue() throws NoSuchMethodException {
    context.setFunction("", "twice", method("twice", int.class));
    ValueExpression expression = value("${twice(1)}");

    assertThat(expression.isReadOnly(context)).isTrue();
    assertThatThrownBy(() -> expression.setValue(context, 1)).isInstanceOf(ELException.class);
    assertThat(expression.getType(context)).isNull();
  }

  @Test
  public void shouldKeepFunctionAndVariableBindingsAcrossSerialization() throws Exception {
    context.setFunction("", "twice", method("twice", int.class));
    context.setVariable("n", factory.createValueExpression(5, Integer.class));
    TreeValueExpression expression =
        factory.createValueExpression(context, "${twice(n) + 1}", Object.class);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(expression);
    }
    Object copy;
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = in.readObject();
    }

    assertThat(copy).isEqualTo(expression);
    assertThat(((TreeValueExpression) copy).getValue(context)).isEqualTo(11L);
  }

  @Test
  public void shouldRefuseFunctionsWithUnmappedPrefixes() {
    assertThatThrownBy(() -> value("${p:missing(1)}").getValue(context)).isInstanceOf(ELException.class);
  }

  // text and literal handling --------------------------------------------------------------------

  @Test
  public void shouldKeepEscapedExpressionMarkersInTheStructure() {
    TreeValueExpression expression = factory.createValueExpression(context, "a \\${b} c", Object.class);

    assertThat(expression.getValue(context)).isEqualTo("a ${b} c");
    assertThat(expression.isLiteralText()).isTrue();
  }

  @Test
  public void shouldTreatTextAsReadOnlyAndNotAReference() {
    ValueExpression expression = value("just text");

    assertThat(expression.isLiteralText()).isTrue();
    assertThat(expression.getType(context)).isNull();
    assertThat(expression.isReadOnly(context)).isTrue();
    assertThat(expression.getValueReference(context)).isNull();
    assertThatThrownBy(() -> expression.setValue(context, "x")).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldCoerceTextToTheExpectedType() {
    ValueExpression number = factory.createValueExpression(context, "42", Integer.class);

    assertThat(number.getValue(context)).isEqualTo(42);
  }

  // messages and builder entry point -------------------------------------------------------------

  @Test
  public void shouldFormatKnownAndUnknownMessages() {
    assertThat(LocalMessages.get("error.identifier.property.notfound", "abc")).contains("abc");
    assertThat(LocalMessages.get("no.such.key")).contains("no.such.key");
    assertThat(LocalMessages.get("no.such.key", "one", 2)).contains("no.such.key").contains("one").contains("2");
  }

  @Test
  public void shouldDumpAndEvaluateFromTheBuilderEntryPoint() {
    PrintStream original = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured, true));
    try {
      Builder.main(new String[] {"${1 + 2}"});
    } finally {
      System.setOut(original);
    }

    assertThat(captured.toString()).contains("'+'").contains(">> 3");
  }

  @Test
  public void shouldReportTheErrorOfAnExpressionThatCannotBeEvaluatedFromTheEntryPoint() {
    PrintStream original = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured, true));
    try {
      Builder.main(new String[] {"${1 / 0 > 'a'}"});
    } finally {
      System.setOut(original);
    }

    assertThat(captured.toString()).contains(">> ");
  }

  // scanner failures -----------------------------------------------------------------------------

  @Test
  public void shouldRefuseAnInvalidEscapeInAString() {
    assertThatThrownBy(() -> value("${'a\\qb'}")).isInstanceOf(ELException.class)
        .hasMessageContaining("escape");
  }

  @Test
  public void shouldRefuseAnUnterminatedStringAtTheEnd() {
    assertThatThrownBy(() -> value("${'abc\\")).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldReadEscapedBackslashes() {
    assertThat(value("${'a\\\\b'}").getValue(context)).isEqualTo("a\\b");
  }
}
