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
import jakarta.el.MethodInfo;
import jakarta.el.PropertyNotFoundException;
import jakarta.el.PropertyNotWritableException;
import jakarta.el.ValueExpression;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;

/**
 * Exercises the parts of JUEL that sit behind the factory: method expressions, the tree dump,
 * serialization, the root resolver and the factory configuration.
 */
public class ExpressionInternalsTest {

  public static class Greeter implements java.io.Serializable {

    protected String name = "ana";
    protected Greeter friend;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Greeter getFriend() {
      return friend;
    }

    public String greet(String greeting) {
      return greeting + ", " + name;
    }

    public String join(String... parts) {
      return String.join("-", parts);
    }

    public String hello() {
      return "hello " + name;
    }

    public int add(int a, int b) {
      return a + b;
    }

    public void fail() {
      throw new IllegalStateException("boom");
    }
  }

  public static int twice(int value) {
    return value * 2;
  }

  public static String joinAll(String... parts) {
    return String.join("+", parts);
  }

  protected ExpressionFactoryImpl factory;
  protected SimpleContext context;

  @Before
  public void setUp() {
    Properties properties = new Properties();
    properties.setProperty(ExpressionFactoryImpl.PROP_METHOD_INVOCATIONS, "true");
    properties.setProperty(ExpressionFactoryImpl.PROP_VAR_ARGS, "true");
    properties.setProperty(ExpressionFactoryImpl.PROP_NULL_PROPERTIES, "true");
    factory = new ExpressionFactoryImpl(properties);
    context = new SimpleContext(new SimpleResolver());
    set("g", new Greeter());
  }

  protected void set(String name, Object value) {
    context.setVariable(name,
        factory.createValueExpression(value, value == null ? Object.class : value.getClass()));
  }

  protected Object eval(String expression) {
    return factory.createValueExpression(context, expression, Object.class).getValue(context);
  }

  // method invocation inside value expressions ---------------------------------------------------

  @Test
  public void shouldInvokeMethodsWithArguments() {
    assertThat(eval("${g.greet('hi')}")).isEqualTo("hi, ana");
    assertThat(eval("${g.hello()}")).isEqualTo("hello ana");
    assertThat(eval("${g.add(2, 3)}")).isEqualTo(5);
  }

  @Test
  public void shouldInvokeVarArgsMethods() {
    assertThat(eval("${g.join('a', 'b', 'c')}")).isEqualTo("a-b-c");
    assertThat(eval("${g.join()}")).isEqualTo("");
  }

  @Test
  public void shouldInvokeMethodsOnComputedBases() {
    assertThat(eval("${g['greet']('yo')}")).isEqualTo("yo, ana");
  }

