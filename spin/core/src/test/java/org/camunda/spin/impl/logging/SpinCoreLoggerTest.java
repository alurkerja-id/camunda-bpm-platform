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
package org.camunda.spin.impl.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collections;

import org.camunda.spin.SpinFileNotFoundException;
import org.camunda.spin.SpinRuntimeException;
import org.camunda.spin.SpinScriptException;
import org.camunda.spin.spi.CustomDataFormatProvider;
import org.camunda.spin.spi.ExampleCustomDataFormat;
import org.camunda.spin.spi.ExampleCustomDataFormatConfigurator;
import org.camunda.spin.spi.SpinDataFormatException;
import org.junit.Test;

public class SpinCoreLoggerTest {

  private final SpinCoreLogger logger = SpinLogger.CORE_LOGGER;

  @Test
  public void shouldCreateUnsupportedInputException() {
    IllegalArgumentException e = logger.unsupportedInputParameter(String.class);

    assertThat(e).hasMessageContaining("001").hasMessageContaining("Unsupported input of type 'java.lang.String'");
  }

  @Test
  public void shouldCreateFileNotFoundExceptions() {
    IOException cause = new IOException("x");

    SpinFileNotFoundException withCause = logger.fileNotFoundException("a.txt", cause);
    SpinFileNotFoundException withoutCause = logger.fileNotFoundException("b.txt");

    assertThat(withCause).hasMessageContaining("002").hasMessageContaining("'a.txt'").hasCause(cause);
    assertThat(withoutCause).hasMessageContaining("'b.txt'").hasNoCause();
  }

  @Test
  public void shouldCreateReaderAndFormatExceptions() {
    IOException cause = new IOException("x");

    SpinRuntimeException unreadable = logger.unableToReadFromReader(cause);
    SpinDataFormatException unrecognized = logger.unrecognizableDataFormatException();
    SpinDataFormatException multiple = logger.multipleProvidersForDataformat("fmt");

    assertThat(unreadable).hasMessageContaining("003").hasCause(cause);
    assertThat(unrecognized).hasMessageContaining("004").hasMessageContaining("No matching data format detected");
    assertThat(multiple).hasMessageContaining("008").hasMessageContaining("'fmt'");
  }

  @Test
  public void shouldCreateScriptAndRewindExceptions() {
    SpinScriptException script = logger.noScriptEnvFoundForLanguage("groovy", "path/x");
    IOException rewind = logger.unableToRewindReader();

    assertThat(script).hasMessageContaining("006").hasMessageContaining("'groovy'").hasMessageContaining("'path/x'");
    assertThat(rewind).hasMessageContaining("007").hasMessageContaining("rewind buffering limit exceeded");
  }

  @Test
  public void shouldCreateClassNotFoundException() {
    ClassNotFoundException cause = new ClassNotFoundException("com.x.Y");

    SpinDataFormatException e = logger.classNotFound("com.x.Y", cause);

    assertThat(e).hasMessageContaining("012").hasMessageContaining("com.x.Y").hasCause(cause);
  }

  @Test
  public void shouldLogDiscoveryWithoutFailing() {
    // exercises all logging paths; the logger must tolerate any enabled level
    logger.logDataFormats(Collections.singletonList(new ExampleCustomDataFormat("fmt")));
    logger.logDataFormatProvider(new CustomDataFormatProvider());
    logger.logDataFormatConfigurator(new ExampleCustomDataFormatConfigurator());
    logger.tryLoadingClass("com.x.Y", getClass().getClassLoader());
  }
}
