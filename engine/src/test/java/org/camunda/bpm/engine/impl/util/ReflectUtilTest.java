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
package org.camunda.bpm.engine.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import org.camunda.bpm.engine.ProcessEngineException;
import org.junit.Test;

/**
 * Covers {@link ReflectUtil}.
 *
 * <p>Two of its rules are easy to get wrong and are pinned deliberately: looking a field up walks
 * the superclass chain, which getDeclaredField alone does not, and the setter lookup accepts a
 * parameter type the field can be assigned to rather than an exact match.
 */
public class ReflectUtilTest {

  public static class Parent {

    protected String inherited = "from parent";
  }

  public static class Child extends Parent {

    protected String own = "from child";

    public void setOwn(String own) {
      this.own = own;
    }

    public void setNumber(Number number) {
    }

    public String greet(String greeting) {
      return greeting + "!";
    }
  }

  public static class Ambiguous {

    public void setValue(String value) {
    }

    public void setValue(Integer value) {
    }
  }

  public static class NeedsArguments {

    protected final String value;

    public NeedsArguments(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public static class Unbuildable {

    private Unbuildable() {
    }
  }

  // class loading -------------------------------------------------------------------------------

  @Test
  public void shouldExposeAClassLoader() {
    assertThat(ReflectUtil.getClassLoader()).isNotNull();
  }

  @Test
  public void shouldLoadAClassByName() {
    assertThat(ReflectUtil.loadClass("java.lang.String")).isEqualTo(String.class);
  }

  @Test
  public void shouldRefuseAClassThatIsNotOnTheClasspath() {
    assertThatThrownBy(() -> ReflectUtil.loadClass("no.such.Class"))
        .isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldLoadAClassThroughACustomClassLoader() throws ClassNotFoundException {
    Class<? extends CharSequence> loaded = ReflectUtil.loadClass("java.lang.String",
        getClass().getClassLoader(), CharSequence.class);

    assertThat(loaded).isEqualTo(String.class);
  }

  // resources ----------------------------------------------------------------------------------------

  @Test
  public void shouldFindAResourceOnTheClasspath() {
    URL url = ReflectUtil.getResource("logging.properties");
    InputStream stream = ReflectUtil.getResourceAsStream("logging.properties");

    assertThat(url).isNotNull();
    assertThat(stream).isNotNull();
    IoUtil.closeSilently(stream);
  }

  @Test
  public void shouldAnswerNullForAResourceThatIsNotThere() {
    assertThat(ReflectUtil.getResource("no-such-resource.properties")).isNull();
    assertThat(ReflectUtil.getResourceAsStream("no-such-resource.properties")).isNull();
  }

  @Test
  public void shouldRenderAResourceUrlAsAString() {
    assertThat(ReflectUtil.getResourceUrlAsString("logging.properties")).contains("logging");
  }

  @Test
  public void shouldConvertAUrlToAUri() throws Exception {
    URL url = ReflectUtil.getResource("logging.properties");

    assertThat(ReflectUtil.urlToURI(url)).isNotNull();
  }

  // instantiation -------------------------------------------------------------------------------------

  @Test
  public void shouldInstantiateByNameAndByType() {
    assertThat(ReflectUtil.instantiate("java.lang.String")).isInstanceOf(String.class);
    assertThat(ReflectUtil.instantiate(String.class)).isInstanceOf(String.class);
    assertThat(ReflectUtil.createInstance(String.class)).isInstanceOf(String.class);
  }

  @Test
  public void shouldInstantiateWithConstructorArguments() {
    Object instance = ReflectUtil.instantiate(NeedsArguments.class.getName(),
        new Object[] { "value" });

    assertThat(instance).isInstanceOf(NeedsArguments.class);
    assertThat(((NeedsArguments) instance).getValue()).isEqualTo("value");
  }

  @Test
  public void shouldRefuseToInstantiateWhatItCannot() {
    assertThatThrownBy(() -> ReflectUtil.instantiate(Unbuildable.class))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() -> ReflectUtil.instantiate("no.such.Class"))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() -> ReflectUtil.instantiate(NeedsArguments.class.getName(),
        new Object[] { 1 })).isInstanceOf(ProcessEngineException.class);
  }

  // fields ---------------------------------------------------------------------------------------------

  @Test
  public void shouldFindAFieldDeclaredOnTheClass() {
    assertThat(ReflectUtil.getField("own", new Child())).isNotNull();
    assertThat(ReflectUtil.getField("own", Child.class)).isNotNull();
  }

  /** getDeclaredField stops at the class itself, so the lookup has to climb by hand. */
  @Test
  public void shouldFindAFieldInheritedFromASuperclass() {
    assertThat(ReflectUtil.getField("inherited", Child.class)).isNotNull();
  }

  @Test
  public void shouldAnswerNullForAFieldNobodyDeclares() {
    assertThat(ReflectUtil.getField("absent", Child.class)).isNull();
  }

  @Test
  public void shouldWriteAField() {
    Child child = new Child();
    Field field = ReflectUtil.getField("own", child);

    ReflectUtil.setField(field, child, "changed");

    assertThat(child.own).isEqualTo("changed");
  }

  @Test
  public void shouldRefuseWritingAValueOfTheWrongType() {
    Child child = new Child();
    Field field = ReflectUtil.getField("own", child);

    assertThatThrownBy(() -> ReflectUtil.setField(field, child, 42))
        .isInstanceOf(ProcessEngineException.class);
  }

  // setters -----------------------------------------------------------------------------------------------

  @Test
  public void shouldFindASetterByFieldName() {
    assertThat(ReflectUtil.getSetter("own", Child.class, String.class)).isNotNull();
  }

  /** The parameter type only has to accept the field type, it does not have to match it. */
  @Test
  public void shouldAcceptASetterThatTakesASupertype() {
    assertThat(ReflectUtil.getSetter("number", Child.class, Integer.class)).isNotNull();
  }

  @Test
  public void shouldAnswerNullWhenNoSetterFits() {
    assertThat(ReflectUtil.getSetter("own", Child.class, Integer.class)).isNull();
    assertThat(ReflectUtil.getSetter("absent", Child.class, String.class)).isNull();
  }

  @Test
  public void shouldFindTheSingleSetter() {
    assertThat(ReflectUtil.getSingleSetter("own", Child.class)).isNotNull();
    assertThat(ReflectUtil.getSingleSetter("absent", Child.class)).isNull();
  }

  @Test
  public void shouldRefuseToChooseBetweenTwoSetters() {
    assertThatThrownBy(() -> ReflectUtil.getSingleSetter("value", Ambiguous.class))
        .isInstanceOf(ProcessEngineException.class);
  }

  // methods -------------------------------------------------------------------------------------------------

  @Test
  public void shouldInvokeAMethodByName() {
    assertThat(ReflectUtil.invoke(new Child(), "greet", new Object[] { "Hi" })).isEqualTo("Hi!");
  }

  @Test
  public void shouldRefuseInvokingAMethodThatIsNotThere() {
    assertThatThrownBy(() -> ReflectUtil.invoke(new Child(), "absent", new Object[0]))
        .isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldLookUpAMethodByItsSignature() {
    Method method = ReflectUtil.getMethod(Child.class, "greet", String.class);

    assertThat(method).isNotNull();
    assertThat(ReflectUtil.getMethod(Child.class, "absent")).isNull();
  }
}
