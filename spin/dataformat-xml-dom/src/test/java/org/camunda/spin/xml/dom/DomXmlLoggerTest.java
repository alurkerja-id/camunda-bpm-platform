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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.camunda.spin.impl.xml.dom.DomXmlLogger;
import org.camunda.spin.xml.SpinXmlAttribute;
import org.camunda.spin.xml.SpinXmlElement;
import org.junit.Test;
import org.w3c.dom.Node;

public class DomXmlLoggerTest {

  private final DomXmlLogger logger = DomXmlLogger.XML_DOM_LOGGER;
  private final Exception cause = new IllegalStateException("cause");

  private SpinXmlElement element(String name) {
    SpinXmlElement element = mock(SpinXmlElement.class);
    when(element.name()).thenReturn(name);
    when(element.namespace()).thenReturn("urn:ns");
    return element;
  }

  @Test
  public void shouldLogDebugMessagesWithoutFailing() {
    logger.usingDocumentBuilderFactory("factory");
    logger.createdDocumentBuilder();
    logger.documentBuilderFactoryConfiguration("property", "value");
    logger.parsingInput();
  }

  @Test
  public void shouldCreateDataFormatExceptions() {
    assertThat(logger.unableToCreateParser(cause)).hasMessageContaining("Unable to create DocumentBuilder").hasCause(cause);
    assertThat(logger.unableToParseInput(cause)).hasCause(cause);
    assertThat(logger.unableToDetectCanonicalType("param")).isNotNull();
    assertThat(logger.unableToMapInput("param", cause)).hasCause(cause);
    assertThat(logger.unableToDeserialize("node", "com.x.Y", cause))
      .hasMessageContaining("com.x.Y").hasCause(cause);
    assertThat(logger.unableToCreateMarshaller(cause)).hasCause(cause);
    assertThat(logger.unableToCreateContext(cause)).hasCause(cause);
    assertThat(logger.unableToCreateUnmarshaller(cause)).hasCause(cause);
    assertThat(logger.unableToSetEventHandler("com.x.Y", cause))
      .hasMessageContaining("com.x.Y").hasCause(cause);
    assertThat(logger.unableToSetProperty("prop", "com.x.Y", cause))
      .hasMessageContaining("prop").hasMessageContaining("com.x.Y").hasCause(cause);
    assertThat(logger.unableToFindStripSpaceXsl("strip.xsl")).hasMessageContaining("strip.xsl");
    assertThat(logger.unableToLoadFormattingTemplates(cause)).hasCause(cause);
  }

  @Test
  public void shouldCreateAttributeAndLookupExceptions() {
    assertThat(logger.unableToFindAttributeWithNamespaceAndName("urn:x", "attr"))
      .hasMessageContaining("urn:x").hasMessageContaining("attr");
    assertThat(logger.unableToFindChildElementWithNamespaceAndName("urn:x", "child"))
      .hasMessageContaining("urn:x").hasMessageContaining("child");
    assertThat(logger.moreThanOneChildElementFoundForNamespaceAndName("urn:x", "child"))
      .hasMessageContaining("urn:x").hasMessageContaining("child");
    assertThat(logger.unableToSetAttributeValueToNull("urn:x", "attr"))
      .hasMessageContaining("urn:x").hasMessageContaining("attr");
    SpinXmlAttribute attribute = mock(SpinXmlAttribute.class);
    when(attribute.name()).thenReturn("attr");
    assertThat(logger.unableToWriteAttribute(attribute, cause)).hasCause(cause);
  }

  @Test
  public void shouldCreateElementExceptions() {
    SpinXmlElement parent = element("parent");
    SpinXmlElement child = element("child");

    assertThat(logger.unableToAdoptElement(child)).hasMessageContaining("child");
    assertThat(logger.elementHasNoParent(child)).hasMessageContaining("child");
    assertThat(logger.elementIsNotChildOfThisElement(child, parent))
      .hasMessageContaining("child").hasMessageContaining("parent");
    assertThat(logger.unableToReplaceElementInImplementation(child, parent, cause)).hasCause(cause);
    assertThat(logger.unableToSetAttributeInImplementation(parent, "urn:x", "attr", "v", cause))
      .hasMessageContaining("attr").hasCause(cause);
    assertThat(logger.unableToAppendElementInImplementation(parent, child, cause)).hasCause(cause);
    assertThat(logger.unableToInsertElementInImplementation(parent, child, cause)).hasCause(cause);
    assertThat(logger.unableToRemoveChildInImplementation(parent, child, cause)).hasCause(cause);
    assertThat(logger.unableToCreateTransformer(cause)).hasCause(cause);
    assertThat(logger.unableToTransformElement(mock(Node.class), cause)).hasCause(cause);
  }

  @Test
  public void shouldCreateIteratorAndXPathExceptions() {
    assertThat(logger.methodNotSupportedByClass("remove", String.class))
      .hasMessageContaining("remove").hasMessageContaining("java.lang.String");
    assertThat(logger.iteratorHasNoMoreElements(String.class)).hasMessageContaining("java.lang.String");
    assertThat(logger.unableToEvaluateXPathExpressionOnElement(element("e"), cause)).hasCause(cause);
    assertThat(logger.unableToCastXPathResultTo(String.class, cause))
      .hasMessageContaining("java.lang.String").hasCause(cause);
    assertThat(logger.notAllowedXPathExpression("/")).hasMessageContaining("/");
    assertThat(logger.unableToFindXPathExpression("//x")).hasMessageContaining("//x");
  }
}
