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
package org.camunda.spin.xml;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.spin.SpinRuntimeException;
import org.camunda.spin.json.SpinJsonDataFormatException;
import org.camunda.spin.json.SpinJsonException;
import org.camunda.spin.json.SpinJsonPathException;
import org.camunda.spin.json.SpinJsonPropertyException;
import org.camunda.spin.spi.SpinDataFormatException;
import org.junit.Test;

public class SpinXmlExceptionsTest {

  private final Throwable cause = new IllegalStateException("cause");

  @Test
  public void shouldCarryMessageAndCauseForXmlExceptions() {
    SpinRuntimeException[] withMessage = {
      new SpinXmlNodeException("m"),
      new SpinXmlElementException("m"),
      new SpinXmlAttributeException("m"),
      new SpinXmlElementImplementationException("m"),
      new SpinXmlDataFormatException("m"),
      new SpinXPathException("m"),
    };
    for (SpinRuntimeException e : withMessage) {
      assertThat(e).hasMessage("m").hasNoCause();
    }

    SpinRuntimeException[] withCause = {
      new SpinXmlNodeException("m", cause),
      new SpinXmlElementException("m", cause),
      new SpinXmlAttributeException("m", cause),
      new SpinXmlElementImplementationException("m", cause),
      new SpinXmlDataFormatException("m", cause),
      new SpinXPathException("m", cause),
    };
    for (SpinRuntimeException e : withCause) {
      assertThat(e).hasMessage("m").hasCause(cause);
    }
  }

  @Test
  public void shouldCarryMessageAndCauseForJsonExceptions() {
    assertThat(new SpinJsonException("m")).hasMessage("m").hasNoCause();
    assertThat(new SpinJsonException("m", cause)).hasMessage("m").hasCause(cause);
    assertThat(new SpinJsonPathException("m")).hasMessage("m").hasNoCause();
    assertThat(new SpinJsonPathException("m", cause)).hasMessage("m").hasCause(cause);
    assertThat(new SpinJsonPropertyException("m")).hasMessage("m").hasNoCause();
    assertThat(new SpinJsonPropertyException("m", cause)).hasMessage("m").hasCause(cause);
    assertThat(new SpinJsonDataFormatException("m")).hasMessage("m").hasNoCause();
    assertThat(new SpinJsonDataFormatException("m", cause)).hasMessage("m").hasCause(cause);
  }

  @Test
  public void shouldKeepExceptionHierarchy() {
    assertThat(new SpinXmlElementImplementationException("m")).isInstanceOf(SpinXmlElementException.class);
    assertThat(new SpinXmlElementException("m")).isInstanceOf(SpinXmlNodeException.class);
    assertThat(new SpinXmlAttributeException("m")).isInstanceOf(SpinXmlNodeException.class);
    assertThat(new SpinXmlDataFormatException("m")).isInstanceOf(SpinDataFormatException.class);
  }
}
