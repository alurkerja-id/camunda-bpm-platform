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
package org.camunda.spin.xml.dom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.spin.Spin.XML;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.camunda.spin.DataFormats;
import org.camunda.spin.impl.xml.dom.format.DomXmlDataFormat;
import org.camunda.spin.impl.xml.dom.format.spi.DefaultJaxBContextProvider;
import org.camunda.spin.xml.SpinXmlDataFormatException;
import org.camunda.spin.xml.SpinXmlElement;
import org.camunda.spin.xml.SpinXmlElementException;
import org.junit.Test;

public class DomXmlJaxbAndWriterTest {

  // an interface cannot be bound by JAXB
  private static final Class<?> UNBINDABLE = Runnable.class;

  private final DefaultJaxBContextProvider provider = new DefaultJaxBContextProvider();

  @Test
  public void shouldFailToCreateContextForUnbindableType() {
    assertThatThrownBy(() -> provider.getContext(UNBINDABLE)).isInstanceOf(SpinXmlDataFormatException.class);
  }

  @Test
  public void shouldFailToCreateMarshallerForUnbindableType() {
    assertThatThrownBy(() -> provider.createMarshaller(UNBINDABLE)).isInstanceOf(SpinXmlDataFormatException.class);
  }

  @Test
  public void shouldFailToCreateUnmarshallerForUnbindableType() {
    assertThatThrownBy(() -> provider.createUnmarshaller(UNBINDABLE)).isInstanceOf(SpinXmlDataFormatException.class);
  }

  @Test
  public void shouldCreateMarshallerAndUnmarshallerForBindableType() {
    assertThat(provider.createMarshaller(String.class)).isNotNull();
    assertThat(provider.createUnmarshaller(String.class)).isNotNull();
  }

  @Test
  public void shouldMapperExposeCanonicalTypeNameAndCapability() {
    DomXmlDataFormat format = (DomXmlDataFormat) DataFormats.xml();

    assertThat(format.getMapper().getCanonicalTypeName("text")).isEqualTo("java.lang.String");
    assertThat(format.getMapper().canMap(new Object())).isTrue();
    assertThat(format.getMapper().canMap(null)).isFalse();
  }

  @Test
  public void shouldFailMappingXmlToUnbindableType() {
    SpinXmlElement element = XML("<a/>");

    assertThatThrownBy(() -> element.mapTo(UNBINDABLE)).isInstanceOf(SpinXmlDataFormatException.class);
  }

  @Test
  public void shouldWrapWriterFailure() {
    Writer failing = new Writer() {
      public void write(char[] cbuf, int off, int len) throws IOException {
        throw new IOException("boom");
      }

      public void flush() throws IOException {
        throw new IOException("boom");
      }

      public void close() {
      }
    };

    assertThatThrownBy(() -> XML("<a><b/></a>").writeToWriter(failing)).isInstanceOf(SpinXmlElementException.class);
  }

  @Test
  public void shouldWriteWithoutPrettyPrint() {
    DomXmlDataFormat format = new DomXmlDataFormat("application/xml");
    format.setPrettyPrint(false);
    SpinXmlElement element = org.camunda.spin.Spin.S("<a><b/></a>", format);
    StringWriter writer = new StringWriter();

    element.writeToWriter(writer);

    assertThat(writer.toString()).contains("<a><b/></a>");
  }

  @Test
  public void shouldFailWithInvalidFormattingConfiguration() {
    DomXmlDataFormat format = new DomXmlDataFormat("application/xml");

    // reloading templates with an unparseable stylesheet fails
    assertThatThrownBy(() -> format.setFormattingConfiguration(new ByteArrayInputStream("not a stylesheet".getBytes())))
      .isInstanceOf(SpinXmlDataFormatException.class);

    
  }
}
