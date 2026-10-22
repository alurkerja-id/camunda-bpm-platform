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
package org.camunda.spin.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.regex.Pattern;

import org.camunda.spin.SpinRuntimeException;
import org.junit.Test;

public class TextBasedDataFormatReaderTest {

  protected TextBasedDataFormatReader reader = new TextBasedDataFormatReader() {
    @Override
    protected Pattern getInputDetectionPattern() {
      return Pattern.compile("^\\s*<");
    }

    @Override
    public Object readInput(Reader input) {
      return null;
    }
  };

  @Test
  public void shouldDetectMatchingInput() {
    assertThat(reader.canRead(new StringReader("  <root/>"), 10)).isTrue();
  }

  @Test
  public void shouldNotDetectNonMatchingInput() {
    assertThat(reader.canRead(new StringReader("{\"a\": 1}"), 10)).isFalse();
  }

  @Test
  public void shouldOnlyLookAtReadLimit() {
    // marker lies beyond the read limit -> not detected
    assertThat(reader.canRead(new StringReader("          <root/>"), 5)).isFalse();
  }

  @Test
  public void shouldWrapIoErrors() {
    Reader failing = new Reader() {
      @Override
      public int read(char[] cbuf, int off, int len) throws IOException {
        throw new IOException("boom");
      }

      @Override
      public void close() {
      }
    };

    assertThatThrownBy(() -> reader.canRead(failing, 10))
      .isInstanceOf(SpinRuntimeException.class)
      .hasMessageContaining("Unable to read from reader");
  }
}
