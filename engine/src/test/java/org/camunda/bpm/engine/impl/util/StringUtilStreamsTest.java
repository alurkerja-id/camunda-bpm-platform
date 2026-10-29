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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.runtime.ProcessElementInstance;
import org.junit.After;
import org.junit.Test;

public class StringUtilStreamsTest {

  @After
  public void tearDown() {
    if (Context.getCommandContext() != null) {
      Context.removeCommandContext();
    }
    if (Context.getProcessEngineConfiguration() != null) {
      Context.removeProcessEngineConfiguration();
    }
  }

  protected void enterCommandContext() {
    ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
    when(configuration.getDefaultCharset()).thenReturn(StandardCharsets.UTF_16BE);
    Context.setProcessEngineConfiguration(configuration);
    Context.setCommandContext(mock(CommandContext.class));
  }

  @Test
  public void shouldReadBytesInTheEngineCharset() throws IOException {
    enterCommandContext();
    byte[] bytes = "héllo".getBytes(StandardCharsets.UTF_16BE);

    try (Reader reader = StringUtil.readerFromBytes(bytes)) {
      char[] buffer = new char[16];
      int read = reader.read(buffer);
      assertThat(new String(buffer, 0, read)).isEqualTo("héllo");
    }
  }

  @Test
  public void shouldWriteToStreamInTheEngineCharset() throws IOException {
    enterCommandContext();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    try (Writer writer = StringUtil.writerForStream(out)) {
      writer.write("héllo");
    }

    assertThat(out.toByteArray()).isEqualTo("héllo".getBytes(StandardCharsets.UTF_16BE));
  }

  @Test
  public void shouldRefuseReaderAndWriterOutsideACommandContext() {
    assertThatThrownBy(() -> StringUtil.readerFromBytes(new byte[0])).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> StringUtil.writerForStream(new ByteArrayOutputStream())).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldJoinProcessElementInstanceIds() {
    ProcessElementInstance first = mock(ProcessElementInstance.class);
    ProcessElementInstance second = mock(ProcessElementInstance.class);
    when(first.getId()).thenReturn("a");
    when(second.getId()).thenReturn("b");

    assertThat(StringUtil.joinProcessElementInstanceIds(Arrays.asList(first, second))).isEqualTo("a, b");
    assertThat(StringUtil.joinProcessElementInstanceIds(new ArrayList<ProcessElementInstance>())).isEmpty();
  }

  @Test
  public void shouldDelegateRemoveToTheWrappedIterator() {
    List<String> source = new ArrayList<>(Arrays.asList("x", "y"));
    Iterator<String> iterator = new StringUtil.StringIterator<String>(source.iterator()) {
      @Override
      public String next() {
        return iterator.next();
      }
    };

    assertThat(iterator.next()).isEqualTo("x");
    iterator.remove();

    assertThat(source).containsExactly("y");
    assertThat(iterator.hasNext()).isTrue();
  }

  @Test
  public void shouldDetectText() {
    assertThat(StringUtil.hasText("a")).isTrue();
    assertThat(StringUtil.hasText("")).isFalse();
    assertThat(StringUtil.hasText(null)).isFalse();
  }
}
