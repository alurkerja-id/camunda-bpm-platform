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

import java.util.HashMap;
import java.util.Map;

import org.camunda.spin.SpinList;
import org.camunda.spin.xml.SpinXPathException;
import org.camunda.spin.xml.SpinXmlAttribute;
import org.camunda.spin.xml.SpinXmlElement;
import org.junit.Test;

public class XmlDomXPathErrorsTest {

  protected static final String XML_DOC =
    "<root xmlns:a=\"urn:a\" xmlns=\"urn:d\"><a:child a:attr=\"1\" other=\"2\">text</a:child><child/></root>";

  protected SpinXmlElement root = XML(XML_DOC);

  @Test
  public void shouldFailForInvalidExpression() {
    assertThatThrownBy(() -> root.xPath("//[").element()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("//[").elementList()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("//[").attribute()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("//[").attributeList()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("//[").string()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("//[").number()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("//[").bool()).isInstanceOf(SpinXPathException.class);
  }

  @Test
  public void shouldRejectDocumentRootExpression() {
    assertThatThrownBy(() -> root.xPath("/").element()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("/").string()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("/").number()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("/").bool()).isInstanceOf(SpinXPathException.class);
  }

  @Test
  public void shouldFailWhenResultIsNotOfRequestedType() {
    // selecting an attribute as element and an element as attribute
    assertThatThrownBy(() -> root.xPath("//@other").element()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("//a:child").ns("a", "urn:a").attribute())
      .isInstanceOf(SpinXPathException.class);
  }

  @Test
  public void shouldFailWhenNothingIsFound() {
    assertThatThrownBy(() -> root.xPath("//missing").element()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("//missing").elementList()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("//@missing").attribute()).isInstanceOf(SpinXPathException.class);
    assertThatThrownBy(() -> root.xPath("//@missing").attributeList()).isInstanceOf(SpinXPathException.class);
  }

  @Test
  public void shouldEvaluateWithNamespaceMap() {
    Map<String, String> namespaces = new HashMap<>();
    namespaces.put("x", "urn:a");

    SpinList<SpinXmlElement> elements = root.xPath("//x:child").ns(namespaces).elementList();
    SpinXmlAttribute attribute = root.xPath("//x:child/@x:attr").ns(namespaces).attribute();
    SpinList<SpinXmlAttribute> attributes = root.xPath("//x:child/@*").ns(namespaces).attributeList();

    assertThat(elements).hasSize(1);
    assertThat(attribute.value()).isEqualTo("1");
    assertThat(attributes).hasSize(2);
  }

  @Test
  public void shouldEvaluateScalarResults() {
    assertThat(root.xPath("count(//*)").number()).isEqualTo(3.0);
    assertThat(root.xPath("boolean(//*)").bool()).isTrue();
    assertThat(root.xPath("string(//@other)").string()).isEqualTo("2");
  }
}
