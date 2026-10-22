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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.NoSuchElementException;

import java.util.ArrayList;
import java.util.List;

import org.camunda.spin.SpinList;
import org.camunda.spin.impl.xml.dom.DomXmlAttributeIterable;
import org.camunda.spin.impl.xml.dom.format.DomXmlDataFormat;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.camunda.spin.xml.SpinXmlAttribute;
import org.camunda.spin.xml.SpinXmlAttributeException;
import org.camunda.spin.xml.SpinXmlElement;
import org.junit.Test;

public class XmlDomAttributeDetailsTest {

  protected SpinXmlElement root =
    XML("<root xmlns:a=\"urn:a\" xmlns:b=\"urn:b\" a:one=\"1\" b:two=\"2\" three=\"3\"/>");

  @Test
  public void shouldExposeNameNamespaceAndPrefix() {
    SpinXmlAttribute one = root.attrNs("urn:a", "one");

    assertThat(one.name()).isEqualTo("one");
    assertThat(one.namespace()).isEqualTo("urn:a");
    assertThat(one.prefix()).isEqualTo("a");
    assertThat(one.value()).isEqualTo("1");
    assertThat(one.toString()).isEqualTo("1");
    assertThat(one.hasPrefix("a")).isTrue();
    assertThat(one.hasPrefix("b")).isFalse();
    assertThat(one.hasPrefix(null)).isFalse();
    assertThat(one.hasNamespace("urn:a")).isTrue();
    assertThat(one.hasNamespace("urn:b")).isFalse();
    assertThat(one.hasNamespace(null)).isFalse();
  }

  @Test
  public void shouldHandleAttributeWithoutNamespace() {
    SpinXmlAttribute three = root.attr("three");

    assertThat(three.prefix()).isNull();
    assertThat(three.hasPrefix(null)).isTrue();
    assertThat(three.hasPrefix("a")).isFalse();
    assertThat(three.hasNamespace(null)).isTrue();
    assertThat(three.hasNamespace("urn:a")).isFalse();
  }

  @Test
  public void shouldUpdateValue() {
    SpinXmlAttribute three = root.attr("three");

    assertThat(three.value("changed")).isSameAs(three);
    assertThat(root.attr("three").value()).isEqualTo("changed");
  }

  @Test
  public void shouldRejectNullValue() {
    SpinXmlAttribute three = root.attr("three");

    assertThatThrownBy(() -> three.value(null)).isInstanceOf(SpinXmlAttributeException.class);
  }

  @Test
  public void shouldRemoveAttributeAndReturnOwner() {
    SpinXmlElement owner = root.attr("three").remove();

    assertThat(owner.hasAttr("three")).isFalse();
    assertThat(owner.hasAttrNs("urn:a", "one")).isTrue();
  }

  @Test
  public void shouldWriteValueToWriter() {
    StringWriter writer = new StringWriter();

    root.attr("three").writeToWriter(writer);

    assertThat(writer.toString()).isEqualTo("3");
  }

  @Test
  public void shouldWrapWriterFailure() {
    Writer failing = new Writer() {
      public void write(char[] cbuf, int off, int len) throws IOException {
        throw new IOException("boom");
      }

      public void flush() {
      }

      public void close() {
      }
    };

    assertThatThrownBy(() -> root.attr("three").writeToWriter(failing))
      .isInstanceOf(SpinXmlAttributeException.class);
  }

  @Test
  public void shouldReportDataFormatName() {
    assertThat(((org.camunda.spin.impl.xml.dom.DomXmlAttribute) root.attr("three")).getDataFormatName())
      .isEqualTo("application/xml");
  }

  @Test
  public void shouldIterateAttributesOfNamespace() {
    SpinList<SpinXmlAttribute> all = root.attrs();
    SpinList<SpinXmlAttribute> namespaced = root.attrs("urn:a");

    assertThat(all).hasSize(5);
    assertThat(namespaced).hasSize(1);
    assertThat(namespaced.get(0).name()).isEqualTo("one");
  }

  protected NodeList attributeNodes() {
    NamedNodeMap map = ((Element) root.unwrap()).getAttributes();
    List<Node> nodes = new ArrayList<>();
    for (int i = 0; i < map.getLength(); i++) {
      nodes.add(map.item(i));
    }
    return new NodeList() {
      public Node item(int index) {
        return nodes.get(index);
      }

      public int getLength() {
        return nodes.size();
      }
    };
  }

  @Test
  public void shouldIterateAttributeIterableAndFilterByNamespace() {
    DomXmlDataFormat format = new DomXmlDataFormat("application/xml");

    Iterator<SpinXmlAttribute> all = new DomXmlAttributeIterable(attributeNodes(), format).iterator();
    int count = 0;
    while (all.hasNext()) {
      all.next();
      count++;
    }
    Iterator<SpinXmlAttribute> filtered = new DomXmlAttributeIterable(attributeNodes(), format, "urn:b").iterator();

    assertThat(count).isEqualTo(5);
    assertThat(filtered.hasNext()).isTrue();
    assertThat(filtered.next().name()).isEqualTo("two");
    assertThat(filtered.hasNext()).isFalse();
  }

  @Test
  public void shouldFailIteratingBeyondEnd() {
    DomXmlDataFormat format = new DomXmlDataFormat("application/xml");
    Iterator<SpinXmlAttribute> it = new DomXmlAttributeIterable(attributeNodes(), format, "urn:none").iterator();

    assertThat(it.hasNext()).isFalse();
    assertThatThrownBy(it::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  public void shouldNotSupportRemovalThroughIterator() {
    DomXmlDataFormat format = new DomXmlDataFormat("application/xml");
    Iterator<SpinXmlAttribute> it = new DomXmlAttributeIterable(attributeNodes(), format).iterator();
    it.next();

    assertThatThrownBy(it::remove).isInstanceOf(UnsupportedOperationException.class);
  }
}
