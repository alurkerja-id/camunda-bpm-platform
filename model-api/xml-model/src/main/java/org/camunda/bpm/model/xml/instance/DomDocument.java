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
package org.camunda.bpm.model.xml.instance;

import java.util.List;

import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

/**
 * Encapsulates a {@link Document}. Implementations of this interface must be thread-safe.
 *
 * @author Sebastian Menski
 */
public interface DomDocument {

  /**
   * Returns the root element of the document.
   *
   * @return the root element or null if non exists
   */
  DomElement getRootElement();

  /**
   * Sets the root element of the DOM document. Replace an existing if necessary.
   *
   * @param rootElement  the new root element
   */
  void setRootElement(DomElement rootElement);

  /**
   * Creates a new element in the dom document.
   *
   * @param namespaceUri  the namespaceUri of the new element
   * @param localName  the localName of the new element
   * @return the new DOM element
   */
  DomElement createElement(String namespaceUri, String localName);

  /**
   * Gets an element by its id.
   *
   * @param id  the id to search for
   * @return the element or null if no such element exists
   */
  DomElement getElementById(String id);

  /**
   * Gets all elements with the namespace and name.
   *
   * @param namespaceUri  the element namespaceURI to search for
   * @param localName  the element name to search for
   * @return the list of matching elements
   */
  List<DomElement> getElementsByNameNs(String namespaceUri, String localName);

  /**
   * Returns a new {@link DOMSource} of the document.
   *
   * Note that a {@link DOMSource} wraps the underlying {@link Document} which is
   * not thread-safe. Multiple DOMSources of the same document should be synchronized
   * by the calling application.
   *
   * @return the new {@link DOMSource}
   */
  DOMSource getDomSource();

  /**
   * Returns the object guarding this document's DOM tree. Synchronize on it around any work that
   * touches the DOM directly, a {@link DOMSource} in particular.
   *
   * <p>Every method in this package that reads or writes the tree locks the underlying
   * {@link Document}. Callers outside it could only lock this wrapper, which is a different
   * monitor, so their synchronized blocks excluded nothing. This hands them the right one.
   *
   * @return the monitor guarding the underlying document
   */
  default Object getDomLock() {
    return this;
  }

  /**
   * Registers a new namespace with a generic prefix.
   *
   * @param namespaceUri  the namespaceUri of the new namespace
   * @return the used prefix
   */
  String registerNamespace(String namespaceUri);

  /**
   * Registers a new namespace for the prefix.
   *
   * @param prefix  the prefix of the new namespace
   * @param namespaceUri  the namespaceUri of the new namespace
   */
  void registerNamespace(String prefix, String namespaceUri);

  /**
   * Clones the DOM document.
   *
   * @return the cloned DOM document
   */
  DomDocument clone();

}
