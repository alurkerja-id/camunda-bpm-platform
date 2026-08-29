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
import jakarta.el.PropertyNotFoundException;
import jakarta.el.ValueExpression;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/**
 * Evaluates expressions end to end, which is the half of JUEL the parsing tests never reach.
 *
 * <p>One evaluation walks the whole chain at once - the factory builds a tree, Bindings resolves
 * the variables and functions in it, and the Ast nodes read them off the resolver. So these tests
 * are written as expressions and expected values rather than as calls into single classes.
 */
public class ExpressionEvaluationTest {

  /** A bean with the shapes property access has to handle: getter, boolean getter, nesting. */
  public static class Person {

    protected String name;
    protected int age;
    protected boolean active;
    protected Person manager;

    public Person(String name, int age) {
      this.name = name;
      this.age = age;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getAge() {
      return age;
    }

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }

    public Person getManager() {
      return manager;
    }

    public void setManager(Person manager) {
      this.manager = manager;
    }

    public String greet(String greeting) {
      return greeting + ", " + name;
    }
  }

  public static String shout(String text) {
    return text.toUpperCase();
  }

  protected ExpressionFactoryImpl factory;
  protected SimpleContext context;

  @Before
  public void setUp() {
    factory = new ExpressionFactoryImpl();
    context = new SimpleContext(new SimpleResolver());
  }

  protected void set(String name, Object value) {
    context.setVariable(name,
        factory.createValueExpression(value, value == null ? Object.class : value.getClass()));
  }

  protected Object eval(String expression) {
    return factory.createValueExpression(context, expression, Object.class).getValue(context);
  }

  // literals and operators -----------------------------------------------------------------------

  @Test
  public void shouldEvaluateLiterals() {
    assertThat(eval("${1}")).isEqualTo(1L);
    assertThat(eval("${1.5}")).isEqualTo(1.5d);
    assertThat(eval("${'text'}")).isEqualTo("text");
    assertThat(eval("${true}")).isEqualTo(true);
    assertThat(eval("${null}")).isNull();
    assertThat(eval("plain text")).isEqualTo("plain text");
  }

  @Test
  public void shouldEvaluateArithmetic() {
    assertThat(eval("${1 + 2}")).isEqualTo(3L);
    assertThat(eval("${5 - 2}")).isEqualTo(3L);
    assertThat(eval("${3 * 4}")).isEqualTo(12L);
    assertThat(eval("${7 / 2}")).isEqualTo(3.5d);
    assertThat(eval("${7 % 3}")).isEqualTo(1L);
    assertThat(eval("${-3}")).isEqualTo(-3L);
  }

  @Test
  public void shouldEvaluateComparisons() {
    assertThat(eval("${1 < 2}")).isEqualTo(true);
    assertThat(eval("${1 > 2}")).isEqualTo(false);
    assertThat(eval("${2 <= 2}")).isEqualTo(true);
    assertThat(eval("${2 >= 3}")).isEqualTo(false);
    assertThat(eval("${1 == 1}")).isEqualTo(true);
    assertThat(eval("${1 != 1}")).isEqualTo(false);
  }

  @Test
  public void shouldShortCircuitLogicalOperators() {
    set("nothing", null);

    assertThat(eval("${true and false}")).isEqualTo(false);
    assertThat(eval("${true or false}")).isEqualTo(true);
    assertThat(eval("${not true}")).isEqualTo(false);
    assertThat(eval("${empty nothing}")).isEqualTo(true);
    assertThat(eval("${empty 'text'}")).isEqualTo(false);
  }

  @Test
  public void shouldEvaluateTheTernaryOperator() {
    assertThat(eval("${true ? 'yes' : 'no'}")).isEqualTo("yes");
    assertThat(eval("${false ? 'yes' : 'no'}")).isEqualTo("no");
  }

  @Test
  public void shouldConcatenateTextAndExpressions() {
    set("name", "ana");

    assertThat(eval("Hello ${name}!")).isEqualTo("Hello ana!");
    assertThat(eval("${1} and ${2}")).isEqualTo("1 and 2");
  }

  // identifiers ------------------------------------------------------------------------------------

  @Test
  public void shouldReadAVariable() {
    set("count", 42);

    assertThat(eval("${count}")).isEqualTo(42);
  }

  /** An identifier nothing resolves is an error, not a silent null. */
  @Test
  public void shouldRefuseAnUnknownIdentifier() {
    assertThatThrownBy(() -> eval("${unknown}"))
        .isInstanceOf(PropertyNotFoundException.class);
  }