  @Test
  public void shouldPropagateTheFailureOfAnInvokedMethod() {
    assertThatThrownBy(() -> eval("${g.fail()}")).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldRefuseInvokingAnUnknownMethod() {
    assertThatThrownBy(() -> eval("${g.nothing()}")).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldAnswerNullForNullPropertiesWhenEnabled() {
    assertThat(eval("${g.friend.name}")).isNull();
  }

  @Test
  public void shouldInvokeVarArgsFunctions() throws NoSuchMethodException {
    context.setFunction("", "joinAll", ExpressionInternalsTest.class.getMethod("joinAll", String[].class));

    assertThat(eval("${joinAll('a', 'b')}")).isEqualTo("a+b");
    assertThat(eval("${joinAll()}")).isEqualTo("");
  }

  @Test
  public void shouldCallFunctionsWithAPrefix() throws NoSuchMethodException {
    context.setFunction("m", "twice", ExpressionInternalsTest.class.getMethod("twice", int.class));

    assertThat(eval("${m:twice(21)}")).isEqualTo(42);
    assertThat(eval("${m:twice(1) + m:twice(2)}")).isEqualTo(6L);
  }

  // method expressions ---------------------------------------------------------------------------

  protected MethodExpression method(String expression, Class<?> returnType, Class<?>... params) {
    return factory.createMethodExpression(context, expression, returnType, params);
  }

  @Test
  public void shouldInvokeAMethodExpression() {
    MethodExpression expression = method("${g.greet}", String.class, String.class);

    assertThat(expression.invoke(context, new Object[] {"hey"})).isEqualTo("hey, ana");
    assertThat(expression.isParametersProvided()).isFalse();
    assertThat(expression.isLiteralText()).isFalse();
  }

  @Test
  public void shouldInvokeAMethodExpressionWithItsOwnParameters() {
    MethodExpression expression = method("${g.hello()}", String.class);

    assertThat(expression.isParametersProvided()).isTrue();
    assertThat(expression.invoke(context, null)).isEqualTo("hello ana");
  }

  @Test
  public void shouldDescribeTheMethodBehindAMethodExpression() {
    MethodExpression expression = method("${g.greet}", String.class, String.class);

    MethodInfo info = expression.getMethodInfo(context);

    assertThat(info.getName()).isEqualTo("greet");
    assertThat(info.getReturnType()).isEqualTo(String.class);
    assertThat(info.getParamTypes()).containsExactly(String.class);
  }

  @Test
  public void shouldInvokeALiteralMethodExpression() {
    MethodExpression expression = method("outcome", String.class);

    assertThat(expression.isLiteralText()).isTrue();
    assertThat(expression.getExpressionString()).isEqualTo("outcome");
    assertThat(expression.invoke(context, null)).isEqualTo("outcome");
    assertThat(expression.getMethodInfo(context)).isNull();
  }

  @Test
  public void shouldRefuseAVoidLiteralMethodExpression() {
    assertThatThrownBy(() -> method("outcome", void.class)).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldRefuseAMethodExpressionThatIsNotAMethod() {
    assertThatThrownBy(() -> method("${1 + 2}", Long.class)).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldRefuseAMethodExpressionWithoutParameterTypes() {
    assertThatThrownBy(() -> factory.createMethodExpression(context, "${g.greet}", String.class, null))
        .isInstanceOf(ELException.class)
        .hasMessageContaining("Parameter types");
  }

  @Test
  public void shouldCompareMethodExpressions() {
    MethodExpression a = method("${g.greet}", String.class, String.class);
    MethodExpression b = method("${g.greet}", String.class, String.class);
    MethodExpression c = method("${g.hello}", String.class);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c).isNotEqualTo("${g.greet}").isNotEqualTo(null);
    assertThat(a.toString()).contains("${g.greet}");
  }

  @Test
  public void shouldDumpAMethodExpression() {
    TreeMethodExpression expression = factory.createMethodExpression(
        context, "${g.greet}", String.class, new Class<?>[] {String.class});
    StringWriter out = new StringWriter();

    expression.dump(new PrintWriter(out));

    assertThat(out.toString()).isNotBlank().contains("greet");
  }

  @Test
  public void shouldReportWhetherAMethodExpressionIsDeferred() {
    assertThat(((TreeMethodExpression) method("#{g.hello}", String.class)).isDeferred()).isTrue();
    assertThat(((TreeMethodExpression) method("${g.hello}", String.class)).isDeferred()).isFalse();
  }

  // value expressions ----------------------------------------------------------------------------

  @Test
  public void shouldDescribeTheTypeOfAProperty() {
    ValueExpression expression = factory.createValueExpression(context, "${g.name}", Object.class);

    assertThat(expression.getType(context)).isEqualTo(String.class);
    assertThat(expression.isReadOnly(context)).isFalse();
  }

  @Test
  public void shouldWriteAPropertyThroughAValueExpression() {
    ValueExpression expression = factory.createValueExpression(context, "${g.name}", Object.class);

    expression.setValue(context, "bob");

    assertThat(eval("${g.name}")).isEqualTo("bob");
  }

  @Test
  public void shouldRefuseWritingAPropertyWithoutSetter() {
    ValueExpression expression = factory.createValueExpression(context, "${g.friend}", Object.class);

    assertThat(expression.isReadOnly(context)).isTrue();
    assertThatThrownBy(() -> expression.setValue(context, new Greeter()))
        .isInstanceOf(PropertyNotWritableException.class);
  }

  @Test
  public void shouldDescribeTheTypeOfAMapEntry() {
    Map<String, Object> map = new HashMap<>();
    map.put("k", 1);
    set("map", map);
    ValueExpression expression = factory.createValueExpression(context, "${map.k}", Object.class);

    assertThat(expression.getType(context)).isEqualTo(Object.class);
  }

  @Test
  public void shouldEvaluateChoiceAndNestedExpressions() {
    assertThat(eval("${(1 + 2) * 3}")).isEqualTo(9L);
    assertThat(eval("${g.name == 'ana' ? g.hello() : 'nobody'}")).isEqualTo("hello ana");
    assertThat(eval("${g.name != 'ana' ? 1 : 2}")).isEqualTo(2L);
  }

  @Test
  public void shouldEvaluateEmptyAndUnaryOperators() {
    set("list", new ArrayList<>());
    set("full", Arrays.asList(1, 2));

    assertThat(eval("${empty list}")).isEqualTo(true);
    assertThat(eval("${empty full}")).isEqualTo(false);
    assertThat(eval("${!true}")).isEqualTo(false);
    assertThat(eval("${- 5 + 2}")).isEqualTo(-3L);
    assertThat(eval("${full[1]}")).isEqualTo(2);
    assertThat(eval("${full.size()}")).isEqualTo(2);
  }

  @Test
  public void shouldEvaluateWordOperators() {
    assertThat(eval("${5 div 2}")).isEqualTo(2.5d);
    assertThat(eval("${5 mod 2}")).isEqualTo(1L);
    assertThat(eval("${1 lt 2 and 2 gt 1}")).isEqualTo(true);
    assertThat(eval("${1 le 1 and 1 ge 1}")).isEqualTo(true);
    assertThat(eval("${1 eq 1 or 1 ne 1}")).isEqualTo(true);
  }

  @Test
  public void shouldEvaluateStringEscapes() {
    assertThat(eval("${'it\\'s'}")).isEqualTo("it's");
    assertThat(eval("${\"say \\\"x\\\"\"}")).isEqualTo("say \"x\"");
    assertThat(eval("a \\${literal}")).isEqualTo("a ${literal}");
  }

  @Test
  public void shouldEvaluateNumberLiterals() {
    assertThat(eval("${1e2}")).isEqualTo(100d);
    assertThat(eval("${.5}")).isEqualTo(0.5d);
    assertThat(eval("${2.5E-1}")).isEqualTo(0.25d);
  }

  @Test
  public void shouldRefuseAnUnknownBeanPropertyOnAMethodBase() {
    assertThatThrownBy(() -> eval("${g.missing}")).isInstanceOf(PropertyNotFoundException.class);
  }

  // object value expressions ---------------------------------------------------------------------

  @Test
  public void shouldBehaveAsAConstantWhenWrapped() {
    ObjectValueExpression expression = factory.createValueExpression("text", String.class);

    assertThat(expression.getValue(context)).isEqualTo("text");
    assertThat(expression.getType(context)).isNull();
    assertThat(expression.isReadOnly(context)).isTrue();
    assertThat(expression.isLiteralText()).isFalse();
    assertThat(expression.getExpectedType()).isEqualTo(String.class);
    assertThat(expression.getExpressionString()).isNull();
    assertThat(expression.toString()).contains("text");
    assertThatThrownBy(() -> expression.setValue(context, "x"))
        .isInstanceOf(ELException.class)
        .hasMessageContaining("non-lvalue");
  }

  @Test
  public void shouldCoerceAWrappedValueToTheExpectedType() {
    ObjectValueExpression expression = factory.createValueExpression("42", Integer.class);

    assertThat(expression.getValue(context)).isEqualTo(42);
  }

  @Test
  public void shouldCompareWrappedValues() {
    ObjectValueExpression a = factory.createValueExpression("x", String.class);
    ObjectValueExpression b = factory.createValueExpression("x", String.class);
    ObjectValueExpression otherValue = factory.createValueExpression("y", String.class);
    ObjectValueExpression otherType = factory.createValueExpression("x", Object.class);
    ObjectValueExpression nulls = factory.createValueExpression(null, Object.class);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(otherValue).isNotEqualTo(otherType).isNotEqualTo("x").isNotEqualTo(null);
    assertThat(nulls).isEqualTo(factory.createValueExpression(null, Object.class));
    assertThat(nulls).isNotEqualTo(a);
    assertThat(nulls.hashCode()).isZero();
  }

  @Test
  public void shouldRefuseWrappingWithoutAType() {
    assertThatThrownBy(() -> factory.createValueExpression("x", null))
        .isInstanceOf(NullPointerException.class);
  }

  // tree dump and serialization ------------------------------------------------------------------

  @Test
  public void shouldDumpTheTreeOfAnExpression() throws NoSuchMethodException {
    context.setFunction("m", "twice", ExpressionInternalsTest.class.getMethod("twice", int.class));
    TreeValueExpression expression = factory.createValueExpression(context,
        "x ${a.b[1] + m:twice(2) > 3 ? -c : !d and (e or f)} ${'s'} ${true} ${null} ${empty g}",
        Object.class);
    StringWriter out = new StringWriter();

    expression.dump(new PrintWriter(out));

    assertThat(out.toString()).contains("m:twice", "composite", "'>'", "'&&'", "'||'", "'!'", "'-'", "\"s\"", "true", "null");
  }

  @Test
  public void shouldPrintTheExpressionBack() {
    String source = "a ${b.c + 1}";
    TreeValueExpression expression = factory.createValueExpression(context, source, Object.class);

    assertThat(expression.getExpressionString()).isEqualTo(source);
    assertThat(expression.toString()).contains(source);
  }

  @Test
  public void shouldSurviveSerialization() throws Exception {
    TreeValueExpression value = factory.createValueExpression(context, "${1 + 2}", Object.class);
    TreeMethodExpression method = factory.createMethodExpression(
        context, "${g.hello}", String.class, new Class<?>[0]);

    TreeValueExpression valueCopy = roundTrip(value);
    TreeMethodExpression methodCopy = roundTrip(method);

    assertThat(valueCopy).isEqualTo(value);
    assertThat(valueCopy.getValue(context)).isEqualTo(3L);
    assertThat(methodCopy.invoke(context, null)).isEqualTo("hello ana");
  }

  @SuppressWarnings("unchecked")
  protected <T> T roundTrip(T object) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(object);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (T) in.readObject();
    }
  }

  // root property resolver -----------------------------------------------------------------------

  @Test
  public void shouldResolveRootProperties() {
    RootPropertyResolver resolver = new RootPropertyResolver();
    SimpleContext ctx = new SimpleContext(resolver);
    resolver.setProperty("answer", 42);

    assertThat(resolver.isProperty("answer")).isTrue();
    assertThat(resolver.isProperty("question")).isFalse();
    assertThat(resolver.getProperty("answer")).isEqualTo(42);
    assertThat(resolver.properties()).containsExactly("answer");
    assertThat(resolver.getValue(ctx, null, "answer")).isEqualTo(42);
    assertThat(ctx.isPropertyResolved()).isTrue();
    assertThat(resolver.getType(ctx, null, "answer")).isEqualTo(Object.class);
    assertThat(resolver.isReadOnly(ctx, null, "answer")).isFalse();
    assertThat(resolver.getFeatureDescriptors(ctx, null)).isNull();
  }

  @Test
  public void shouldNotResolveWhatItDoesNotOwn() {
    RootPropertyResolver resolver = new RootPropertyResolver();
    SimpleContext ctx = new SimpleContext(resolver);

    assertThat(resolver.getValue(ctx, "base", "x")).isNull();
    assertThat(ctx.isPropertyResolved()).isFalse();
    assertThat(resolver.getType(ctx, null, 1)).isNull();
    assertThat(resolver.isReadOnly(ctx, "base", "x")).isFalse();
    assertThat(resolver.invoke(ctx, "base", "m", null, null)).isNull();
    resolver.setValue(ctx, "base", "x", 1);
    assertThat(resolver.isProperty("x")).isFalse();
  }

  @Test
  public void shouldRefuseUnknownRootPropertyAndNullInvocation() {
    RootPropertyResolver resolver = new RootPropertyResolver();
    SimpleContext ctx = new SimpleContext(resolver);

    assertThatThrownBy(() -> resolver.getValue(ctx, null, "nothing"))
        .isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> resolver.invoke(ctx, null, "m", null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldWriteRootPropertiesUnlessReadOnly() {
    RootPropertyResolver writable = new RootPropertyResolver();
    RootPropertyResolver readOnly = new RootPropertyResolver(true);
    SimpleContext ctx = new SimpleContext(writable);

    writable.setValue(ctx, null, "k", "v");

    assertThat(writable.getProperty("k")).isEqualTo("v");
    assertThat(readOnly.isReadOnly(ctx, null, "k")).isTrue();
    assertThatThrownBy(() -> readOnly.setValue(ctx, null, "k", "v"))
        .isInstanceOf(PropertyNotWritableException.class);
  }

  @Test
  public void shouldEvaluateAgainstARootResolver() {
    RootPropertyResolver resolver = new RootPropertyResolver();
    resolver.setProperty("n", 7);
    SimpleContext ctx = new SimpleContext(resolver);

    assertThat(factory.createValueExpression(ctx, "${n + 1}", Object.class).getValue(ctx)).isEqualTo(8L);
    factory.createValueExpression(ctx, "${n}", Object.class).setValue(ctx, 9);
    assertThat(resolver.getProperty("n")).isEqualTo(9);
  }

  // simple context and factory configuration -----------------------------------------------------

  @Test
  public void shouldKeepFunctionsAndVariablesInTheContext() throws NoSuchMethodException {
    SimpleContext ctx = new SimpleContext();
    ValueExpression variable = factory.createValueExpression("v", String.class);

    assertThat(ctx.setVariable("x", variable)).isNull();
    assertThat(ctx.setVariable("x", null)).isSameAs(variable);
    assertThat(ctx.getVariableMapper().resolveVariable("x")).isNull();

    ctx.setFunction("p", "twice", ExpressionInternalsTest.class.getMethod("twice", int.class));
    assertThat(ctx.getFunctionMapper().resolveFunction("p", "twice")).isNotNull();
    assertThat(ctx.getFunctionMapper().resolveFunction("p", "other")).isNull();
    assertThat(ctx.getELResolver()).isNotNull();

    RootPropertyResolver resolver = new RootPropertyResolver();
    ctx.setELResolver(resolver);
    assertThat(ctx.getELResolver()).isSameAs(resolver);
  }

  @Test
  public void shouldConfigureTheFactoryFromProfiles() {
    ExpressionFactoryImpl jee5 = new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE5);
    ExpressionFactoryImpl jee6 = new ExpressionFactoryImpl(ExpressionFactoryImpl.Profile.JEE6);
    SimpleContext ctx = new SimpleContext(new SimpleResolver());

    assertThatThrownBy(() -> jee5.createValueExpression(ctx, "${'a'.length()}", Object.class))
        .isInstanceOf(ELException.class);
    assertThat(jee6.createValueExpression(ctx, "${'abc'.length()}", Object.class).getValue(ctx))
        .isEqualTo(3);
  }

  @Test
  public void shouldConfigureTheCacheSize() {
    Properties properties = new Properties();
    properties.setProperty(ExpressionFactoryImpl.PROP_CACHE_SIZE, "3");
    ExpressionFactoryImpl small = new ExpressionFactoryImpl(properties);
    SimpleContext ctx = new SimpleContext(new SimpleResolver());

    for (int i = 0; i < 10; i++) {
      assertThat(small.createValueExpression(ctx, "${" + i + "}", Object.class).getValue(ctx))
          .isEqualTo((long) i);
    }
    assertThat(small.createValueExpression(ctx, "${0}", Object.class).getValue(ctx)).isEqualTo(0L);
  }

  @Test
  public void shouldRefuseABadCacheSize() {
    Properties properties = new Properties();
    properties.setProperty(ExpressionFactoryImpl.PROP_CACHE_SIZE, "many");

    assertThatThrownBy(() -> new ExpressionFactoryImpl(properties)).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldCreateTheFactoryFromPropertiesAndConverter() {
    ExpressionFactoryImpl custom = new ExpressionFactoryImpl(new Properties(), new TypeConverterImpl());
    SimpleContext ctx = new SimpleContext(new SimpleResolver());

    assertThat(custom.coerceToType("5", Integer.class)).isEqualTo(5);
    assertThat(custom.createValueExpression(ctx, "${'5' + 1}", Object.class).getValue(ctx)).isEqualTo(6L);
  }

  @Test
  public void shouldGroupIdentifiersAndFunctionsOfATree() {
    List<String> names = new ArrayList<>();
    Builder builder = new Builder();
    Tree tree = builder.build("${a + b.c}");
    tree.getIdentifierNodes().forEach(n -> names.add(n.getName()));

    assertThat(names).containsExactly("a", "b");
    assertThat(tree.isDeferred()).isFalse();
    assertThat(tree.getRoot()).isNotNull();
    assertThat(tree.toString()).isNotBlank();
  }
}
