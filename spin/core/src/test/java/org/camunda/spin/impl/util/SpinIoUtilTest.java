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
package org.camunda.spin.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.camunda.spin.SpinFileNotFoundException;
import org.junit.Test;

public class SpinIoUtilTest {

  @Test
  public void shouldConvertOutputStreamToInputStream() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write("hello".getBytes(StandardCharsets.UTF_8));

    InputStream in = SpinIoUtil.convertOutputStreamToInputStream(out);

    assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
  }

  @Test
  public void shouldReadInputStreamTrimmed() throws IOException {
    InputStream in = new ByteArrayInputStream("  a  \n  b  \n".getBytes(StandardCharsets.UTF_8));

    assertThat(SpinIoUtil.getStringFromInputStream(in)).isEqualTo("ab");
  }

  @Test
  public void shouldReadInputStreamUntrimmed() throws IOException {
    InputStream in = new ByteArrayInputStream("  a  \nb\n".getBytes(StandardCharsets.UTF_8));

    assertThat(SpinIoUtil.getStringFromInputStream(in, false)).isEqualTo("  a  \nb\n");
  }

  @Test
  public void shouldReadReaderTrimmed() throws IOException {
    assertThat(SpinIoUtil.getStringFromReader(new StringReader("  a  \n  b  \n"))).isEqualTo("ab");
  }

  @Test
  public void shouldReadReaderUntrimmed() throws IOException {
    assertThat(SpinIoUtil.getStringFromReader(new StringReader("  a  \nb\n"), false)).isEqualTo("  a  \nb\n");
  }

  @Test
  public void shouldWrapStringAsReader() throws IOException {
    Reader reader = SpinIoUtil.stringAsReader("content");

    assertThat(SpinIoUtil.getStringFromReader(reader)).isEqualTo("content");
  }

  @Test
  public void shouldReadClasspathResourceAsReader() throws IOException {
    // log4j / logging config or any resource shipped with the test classpath
    Reader reader = SpinIoUtil.classpathResourceAsReader("org/camunda/spin/impl/util/sample.txt");

    assertThat(SpinIoUtil.getStringFromReader(reader)).isEqualTo("sample content");
  }

  @Test
  public void shouldFailForMissingClasspathResource() {
    assertThatThrownBy(() -> SpinIoUtil.classpathResourceAsReader("does/not/exist.txt"))
      .isInstanceOf(RuntimeException.class);
  }

  @Test
  public void shouldUseUtf8AsEncoding() {
    assertThat(SpinIoUtil.ENCODING_CHARSET).isEqualTo(StandardCharsets.UTF_8);
    assertThat(SpinFileNotFoundException.class).isNotNull();
  }
}
