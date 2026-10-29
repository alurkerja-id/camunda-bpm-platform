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

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;

import org.camunda.bpm.engine.ClassLoadingException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.ProcessEngineLogger;
import org.camunda.bpm.engine.impl.bpmn.parser.FieldDeclaration;
import org.camunda.bpm.engine.impl.util.io.StringStreamSource;
import org.junit.Test;

public class EngineUtilLoggerTest {

  private final EngineUtilLogger log = ProcessEngineLogger.UTIL_LOGGER;
  private final Exception cause = new IllegalStateException("boom");

  public String target;

  @Test
  public void shouldCreateExceptionsWithCodeAndCause() throws Exception {
    URISyntaxException uriException = new URISyntaxException("x", "bad");
    Field field = EngineUtilLoggerTest.class.getField("target");

    assertException(log.malformedUrlException("http://x", cause), "001", "http://x", cause);
    assertException(log.parsingFailureException("proc.bpmn", cause), "003", "proc.bpmn", cause);
    assertException(log.exceptionWhileInstantiatingClass("a.B", cause), "008", "a.B", cause);
    assertException(log.exceptionWhileApplyingFieldDeclatation("decl", "a.B", cause), "009", "decl", cause);
    assertException(log.exceptionWhileReadingStream("stream", cause), "011", "stream", cause);
    assertException(log.exceptionWhileReadingFile("/f", cause), "012", "/f", cause);
    assertException(log.exceptionWhileGettingFile("/g", cause), "013", "/g", cause);
    assertException(log.exceptionWhileWritingToFile("/w", cause), "014", "/w", cause);
    assertException(log.cannotConvertUrlToUri(new URL("http://localhost/x"), uriException), "018", "localhost", uriException);
    assertException(log.exceptionWhileInvokingMethod("doIt", "tgt", cause), "019", "doIt", cause);
    assertException(log.exceptionWhileSettingField(field, this, "v", cause), "022", "target", cause);
    assertException(log.exceptionWhileParsingCycleExpresison("R3/PT1H", cause), "026", "R3/PT1H", cause);
    assertException(log.exceptionWhileResolvingDuedate("PT1H", cause), "027", "PT1H", cause);
    assertException(log.exceptionWhileSettingXxeProcessing(cause), "047", "boom", cause);
  }

  @Test
  public void shouldCreateExceptionsWithoutCause() throws Exception {
    Field field = EngineUtilLoggerTest.class.getField("target");

    assertThat(log.multipleSourcesException(new StringStreamSource("s1"), new StringStreamSource("s2")).getMessage())
      .contains("002", "Multiple sources");
    assertThat(log.invalidBitNumber(9).getMessage()).contains("007", "9");
    assertThat(log.unableToAccessField(field, "a.B").getMessage()).contains("020", "a.B");
    assertThat(log.unableToAccessMethod("m", "a.B").getMessage()).contains("021", "m", "a.B");
    assertThat(log.ambiguousSetterMethod("setX", "a.B").getMessage()).contains("023", "setX");
    assertThat(log.cannotFindResource("/res").getMessage()).contains("024", "/res");
    assertThat(log.notInsideCommandContext("op").getMessage()).contains("025", "op");
    assertThat(log.cannotParseDuration("xx").getMessage()).contains("028", "xx");
    assertThat(log.exceptionDuringFormParsing("why", "f.form").getMessage()).contains("033", "why", "f.form");
  }

  @Test
  public void shouldCreateIncompatibleFieldTypeException() throws Exception {
    Field field = EngineUtilLoggerTest.class.getField("target");
    FieldDeclaration declaration = new FieldDeclaration("target", String.class.getName(), Integer.valueOf(1));

    ProcessEngineException e = log.incompatibleTypeForFieldDeclaration(declaration, this, field);

    assertThat(e.getMessage()).contains("010", "target", Integer.class.getName(), String.class.getName());
  }

  @Test
  public void shouldCreateClassLoadingException() {
    ClassLoadingException e = log.classLoadingException("a.B", cause);

    assertThat(e.getMessage()).contains("017", "a.B", "boom");
    assertThat(e.getClassName()).isEqualTo("a.B");
    assertThat(e.getCause()).isSameAs(cause);
  }

  @Test
  public void shouldCreateParseException() {
    ProcessEngineException e = log.exceptionDuringParsing("errs", "res.bpmn", Collections.emptyList(), Collections.emptyList());

    assertThat(e.getMessage()).contains("005", "errs");
  }

  @Test
  public void shouldLogWithoutThrowing() {
    log.logParseWarnings("warn");
    log.unableToSetSchemaResource(cause);
    log.debugCloseException(new IOException("io"));
    log.debugClassLoading("a.B", "desc", getClass().getClassLoader());
    log.logParsingRetryIntervals("R/PT1M", cause);
    log.logJsonException(cause);
    log.logAccessExternalSchemaNotSupported(cause);
    log.logMissingPropertiesFile("camunda.properties");
    log.debugCouldNotResolveCallableElement("def", "act", cause);
  }

  private void assertException(ProcessEngineException e, String code, String fragment, Throwable expectedCause) {
    assertThat(e.getMessage()).contains(code, fragment);
    assertThat(e.getCause()).isSameAs(expectedCause);
  }
}
