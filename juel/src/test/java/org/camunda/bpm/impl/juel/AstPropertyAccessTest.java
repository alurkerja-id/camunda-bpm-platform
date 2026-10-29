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
import jakarta.el.MethodNotFoundException;
import jakarta.el.PropertyNotFoundException;
import jakarta.el.ValueExpression;
import org.junit.Before;
import org.junit.Test;

/** Drives the property and method halves of {@link AstProperty} through real expressions. */
public class AstPropertyAccessTest {

  public static class Bean {

    protected String name = "ana";
    protected int count;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getCount() {
      return count;
    }

    public String greet(String greeting) {
      return greeting + ", " + name;
    }

    public String fail() {
      throw new IllegalStateException("boom");
    }

    public int number() {
      return 7;
    }
  }

  protected ExpressionFactoryImpl factory;
  protected SimpleContext context;

  @Before
  public void setUp() {
    factory = new ExpressionFactoryImpl();
    context = new SimpleContext(new SimpleResolver());
    context.setVariable("bean", factory.createValueExpression(new Bean(), Bean.class));
    context.setVariable("nothing", factory.createValueExpression(null, Object.class));
  }

  protected ValueExpression value(String expression) {
    return factory.createValueExpression(context, expression, Object.class);
  }

  protected MethodExpression method(String expression, Class<?> returnType, Class<?>... params) {
    return factory.createMethodExpression(context, expression, returnType, params);
  }

  @Test
  public void shouldReadAndReportTheTypeOfAProperty() {
    ValueExpression expression = value("${bean.name}");

    assertThat(expression.getValue(context)).isEqualTo("ana");
    assertThat(expression.getType(context)).isEqualTo(String.class);
    assertThat(expression.isReadOnly(context)).isFalse();
  }

  @Test
  public void shouldReadThroughBracketNotation() {
    assertThat(value("${bean['name']}").getValue(context)).isEqualTo("ana");
  }

  @Test
  public void shouldWriteAProperty() {
    ValueExpression expression = value("${bean.name}");

    expression.setValue(context, "bob");

    assertThat(expression.getValue(context)).isEqualTo("bob");
  }

  @Test
  public void shouldReportAGetterOnlyPropertyAsReadOnly() {
    assertThat(value("${bean.count}").isReadOnly(context)).isTrue();
  }

  @Test
  public void shouldYieldNullWhenTheBaseIsNull() {
    assertThat(value("${nothing.name}").getValue(context)).isNull();
  }

  @Test
  public void shouldRefuseTypeReadOnlyAndWriteOnANullBase() {
    ValueExpression expression = value("${nothing.name}");

    assertThatThrownBy(() -> expression.getType(context)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.isReadOnly(context)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.setValue(context, "x")).isInstanceOf(PropertyNotFoundException.class);
  }

  @Test
  public void shouldRefuseAnUnknownProperty() {
    ValueExpression expression = value("${bean.missing}");

    assertThatThrownBy(() -> expression.getValue(context)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.getType(context)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.isReadOnly(context)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.setValue(context, "x")).isInstanceOf(PropertyNotFoundException.class);
  }

  @Test
  public void shouldRefuseANullPropertyKey() {
    context.setVariable("key", factory.createValueExpression(null, Object.class));
    ValueExpression expression = value("${bean[key]}");

    assertThat(expression.getValue(context)).isNull();
    assertThatThrownBy(() -> expression.getType(context)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.isReadOnly(context)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.setValue(context, "x")).isInstanceOf(PropertyNotFoundException.class);
  }

  @Test
  public void shouldDescribeAndInvokeAMethod() {
    MethodExpression expression = method("${bean.greet}", String.class, String.class);

    MethodInfo info = expression.getMethodInfo(context);

    assertThat(info.getName()).isEqualTo("greet");
    assertThat(info.getReturnType()).isEqualTo(String.class);
    assertThat(expression.invoke(context, new Object[] {"hi"})).isEqualTo("hi, ana");
  }

  @Test
  public void shouldInvokeAMethodWithoutDeclaredReturnType() {
    assertThat(method("${bean.number}", null).invoke(context, null)).isEqualTo(7);
  }

  @Test
  public void shouldRefuseAMethodWithTheWrongReturnType() {
    MethodExpression expression = method("${bean.number}", String.class);

    assertThatThrownBy(() -> expression.getMethodInfo(context)).isInstanceOf(MethodNotFoundException.class);
  }

  @Test
  public void shouldRefuseAnUnknownMethod() {
    MethodExpression expression = method("${bean.nope}", String.class);

    assertThatThrownBy(() -> expression.getMethodInfo(context)).isInstanceOf(MethodNotFoundException.class);
    assertThatThrownBy(() -> expression.invoke(context, null)).isInstanceOf(MethodNotFoundException.class);
  }

  @Test
  public void shouldWrapAnExceptionThrownByTheInvokedMethod() {
    MethodExpression expression = method("${bean.fail}", String.class);

    assertThatThrownBy(() -> expression.invoke(context, null))
        .isInstanceOf(ELException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldRefuseMethodAccessOnANullBase() {
    MethodExpression expression = method("${nothing.greet}", String.class, String.class);

    assertThatThrownBy(() -> expression.getMethodInfo(context)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.invoke(context, new Object[] {"hi"}))
        .isInstanceOf(PropertyNotFoundException.class);
  }

  @Test
  public void shouldRefuseMethodAccessWithANullKey() {
    context.setVariable("key", factory.createValueExpression(null, Object.class));
    MethodExpression expression = method("${bean[key]}", String.class);

    assertThatThrownBy(() -> expression.getMethodInfo(context)).isInstanceOf(PropertyNotFoundException.class);
    assertThatThrownBy(() -> expression.invoke(context, null)).isInstanceOf(PropertyNotFoundException.class);
  }

  @Test
  public void shouldInvokeAMethodDirectlyThroughAnExpressionCall() {
    assertThat(value("${bean.greet('yo')}").getValue(context)).isEqualTo("yo, ana");
  }

  @Test
  public void shouldTreatAMethodCallAsAReadOnlyRightValue() {
    ValueExpression expression = value("${bean.greet('yo')}");

    assertThat(expression.getType(context)).isNull();
    assertThat(expression.isReadOnly(context)).isTrue();
    assertThat(expression.getValueReference(context)).isNull();
    assertThatThrownBy(() -> expression.setValue(context, "x")).isInstanceOf(ELException.class);
  }

  @Test
  public void shouldRefuseAMethodCallOnANullBase() {
    assertThatThrownBy(() -> value("${nothing.greet('yo')}").getValue(context))
        .isInstanceOf(PropertyNotFoundException.class);
  }

  @Test
  public void shouldRefuseAMethodCallWithANullName() {
    context.setVariable("key", factory.createValueExpression(null, Object.class));

    assertThatThrownBy(() -> value("${bean[key]('yo')}").getValue(context))
        .isInstanceOf(PropertyNotFoundException.class);
  }

  @Test
  public void shouldRefuseACallToAnUnknownMethod() {
    assertThatThrownBy(() -> value("${bean.nope('yo')}").getValue(context))
        .isInstanceOf(MethodNotFoundException.class);
  }

  @Test
  public void shouldResolveAValueReferenceForAProperty() {
    ValueExpression expression = value("${bean.name}");

    assertThat(expression.getValueReference(context).getProperty()).isEqualTo("name");
  }

}
