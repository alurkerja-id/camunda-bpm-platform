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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;

import org.camunda.spin.impl.xml.dom.query.DomXPathNamespaceResolver;
import org.camunda.spin.xml.SpinXmlElement;
import org.junit.Before;
import org.junit.Test;

public class DomXPathNamespaceResolverTest {

  protected SpinXmlElement element;
  protected DomXPathNamespaceResolver resolver;

  @Before
  public void setUp() {
    element = mock(SpinXmlElement.class);
    when(element.namespace()).thenReturn("urn:default");
    when(element.name()).thenReturn("urn:name");
    resolver = new DomXPathNamespaceResolver(element);
  }

  protected List<String> prefixes(String uri) {
    List<String> result = new ArrayList<>();
    Iterator<?> it = resolver.getPrefixes(uri);
    while (it.hasNext()) {
      result.add((String) it.next());
    }
    return result;
  }

  @Test
  public void shouldResolveReservedPrefixes() {
    assertThat(resolver.getNamespaceURI(XMLConstants.XML_NS_PREFIX)).isEqualTo(XMLConstants.XML_NS_URI);
    assertThat(resolver.getNamespaceURI(XMLConstants.XMLNS_ATTRIBUTE)).isEqualTo(XMLConstants.XMLNS_ATTRIBUTE_NS_URI);
  }

  @Test
  public void shouldResolveDefaultPrefixToElementNamespace() {
    assertThat(resolver.getNamespaceURI(XMLConstants.DEFAULT_NS_PREFIX)).isEqualTo("urn:default");
  }

  @Test
  public void shouldResolveRegisteredAndUnknownPrefixes() {
    resolver.setNamespace("a", "urn:a");

    assertThat(resolver.getNamespaceURI("a")).isEqualTo("urn:a");
    assertThat(resolver.getNamespaceURI("unknown")).isEqualTo(XMLConstants.NULL_NS_URI);
  }

  @Test
  public void shouldResolvePrefixesForReservedUris() {
    assertThat(resolver.getPrefix(XMLConstants.XML_NS_URI)).isEqualTo(XMLConstants.XML_NS_PREFIX);
    assertThat(resolver.getPrefix(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)).isEqualTo(XMLConstants.XMLNS_ATTRIBUTE);
  }

  @Test
  public void shouldResolveDefaultPrefixForElementName() {
    assertThat(resolver.getPrefix("urn:name")).isEqualTo(XMLConstants.DEFAULT_NS_PREFIX);
  }

  @Test
  public void shouldResolvePrefixForRegisteredUri() {
    resolver.setNamespace("a", "urn:a");

    assertThat(resolver.getPrefix("urn:a")).isEqualTo("a");
    assertThat(resolver.getPrefix("urn:none")).isNull();
  }

  @Test
  public void shouldListPrefixesForReservedUris() {
    assertThat(prefixes(XMLConstants.XML_NS_URI)).containsExactly(XMLConstants.XML_NS_PREFIX);
    assertThat(prefixes(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)).containsExactly(XMLConstants.XMLNS_ATTRIBUTE);
  }

  @Test
  public void shouldListDefaultAndRegisteredPrefixes() {
    Map<String, String> namespaces = new HashMap<>();
    namespaces.put("a", "urn:default");
    namespaces.put("b", "urn:default");
    namespaces.put("c", "urn:c");
    resolver.setNamespaces(namespaces);

    assertThat(prefixes("urn:default")).containsExactlyInAnyOrder(XMLConstants.DEFAULT_NS_PREFIX, "a", "b");
    assertThat(prefixes("urn:c")).containsExactly("c");
    assertThat(prefixes("urn:none")).isEmpty();
  }

  @Test
  public void shouldRejectNullArguments() {
    assertThatThrownBy(() -> resolver.getNamespaceURI(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolver.getPrefix(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolver.getPrefixes(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolver.setNamespace(null, "urn:a")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolver.setNamespace("a", null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolver.setNamespaces(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
