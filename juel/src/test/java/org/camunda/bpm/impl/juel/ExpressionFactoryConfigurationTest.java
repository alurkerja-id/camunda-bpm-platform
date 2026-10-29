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
import java.util.Properties;
import org.junit.Test;

/** Covers how the factory reads its configuration: feature switches, cache size and plug-in classes. */
public class ExpressionFactoryConfigurationTest {

  /** A builder that takes features, as the factory looks for. */
  public static class FeatureBuilder extends Builder {
    public FeatureBuilder(Builder.Feature... features) {
      super(features);
    }
  }

  /** A builder subclass with only a default constructor, which the factory cannot hand features to. */
  public static class PlainBuilder extends Builder {
    public PlainBuilder() {
      super();
    }
  }

  public static class NotABuilder implements TreeBuilder {
    @Override
    public Tree build(String expression) {
      return null;
    }
  }

  public static class CustomConverter extends TypeConverterImpl {
    private static final long serialVersionUID = 1L;
  }

  public static class BrokenConverter extends TypeConverterImpl {
    private static final long serialVersionUID = 1L;

    public BrokenConverter(String argument) {
      super();
    }
  }

  protected static final String BUILDER_KEY = TreeBuilder.class.getName();
  protected static final String CONVERTER_KEY = TypeConverter.class.getName();

  @Test
  public void shouldEnableMethodInvocationsFromProperties() {
    Properties properties = new Properties();
    properties.setProperty(ExpressionFactoryImpl.PROP_METHOD_INVOCATIONS, "true");

    ExpressionFactoryImpl factory = new ExpressionFactoryImpl(properties);

    SimpleContext context = new SimpleContext(new SimpleResolver());
    context.setVariable("text", factory.createValueExpression("abc", String.class));
    assertThat(factory.createValueExpression(context, "${text.length()}", Object.class).getValue(context))
        .isEqualTo(3);
  }

  @Test
  public void shouldEnableVarArgsAndNullPropertiesFromProperties() {
    Properties properties = new Properties();
    properties.setProperty(ExpressionFactoryImpl.PROP_VAR_ARGS, "true");
    properties.setProperty(ExpressionFactoryImpl.PROP_NULL_PROPERTIES, "true");
    properties.setProperty(ExpressionFactoryImpl.PROP_CACHE_SIZE, "5");

    ExpressionFactoryImpl factory = new ExpressionFactoryImpl(properties);

    assertThat(factory.createValueExpression(new SimpleContext(), "${1 + 1}", Object.class)
        .getValue(new SimpleContext())).isEqualTo(2L);
  }

  @Test
  public void shouldRefuseANonNumericCacheSize() {
    Properties properties = new Properties();
    properties.setProperty(ExpressionFactoryImpl.PROP_CACHE_SIZE, "many");

    assertThatThrownBy(() -> new ExpressionFactoryImpl(properties))
        .isInstanceOf(ELException.class)
        .hasMessageContaining(ExpressionFactoryImpl.PROP_CACHE_SIZE);
  }

  @Test
  public void shouldRunWithoutACacheWhenTheSizeIsZero() {
    Properties properties = new Properties();
    properties.setProperty(ExpressionFactoryImpl.PROP_CACHE_SIZE, "0");

    ExpressionFactoryImpl factory = new ExpressionFactoryImpl(properties);

    assertThat(factory.createValueExpression(new SimpleContext(), "${2 * 3}", Object.class)
        .getValue(new SimpleContext())).isEqualTo(6L);
  }

  @Test
  public void shouldCreateAConfiguredTreeBuilder() {
    ExpressionFactoryImpl factory = new ExpressionFactoryImpl();
    Properties properties = new Properties();
    properties.setProperty(BUILDER_KEY, FeatureBuilder.class.getName());

    TreeBuilder builder = factory.createTreeBuilder(properties, Builder.Feature.METHOD_INVOCATIONS);

    assertThat(builder).isInstanceOf(FeatureBuilder.class);
    assertThat(((Builder) builder).isEnabled(Builder.Feature.METHOD_INVOCATIONS)).isTrue();
  }

  @Test
  public void shouldCreateAConfiguredTreeBuilderThatIsNotABuilder() {
    ExpressionFactoryImpl factory = new ExpressionFactoryImpl();
    Properties properties = new Properties();
    properties.setProperty(BUILDER_KEY, NotABuilder.class.getName());

    assertThat(factory.createTreeBuilder(properties)).isInstanceOf(NotABuilder.class);
  }

  @Test
  public void shouldRefuseABuilderWithoutAFeatureConstructor() {
    ExpressionFactoryImpl factory = new ExpressionFactoryImpl();
    Properties properties = new Properties();
    properties.setProperty(BUILDER_KEY, PlainBuilder.class.getName());

    assertThatThrownBy(() -> factory.createTreeBuilder(properties, Builder.Feature.VARARGS))
        .isInstanceOf(ELException.class)
        .hasMessageContaining("could not be instantiated");
  }

  @Test
  public void shouldFallBackToThePlainBuilderWithoutConfiguration() {
    ExpressionFactoryImpl factory = new ExpressionFactoryImpl();

    assertThat(factory.createTreeBuilder(null)).isExactlyInstanceOf(Builder.class);
    assertThat(factory.createTreeBuilder(new Properties())).isExactlyInstanceOf(Builder.class);
  }

  @Test
  public void shouldRefuseAnUnknownBuilderClass() {
    ExpressionFactoryImpl factory = new ExpressionFactoryImpl();
    Properties properties = new Properties();
    properties.setProperty(BUILDER_KEY, "does.not.Exist");

    assertThatThrownBy(() -> factory.createTreeBuilder(properties))
        .isInstanceOf(ELException.class)
        .hasMessageContaining("not found");
  }

  @Test
  public void shouldCreateAConfiguredTypeConverter() {
    ExpressionFactoryImpl factory = new ExpressionFactoryImpl();
    Properties properties = new Properties();
    properties.setProperty(CONVERTER_KEY, CustomConverter.class.getName());

    assertThat(factory.createTypeConverter(properties)).isInstanceOf(CustomConverter.class);
  }

  @Test
  public void shouldFallBackToTheDefaultTypeConverter() {
    ExpressionFactoryImpl factory = new ExpressionFactoryImpl();

    assertThat(factory.createTypeConverter(new Properties())).isSameAs(TypeConverter.DEFAULT);
    assertThat(factory.createTypeConverter(null)).isSameAs(TypeConverter.DEFAULT);
  }

  @Test
  public void shouldRefuseATypeConverterThatCannotBeInstantiated() {
    ExpressionFactoryImpl factory = new ExpressionFactoryImpl();
    Properties properties = new Properties();
    properties.setProperty(CONVERTER_KEY, BrokenConverter.class.getName());

    assertThatThrownBy(() -> factory.createTypeConverter(properties))
        .isInstanceOf(ELException.class)
        .hasMessageContaining("could not be instantiated");
  }

}
