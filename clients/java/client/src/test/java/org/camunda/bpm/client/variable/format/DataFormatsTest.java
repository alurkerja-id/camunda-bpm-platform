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
package org.camunda.bpm.client.variable.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;

import org.camunda.bpm.client.exception.DataFormatException;
import org.camunda.bpm.client.exception.ExternalTaskClientException;
import org.camunda.bpm.client.variable.impl.format.json.JacksonJsonDataFormat;
import org.camunda.bpm.client.variable.impl.format.serializable.SerializableDataFormat;
import org.camunda.bpm.client.variable.impl.format.xml.DomXmlDataFormat;
import org.junit.Test;
import org.w3c.dom.Element;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DataFormatsTest {

  public static class Person implements Serializable {
    private static final long serialVersionUID = 1L;
    public String name;
    public int age;
    public Person() { }
    public Person(String name, int age) {
      this.name = name;
      this.age = age;
    }
  }

  // ---- JSON ----

  @Test
  public void shouldRoundTripJsonByClassAndTypeName() {
    JacksonJsonDataFormat format = new JacksonJsonDataFormat("application/json");

    String json = format.writeValue(new Person("ann", 30));
    Person byClass = format.readValue(json, Person.class);
    Person byName = format.readValue(json, Person.class.getName());

    assertThat(format.getName()).isEqualTo("application/json");
    assertThat(json).contains("\"name\":\"ann\"");
    assertThat(byClass.name).isEqualTo("ann");
    assertThat(byName.age).isEqualTo(30);
  }

  @Test
  public void shouldReadJsonByCanonicalGenericTypeName() {
    JacksonJsonDataFormat format = new JacksonJsonDataFormat("application/json");
    List<Person> people = new ArrayList<>(Arrays.asList(new Person("a", 1), new Person("b", 2)));

    String typeName = format.getCanonicalTypeName(people);
    List<Person> result = format.readValue(format.writeValue(people), typeName);

    assertThat(typeName).contains("java.util.ArrayList").contains(Person.class.getName());
    assertThat(result).hasSize(2);
    assertThat(result.get(1)).isInstanceOf(Person.class);
    assertThat(format.getCanonicalTypeName(new Person())).isEqualTo(Person.class.getName());
    assertThat(format.getCanonicalTypeName(new ArrayList<String>())).contains("java.util.ArrayList");
  }

  @Test
  public void shouldHandleJsonConfigurationAndErrors() {
    JacksonJsonDataFormat format = new JacksonJsonDataFormat("application/json");
    ObjectMapper custom = new ObjectMapper();
    format.setObjectMapper(custom);

    assertThat(format.getObjectMapper()).isSameAs(custom);
    assertThat(format.canMap("x")).isTrue();
    assertThat(format.canMap(null)).isFalse();
    assertThatThrownBy(() -> format.readValue("not json", Person.class)).isInstanceOf(DataFormatException.class);
    assertThatThrownBy(() -> format.readValue("{\"unknown\":1}", Person.class)).isInstanceOf(DataFormatException.class);
    assertThatThrownBy(() -> format.readValue("{}", "not.a.Class<<")).isInstanceOf(DataFormatException.class);
    assertThatThrownBy(() -> format.getCanonicalTypeName(null)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> format.writeValue(new Object())).isInstanceOf(DataFormatException.class);
  }

  // ---- Serializable ----

  @Test
  public void shouldRoundTripSerializableValue() {
    SerializableDataFormat format = new SerializableDataFormat("application/x-java-serialized-object");

    String serialized = format.writeValue(new Person("bob", 5));
    Person byClass = format.readValue(serialized, Person.class);
    Person byName = format.readValue(serialized, Person.class.getName());

    assertThat(format.getName()).isEqualTo("application/x-java-serialized-object");
    assertThat(byClass.name).isEqualTo("bob");
    assertThat(byName.age).isEqualTo(5);
    assertThat(format.getCanonicalTypeName(byName)).isEqualTo(Person.class.getName());
  }

  @Test
  public void shouldDecideWhatSerializableFormatCanMapAndFailOnBadInput() {
    SerializableDataFormat format = new SerializableDataFormat("serializable");

    assertThat(format.canMap("text")).isTrue();
    assertThat(format.canMap(new Object())).isFalse();
    assertThatThrownBy(() -> format.writeValue(new Object())).isInstanceOf(DataFormatException.class);
    assertThatThrownBy(() -> format.readValue("AAAA", String.class)).isInstanceOf(DataFormatException.class);
  }

  // ---- XML ----

  @jakarta.xml.bind.annotation.XmlRootElement
  public static class Rooted {
    public String value;
  }

  @Test
  public void shouldWriteAndReadXmlForRootedAndPlainClasses() {
    DomXmlDataFormat format = new DomXmlDataFormat("application/xml");
    Rooted rooted = new Rooted();
    rooted.value = "v";

    String rootedXml = format.writeValue(rooted);
    String plainXml = format.writeValue(new Person("cy", 7));

    assertThat(rootedXml).contains("<rooted>").contains("<value>v</value>");
    assertThat(plainXml).contains("<person>").contains("<name>cy</name>");
    assertThat(format.<Rooted>readValue(rootedXml, Rooted.class).value).isEqualTo("v");
    assertThat(format.<Person>readValue(plainXml, Person.class.getName()).age).isEqualTo(7);
    assertThat(format.getCanonicalTypeName(rooted)).isEqualTo(Rooted.class.getName());
  }

  @Test
  public void shouldExposeXmlFactoriesAndParseElements() {
    DocumentBuilderFactory builderFactory = DomXmlDataFormat.defaultDocumentBuilderFactory();
    TransformerFactory transformerFactory = DomXmlDataFormat.defaultTransformerFactory();
    DomXmlDataFormat format = new DomXmlDataFormat("xml", builderFactory, transformerFactory);

    Element element = format.readAsElement("<a><b>1</b></a>");

    assertThat(element.getTagName()).isEqualTo("a");
    assertThat(format.getName()).isEqualTo("xml");
    assertThat(format.getDocumentBuilderFactory()).isSameAs(builderFactory);
    assertThat(format.getTransformerFactory()).isSameAs(transformerFactory);
    assertThat(builderFactory.isNamespaceAware()).isTrue();
    assertThat(builderFactory.isValidating()).isFalse();
    assertThat(builderFactory.isIgnoringComments()).isTrue();
    assertThat(new DomXmlDataFormat("n").getDocumentBuilderFactory()).isNotNull();
    assertThat(new DomXmlDataFormat("n", builderFactory).getTransformerFactory()).isNotNull();
    assertThat(format.canMap("x")).isTrue();
    assertThat(format.canMap(null)).isFalse();
  }

  @Test
  public void shouldFailOnInvalidXmlOrArguments() {
    DomXmlDataFormat format = new DomXmlDataFormat("xml");

    assertThatThrownBy(() -> format.readAsElement("<unclosed>")).isInstanceOf(DataFormatException.class);
    assertThatThrownBy(() -> format.readValue("<a/>", Person.class.getName() + "Missing"))
        .isInstanceOf(DataFormatException.class);
    assertThatThrownBy(() -> format.readValue(null, Person.class)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> format.readValue("<a/>", (String) null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> format.writeValue(null)).isInstanceOf(IllegalArgumentException.class);
  }

}
