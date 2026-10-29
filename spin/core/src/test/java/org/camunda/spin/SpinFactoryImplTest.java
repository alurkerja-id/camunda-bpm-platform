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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.camunda.spin.impl.SpinFactoryImpl;
import org.camunda.spin.json.SpinJsonNode;
import org.camunda.spin.spi.DataFormat;
import org.camunda.spin.spi.DataFormatMapper;
import org.camunda.spin.spi.DataFormatReader;
import org.camunda.spin.spi.DataFormatWriter;
import org.camunda.spin.spi.SpinDataFormatException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SpinFactoryImplTest {

  protected SpinFactoryImpl factory = new SpinFactoryImpl();
  protected Map<String, DataFormat<?>> originalFormats;
  protected StubDataFormat stub = new StubDataFormat("stub", 'X');

  @Before
  public void registerStubFormat() {
    originalFormats = DataFormats.INSTANCE.availableDataFormats;
    Map<String, DataFormat<?>> formats = new HashMap<>();
    formats.put(stub.getName(), stub);
    DataFormats.INSTANCE.availableDataFormats = formats;
  }

  @After
  public void restoreFormats() {
    DataFormats.INSTANCE.availableDataFormats = originalFormats;
  }

  @Test
  public void shouldRejectNullParameter() {
    assertThatThrownBy(() -> factory.createSpin(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.createSpin(null, stub)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.createSpin("X", (DataFormat<SpinJsonNode>) null))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.createSpin("X", (String) null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.createSpinFromString(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.createSpinFromReader(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.createSpinFromSpin(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.createSpinFromString(null, stub)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.createSpinFromReader(null, stub)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.createSpinFromSpin(null, stub)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.createSpinFromObject(null, stub)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectUnsupportedInputWithoutFormat() {
    assertThatThrownBy(() -> factory.createSpin(Integer.valueOf(1)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("java.lang.Integer");
  }

  @Test
  public void shouldReturnSameSpinInstance() {
    SpinJsonNode node = mock(SpinJsonNode.class);

    assertThat((Object) factory.createSpin(node)).isSameAs(node);
    assertThat((Object) factory.createSpin(node, stub)).isSameAs(node);
    assertThat((Object) factory.createSpinFromSpin(node)).isSameAs(node);
    assertThat((Object) factory.createSpinFromSpin(node, stub)).isSameAs(node);
  }

  @Test
  public void shouldDetectFormatFromString() {
    SpinJsonNode node = factory.createSpin("Xcontent");

    assertThat(node).isSameAs(stub.wrapper);
    assertThat(stub.lastInput).isEqualTo("Xcontent");
  }

  @Test
  public void shouldDetectFormatFromReader() {
    SpinJsonNode node = factory.createSpin(new StringReader("Xreader"));

    assertThat(node).isSameAs(stub.wrapper);
    assertThat(stub.lastInput).isEqualTo("Xreader");
  }

  @Test
  public void shouldFailWhenNoFormatMatches() {
    assertThatThrownBy(() -> factory.createSpin("Ynope"))
      .isInstanceOf(SpinDataFormatException.class)
      .hasMessageContaining("No matching data format detected");
  }

  @Test
  public void shouldCreateFromStringAndReaderWithExplicitFormat() {
    assertThat((Object) factory.createSpin("anything", stub)).isSameAs(stub.wrapper);
    assertThat(stub.lastInput).isEqualTo("anything");

    assertThat((Object) factory.createSpin(new StringReader("fromReader"), stub)).isSameAs(stub.wrapper);
    assertThat(stub.lastInput).isEqualTo("fromReader");
  }

  @Test
  public void shouldCreateFromObjectUsingMapper() {
    Object javaObject = new Object();

    assertThat((Object) factory.createSpin(javaObject, stub)).isSameAs(stub.wrapper);
    assertThat(stub.lastMapped).isSameAs(javaObject);
  }

  @Test
  public void shouldLookUpFormatByName() {
    assertThat((Object) factory.createSpin("content", "stub")).isSameAs(stub.wrapper);

    assertThatThrownBy(() -> factory.createSpin("content", "unknown"))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldFailWhenDetectionExceedsRewindBuffer() {
    // stub detection consumes only 1 char; a longer read in canRead exceeds the 256 char rewind buffer
    StubDataFormat greedy = new StubDataFormat("greedy", 'X') {
      @Override
      public DataFormatReader getReader() {
        return new DataFormatReader() {
          @Override
          public Object readInput(Reader input) {
            return null;
          }

          @Override
          public boolean canRead(Reader input, int readLimit) {
            try {
              char[] buffer = new char[readLimit * 2];
              input.read(buffer, 0, buffer.length);
              return false;
            } catch (IOException e) {
              throw new IllegalStateException(e);
            }
          }
        };
      }
    };
    Map<String, DataFormat<?>> formats = new HashMap<>();
    formats.put(greedy.getName(), greedy);
    DataFormats.INSTANCE.availableDataFormats = formats;

    String longInput = new String(new char[1000]).replace('\0', 'X');

    assertThatThrownBy(() -> factory.createSpin(longInput))
      .isInstanceOf(SpinRuntimeException.class)
      .hasMessageContaining("Unable to read from reader");
  }

  /** Format that recognizes inputs starting with a marker character. */
  static class StubDataFormat implements DataFormat<SpinJsonNode> {
    final String name;
    final char marker;
    final SpinJsonNode wrapper = mock(SpinJsonNode.class);
    Object lastInput;
    Object lastMapped;

    StubDataFormat(String name, char marker) {
      this.name = name;
      this.marker = marker;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Class<? extends SpinJsonNode> getWrapperType() {
      return SpinJsonNode.class;
    }

    @Override
    public SpinJsonNode createWrapperInstance(Object parameter) {
      lastInput = parameter;
      return wrapper;
    }

    @Override
    public DataFormatReader getReader() {
      return new DataFormatReader() {
        @Override
        public Object readInput(Reader input) {
          try {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = input.read()) != -1) {
              sb.append((char) c);
            }
            return sb.toString();
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }

        @Override
        public boolean canRead(Reader input, int readLimit) {
          try {
            return input.read() == marker;
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }
      };
    }

    @Override
    public DataFormatWriter getWriter() {
      return null;
    }

    @Override
    public DataFormatMapper getMapper() {
      DataFormatMapper mapper = mock(DataFormatMapper.class);
      when(mapper.mapJavaToInternal(any())).thenAnswer(invocation -> {
        lastMapped = invocation.getArgument(0);
        return lastMapped;
      });
      return mapper;
    }
  }
}
