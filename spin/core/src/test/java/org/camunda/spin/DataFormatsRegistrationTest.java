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
package org.camunda.spin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.spin.spi.CustomDataFormatProvider;
import org.camunda.spin.spi.DataFormat;
import org.camunda.spin.spi.DataFormatConfigurator;
import org.camunda.spin.spi.ExampleCustomDataFormat;
import org.camunda.spin.spi.SpinDataFormatException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DataFormatsRegistrationTest {

  protected Map<String, DataFormat<?>> originalFormats;

  @Before
  public void saveGlobalState() {
    originalFormats = DataFormats.INSTANCE.availableDataFormats;
  }

  @After
  public void restoreGlobalState() {
    DataFormats.INSTANCE.availableDataFormats = originalFormats;
  }

  @Test
  public void shouldExposeGlobalInstance() {
    assertThat(DataFormats.getInstance()).isSameAs(DataFormats.INSTANCE);
  }

  @Test
  public void shouldProvideXmlAndJsonShortcuts() {
    ExampleCustomDataFormat xml = new ExampleCustomDataFormat(DataFormats.XML_DATAFORMAT_NAME);
    ExampleCustomDataFormat json = new ExampleCustomDataFormat(DataFormats.JSON_DATAFORMAT_NAME);
    Map<String, DataFormat<?>> formats = new HashMap<>();
    formats.put(xml.getName(), xml);
    formats.put(json.getName(), json);
    DataFormats.INSTANCE.availableDataFormats = formats;

    assertThat((Object) DataFormats.xml()).isSameAs(xml);
    assertThat((Object) DataFormats.json()).isSameAs(json);
    assertThat(DataFormats.getAvailableDataFormats()).containsExactlyInAnyOrder(xml, json);
  }

  @Test
  public void shouldReturnCopyOfAvailableFormats() {
    DataFormats dataFormats = new DataFormats();
    dataFormats.availableDataFormats = new HashMap<>();
    dataFormats.availableDataFormats.put("a", new ExampleCustomDataFormat("a"));

    dataFormats.getAllAvailableDataFormats().clear();

    assertThat(dataFormats.getAllAvailableDataFormats()).hasSize(1);
    assertThat(dataFormats.getDataFormatByName("a")).isNotNull();
    assertThat(dataFormats.getDataFormatByName("missing")).isNull();
  }

  @Test
  public void shouldRegisterProviderWithDefaultProperties() {
    DataFormats dataFormats = new DataFormats();
    Map<String, DataFormat<?>> formats = new HashMap<>();
    CustomDataFormatProvider provider = new CustomDataFormatProvider();

    dataFormats.registerProvider(formats, provider);

    assertThat(formats).containsKey(CustomDataFormatProvider.NAME);
    assertThat(formats.get(CustomDataFormatProvider.NAME)).isSameAs(CustomDataFormatProvider.DATA_FORMAT);
  }

  @Test
  public void shouldRejectDuplicateProvider() {
    DataFormats dataFormats = new DataFormats();
    Map<String, DataFormat<?>> formats = new HashMap<>();
    CustomDataFormatProvider provider = new CustomDataFormatProvider();
    dataFormats.registerProvider(formats, provider);

    assertThatThrownBy(() -> dataFormats.registerProvider(formats, provider))
      .isInstanceOf(SpinDataFormatException.class)
      .hasMessageContaining("Multiple providers found for dataformat '" + CustomDataFormatProvider.NAME + "'");
  }

  @Test
  public void shouldOnlyConfigureMatchingDataFormats() {
    DataFormats dataFormats = new DataFormats();
    ExampleCustomDataFormat custom = new ExampleCustomDataFormat("custom");
    DataFormat<?> other = new DataFormat<org.camunda.spin.json.SpinJsonNode>() {
      public String getName() { return "other"; }
      public Class<? extends org.camunda.spin.json.SpinJsonNode> getWrapperType() { return null; }
      public org.camunda.spin.json.SpinJsonNode createWrapperInstance(Object parameter) { return null; }
      public org.camunda.spin.spi.DataFormatReader getReader() { return null; }
      public org.camunda.spin.spi.DataFormatWriter getWriter() { return null; }
      public org.camunda.spin.spi.DataFormatMapper getMapper() { return null; }
    };
    Map<String, DataFormat<?>> formats = new HashMap<>();
    formats.put("custom", custom);
    formats.put("other", other);

    List<Object> configured = new ArrayList<>();
    DataFormatConfigurator<ExampleCustomDataFormat> configurator = new DataFormatConfigurator<ExampleCustomDataFormat>() {
      public Class<ExampleCustomDataFormat> getDataFormatClass() { return ExampleCustomDataFormat.class; }
      public void configure(ExampleCustomDataFormat dataFormat) { configured.add(dataFormat); }
    };

    dataFormats.applyConfigurator(formats, configurator);

    assertThat(configured).containsExactly(custom);
  }

  @Test
  public void shouldLoadFormatsThroughStaticApi() {
    DataFormats.loadDataFormats(DataFormats.class.getClassLoader());
    assertThat(DataFormats.INSTANCE.availableDataFormats).isNotNull();

    DataFormats.INSTANCE.availableDataFormats = null;
    DataFormats.loadDataFormats();
    assertThat(DataFormats.INSTANCE.availableDataFormats).isNotNull();

    DataFormats.INSTANCE.availableDataFormats = null;
    DataFormats.loadDataFormats(null, Collections.emptyList());
    assertThat(DataFormats.INSTANCE.availableDataFormats).isNotNull();

    DataFormats.INSTANCE.availableDataFormats = null;
    DataFormats.loadDataFormats(null, new HashMap<String, Object>());
    assertThat(DataFormats.INSTANCE.availableDataFormats).isNotNull();
  }

  @Test
  public void shouldLazilyInitializeFormats() {
    DataFormats dataFormats = new DataFormats();
    assertThat(dataFormats.availableDataFormats).isNull();

    assertThat(dataFormats.getDataFormatByName("unknown")).isNull();

    assertThat(dataFormats.availableDataFormats).isNotNull();
  }
}
