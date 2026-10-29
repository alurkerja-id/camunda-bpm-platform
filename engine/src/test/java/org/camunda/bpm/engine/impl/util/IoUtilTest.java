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
package org.camunda.bpm.engine.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.ProcessEngineException;
import org.junit.Test;

public class IoUtilTest {

  @Test
  public void shouldReadWholeInputStream() {
    byte[] data = new byte[40_000];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) i;
    }

    byte[] result = IoUtil.readInputStream(new ByteArrayInputStream(data), "big");

    assertThat(result).isEqualTo(data);
  }

  @Test
  public void shouldWrapReadFailureWithStreamName() {
    InputStream failing = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("disk gone");
      }
    };

    assertThatThrownBy(() -> IoUtil.readInputStream(failing, "my-stream"))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("my-stream")
      .hasMessageContaining("disk gone");
  }

  @Test
  public void shouldReadClasspathResourceAsUtf8String() {
    String content = IoUtil.readClasspathResourceAsString("org/camunda/bpm/engine/impl/util/ioutil-sample.txt");

    assertThat(content).isEqualTo("héllo ioutil\n");
  }

  @Test
  public void shouldFailForMissingClasspathResource() {
    assertThatThrownBy(() -> IoUtil.readClasspathResourceAsString("does/not/exist.txt"))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("does/not/exist.txt not found");
  }

  @Test
  public void shouldResolveFileFromClasspath() {
    assertThat(IoUtil.getFile("org/camunda/bpm/engine/impl/util/ioutil-sample.txt"))
      .exists()
      .hasName("ioutil-sample.txt");
  }

  @Test
  public void shouldFailToGetMissingFile() {
    assertThatThrownBy(() -> IoUtil.getFile("does/not/exist.txt"))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("does/not/exist.txt");
  }

  @Test
  public void shouldFailToWriteToMissingFile() {
    assertThatThrownBy(() -> IoUtil.writeStringToFile("x", "does/not/exist.txt"))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("does/not/exist.txt");
  }

  @Test
  public void shouldWriteStringToExistingClasspathFile() throws Exception {
    String path = "org/camunda/bpm/engine/impl/util/ioutil-writable.txt";

    IoUtil.writeStringToFile("written", path);

    assertThat(IoUtil.getFile(path)).hasContent("written");
  }

  @Test
  public void shouldCloseSilently() {
    AtomicInteger closed = new AtomicInteger();
    Closeable ok = closed::incrementAndGet;
    Closeable failing = () -> {
      closed.incrementAndGet();
      throw new IOException("close failed");
    };

    IoUtil.closeSilently(null);
    IoUtil.closeSilently(ok);
    IoUtil.closeSilently(failing);

    assertThat(closed).hasValue(2);
  }

  @Test
  public void shouldFlushSilently() {
    AtomicInteger flushed = new AtomicInteger();
    Flushable ok = flushed::incrementAndGet;
    Flushable failing = () -> {
      flushed.incrementAndGet();
      throw new IOException("flush failed");
    };

    IoUtil.flushSilently(null);
    IoUtil.flushSilently(ok);
    IoUtil.flushSilently(failing);

    assertThat(flushed).hasValue(2);
  }
}