  /**
   * A variable bound through the VariableMapper points at its own ValueExpression, and that one is
   * a literal - so the binding cannot be written through.
   */
  @Test
  public void shouldRefuseWritingThroughAMappedVariable() {
    set("count", 42);
    ValueExpression expression = factory.createValueExpression(context, "${count}", Object.class);

    assertThat(expression.isReadOnly(context)).isTrue();
    assertThatThrownBy(() -> expression.setValue(context, 7)).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldRefuseWritingToALiteral() {
    ValueExpression expression = factory.createValueExpression(context, "${1 + 2}", Object.class);

    assertThat(expression.isReadOnly(context)).isTrue();
    assertThatThrownBy(() -> expression.setValue(context, 7)).isInstanceOf(ELException.class);
  }

  // property access ------------------------------------------------------------------------------------

  /**
   * Property access is driven through maps and lists rather than beans. Jakarta's BeanELResolver
   * pulls in jakarta.el.ELUtil, which insists on finding a default ExpressionFactory provider that
   * this module does not ship - so a bean read fails on the classpath here, not in the code.
   */
  @Test
  public void shouldReadNestedStructures() {
    Map<String, Object> manager = new HashMap<>();
    manager.put("name", "budi");
    Map<String, Object> person = new HashMap<>();
    person.put("name", "ana");
    person.put("manager", manager);
    set("person", person);

    assertThat(eval("${person.name}")).isEqualTo("ana");
    assertThat(eval("${person.manager.name}")).isEqualTo("budi");
    assertThat(eval("${person['name']}")).isEqualTo("ana");
  }

  @Test
  public void shouldWriteIntoAMap() {
    Map<String, Object> person = new HashMap<>();
    person.put("name", "ana");
    set("person", person);

    factory.createValueExpression(context, "${person.name}", Object.class)
        .setValue(context, "budi");

    assertThat(eval("${person.name}")).isEqualTo("budi");
    assertThat(person).containsEntry("name", "budi");
  }

  @Test
  public void shouldAnswerNullForAMemberThatIsNotThere() {
    Map<String, Object> person = new HashMap<>();
    person.put("name", "ana");
    set("person", person);

    assertThat(eval("${person.nickname}")).isNull();
  }

  /**
   * Bean access needs a default ExpressionFactory provider, which this module does not ship at
   * runtime. Test resources register JUEL itself as that provider, which is what lets
   * BeanELResolver initialise here.
   */
  @Test
  public void shouldReadBeanProperties() {
    Person ana = new Person("ana", 30);
    ana.setActive(true);
    ana.setManager(new Person("budi", 40));
    set("person", ana);

    assertThat(eval("${person.name}")).isEqualTo("ana");
    assertThat(eval("${person.age}")).isEqualTo(30);
    assertThat(eval("${person.active}")).isEqualTo(true);
    assertThat(eval("${person.manager.name}")).isEqualTo("budi");
    assertThat(eval("${person['name']}")).isEqualTo("ana");
  }

  @Test
  public void shouldWriteBeanProperties() {
    Person ana = new Person("ana", 30);
    set("person", ana);

    factory.createValueExpression(context, "${person.name}", Object.class)
        .setValue(context, "budi");

    assertThat(ana.getName()).isEqualTo("budi");
  }

  @Test
  public void shouldRefuseAnUnknownBeanProperty() {
    set("person", new Person("ana", 30));

    assertThatThrownBy(() -> eval("${person.nickname}"))
        .isInstanceOf(PropertyNotFoundException.class);
  }

  @Test
  public void shouldInvokeAMethodOnABean() {
    set("person", new Person("ana", 30));

    MethodExpression expression = factory.createMethodExpression(context, "${person.greet}",
        String.class, new Class[] { String.class });

    assertThat(expression.invoke(context, new Object[] { "Hi" })).isEqualTo("Hi, ana");
    assertThat(expression.getMethodInfo(context).getName()).isEqualTo("greet");
    assertThat(expression.isLiteralText()).isFalse();
    assertThat(expression.getExpressionString()).isEqualTo("${person.greet}");
  }

  @Test
  public void shouldReadFromMapsAndLists() {
    Map<String, Object> map = new HashMap<>();
    map.put("key", "value");
    set("map", map);
    set("list", Arrays.asList("first", "second"));

    assertThat(eval("${map.key}")).isEqualTo("value");
    assertThat(eval("${map['key']}")).isEqualTo("value");
    assertThat(eval("${list[1]}")).isEqualTo("second");
  }

  @Test
  public void shouldAnswerNullWhenTheBaseIsNull() {
    set("person", null);

    assertThat(eval("${person.name}")).isNull();
  }

  // functions ---------------------------------------------------------------------------------------------

  @Test
  public void shouldCallAMappedFunction() throws NoSuchMethodException {
    Method method = ExpressionEvaluationTest.class.getMethod("shout", String.class);
    context.setFunction("str", "shout", method);

    assertThat(eval("${str:shout('quiet')}")).isEqualTo("QUIET");
  }

  @Test
  public void shouldRefuseAFunctionThatIsNotMapped() {
    assertThatThrownBy(() -> eval("${str:missing('x')}")).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldRefuseAFunctionCalledWithTheWrongArity() throws NoSuchMethodException {
    Method method = ExpressionEvaluationTest.class.getMethod("shout", String.class);
    context.setFunction("str", "shout", method);

    assertThatThrownBy(() -> eval("${str:shout('a', 'b')}")).isInstanceOf(ELException.class);
  }

  // method expressions ----------------------------------------------------------------------------------------

  /**
   * Only the literal form is exercised here: invoking a method on a bean goes through
   * BeanELResolver, which cannot initialise on this module's classpath - see
   * {@link #shouldReadNestedStructures()}.
   */
  @Test
  public void shouldTreatPlainTextAsALiteralMethodExpression() {
    MethodExpression expression = factory.createMethodExpression(context, "text",
        String.class, new Class[0]);

    assertThat(expression.isLiteralText()).isTrue();
    assertThat(expression.invoke(context, null)).isEqualTo("text");
  }

  // the expression objects themselves ---------------------------------------------------------------------------

  @Test
  public void shouldDescribeAValueExpression() {
    set("count", 42);
    ValueExpression expression = factory.createValueExpression(context, "${count}", Object.class);

    assertThat(expression.getExpressionString()).isEqualTo("${count}");
    assertThat(expression.isLiteralText()).isFalse();
    assertThat(expression.getExpectedType()).isEqualTo(Object.class);
    assertThat(expression.toString()).contains("${count}");
  }

  @Test
  public void shouldCompareValueExpressionsByWhatTheySay() {
    ValueExpression one = factory.createValueExpression(context, "${1 + 1}", Object.class);
    ValueExpression same = factory.createValueExpression(context, "${1 + 1}", Object.class);
    ValueExpression other = factory.createValueExpression(context, "${1 + 2}", Object.class);

    assertThat(one).isEqualTo(same);
    assertThat(one.hashCode()).isEqualTo(same.hashCode());
    assertThat(one.equals(other)).isFalse();
    assertThat(one.equals(null)).isFalse();
  }

  /**
   * An expression built straight from an object wraps the value; it is read-only, but not
   * "literal text" - that name is reserved for a string that carried no expression to begin with.
   */
  @Test
  public void shouldWrapAPlainObjectAsAValueExpression() {
    ValueExpression expression = factory.createValueExpression("text", String.class);

    assertThat(expression.getValue(context)).isEqualTo("text");
    assertThat(expression.isLiteralText()).isFalse();
    assertThat(expression.isReadOnly(context)).isTrue();
    assertThat(expression.getExpectedType()).isEqualTo(String.class);
  }

  // coercion through the factory -----------------------------------------------------------------------------------

  @Test
  public void shouldCoerceThroughTheFactory() {
    assertThat(factory.coerceToType("42", Integer.class)).isEqualTo(42);
    assertThat(factory.coerceToType(42, String.class)).isEqualTo("42");
    assertThat(factory.coerceToType(null, String.class)).isEqualTo("");
  }

  @Test
  public void shouldCoerceTheResultToTheExpectedType() {
    set("count", 42);

    assertThat(factory.createValueExpression(context, "${count}", String.class).getValue(context))
        .isEqualTo("42");
  }

  // refusals -----------------------------------------------------------------------------------------------------

  @Test
  public void shouldRefuseAMalformedExpression() {
    assertThatThrownBy(() -> factory.createValueExpression(context, "${1 +}", Object.class))
        .isInstanceOf(ELException.class);
  }

  @Test
  public void shouldRefuseAValueExpressionWithoutAnExpectedType() {
    assertThatThrownBy(() -> factory.createValueExpression(context, "${1}", null))
        .isInstanceOf(NullPointerException.class);
  }
}
