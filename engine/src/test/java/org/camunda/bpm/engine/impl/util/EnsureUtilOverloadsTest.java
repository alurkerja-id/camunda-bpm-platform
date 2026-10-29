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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.exception.NotValidException;
import org.camunda.bpm.engine.exception.NullValueException;
import org.junit.Test;

/** Exercises the exception-class and message overloads that delegate to the core checks. */
public class EnsureUtilOverloadsTest {

  /** Has no String constructor, so the exception factory cannot instantiate it. */
  public static class UninstantiableException extends ProcessEngineException {
    private static final long serialVersionUID = 1L;

    public UninstantiableException() {
      super("never used");
    }
  }

  @Test
  public void shouldEnsureTrueAndFalse() {
    EnsureUtil.ensureTrue("ok", true);
    EnsureUtil.ensureFalse("ok", false);

    assertThatThrownBy(() -> EnsureUtil.ensureTrue("must hold", false))
        .isExactlyInstanceOf(ProcessEngineException.class).hasMessage("must hold");
    assertThatThrownBy(() -> EnsureUtil.ensureFalse("must not hold", true))
        .isExactlyInstanceOf(ProcessEngineException.class).hasMessage("must not hold");
  }

  @Test
  public void shouldEnsureEmptyCollectionAndNameFirstElementWhenNot() {
    EnsureUtil.ensureEmpty(NotValidException.class, "msg", null);
    EnsureUtil.ensureEmpty(NotValidException.class, "msg", Collections.emptyList());

    assertThatThrownBy(() -> EnsureUtil.ensureEmpty(NotValidException.class, "msg", Arrays.asList("first", "second")))
        .isExactlyInstanceOf(NotValidException.class)
        .hasMessage("msg: first is not empty");
  }

  @Test
  public void shouldEnsureInstanceOfThroughEveryOverload() {
    EnsureUtil.ensureInstanceOf(NotValidException.class, "value", "text", CharSequence.class);

    assertThatThrownBy(() -> EnsureUtil.ensureInstanceOf(NotValidException.class, "value", 1, String.class))
        .isExactlyInstanceOf(NotValidException.class)
        .hasMessageContaining("value has class java.lang.Integer and not java.lang.String");
    assertThatThrownBy(() -> EnsureUtil.ensureInstanceOf(NotValidException.class, "value", null, String.class))
        .isExactlyInstanceOf(NotValidException.class);
  }

  @Test
  public void shouldEnsureNotContainsEmptyStringThroughEveryOverload() {
    EnsureUtil.ensureNotContainsEmptyString("values", Arrays.asList("a", "b"));

    assertThatThrownBy(() -> EnsureUtil.ensureNotContainsEmptyString("values", Arrays.asList("a", "")))
        .isExactlyInstanceOf(NotValidException.class).hasMessage("values contains empty string");
    assertThatThrownBy(() -> EnsureUtil.ensureNotContainsEmptyString(ProcessEngineException.class, "values", Arrays.asList("")))
        .isExactlyInstanceOf(ProcessEngineException.class).hasMessage("values contains empty string");
    assertThatThrownBy(() -> EnsureUtil.ensureNotContainsEmptyString("values", null))
        .isExactlyInstanceOf(NotValidException.class);
  }

  @Test
  public void shouldEnsureNotContainsNullThroughEveryOverload() {
    EnsureUtil.ensureNotContainsNull("values", Arrays.asList("a", "b"));

    assertThatThrownBy(() -> EnsureUtil.ensureNotContainsNull("values", Arrays.asList("a", null)))
        .isExactlyInstanceOf(NullValueException.class);
    assertThatThrownBy(() -> EnsureUtil.ensureNotContainsNull(ProcessEngineException.class, "values", Arrays.asList((String) null)))
        .isExactlyInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldEnsureNumberOfElementsThroughEveryOverload() {
    EnsureUtil.ensureNumberOfElements("values", Arrays.asList(1, 2), 2);
    EnsureUtil.ensureNumberOfElements(NotValidException.class, "values", Arrays.asList(1, 2), 2);

    assertThatThrownBy(() -> EnsureUtil.ensureNumberOfElements("values", Arrays.asList(1), 2))
        .isExactlyInstanceOf(ProcessEngineException.class).hasMessage("values does not have 2 elements");
    assertThatThrownBy(() -> EnsureUtil.ensureNumberOfElements(NotValidException.class, "values", Arrays.asList(1), 2))
        .isExactlyInstanceOf(NotValidException.class).hasMessage("values does not have 2 elements");
    assertThatThrownBy(() -> EnsureUtil.ensureNumberOfElements("values", null, 2))
        .isExactlyInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldFailLoudlyWhenTheExceptionClassCannotBeInstantiated() {
    assertThatThrownBy(() -> EnsureUtil.ensureNotNull(UninstantiableException.class, "msg", "name", null))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-")
        .hasMessageContaining(UninstantiableException.class.getName());
  }

  @Test
  public void shouldRequireAnActiveCommandContext() {
    assertThatThrownBy(() -> EnsureUtil.ensureActiveCommandContext("someOperation"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ENGINE-")
        .hasMessageContaining("someOperation");
  }
}
