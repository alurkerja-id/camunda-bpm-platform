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
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.junit.Test;

/**
 * Covers the argument guards in {@link EnsureUtil}.
 *
 * <p>Every guard comes in two shapes: one that raises a {@link ProcessEngineException} and one that
 * raises whichever exception class the caller names - {@link BadUserRequestException} where the
 * fault is the caller's. Both are driven, since picking the wrong one turns a user mistake into
 * what looks like an engine failure.
 *
 * <p>A guard that passes simply returns, so the accepting cases are plain calls: the test fails by
 * throwing if a guard rejects something it should let through.
 */
public class EnsureUtilTest {

  // not null ------------------------------------------------------------------------------------

  @Test
  public void shouldLetANonNullValueThrough() {
    EnsureUtil.ensureNotNull("value", "something");
    EnsureUtil.ensureNotNull("value", new Object[] { "a", "b" });
  }

  @Test
  public void shouldRefuseNull() {
    assertThatThrownBy(() -> EnsureUtil.ensureNotNull("value", (Object) null))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("value is null");
    assertThatThrownBy(() -> EnsureUtil.ensureNotNull("custom message", "value", null))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("custom message");
  }

  @Test
  public void shouldRefuseNullWithTheRequestedExceptionClass() {
    assertThatThrownBy(() ->
        EnsureUtil.ensureNotNull(BadUserRequestException.class, "value", (Object) null))
        .isInstanceOf(BadUserRequestException.class);
    assertThatThrownBy(() ->
        EnsureUtil.ensureNotNull(BadUserRequestException.class, "msg", "value", null))
        .isInstanceOf(BadUserRequestException.class);
  }

  @Test
  public void shouldRefuseWhenAnyOfSeveralValuesIsNull() {
    assertThatThrownBy(() -> EnsureUtil.ensureNotNull("value", "a", null, "c"))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() ->
        EnsureUtil.ensureNotNull(BadUserRequestException.class, "value", "a", null))
        .isInstanceOf(BadUserRequestException.class);
  }

  // not empty ------------------------------------------------------------------------------------

  @Test
  public void shouldRefuseAnEmptyString() {
    EnsureUtil.ensureNotEmpty("value", "text");

    assertThatThrownBy(() -> EnsureUtil.ensureNotEmpty("value", ""))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() -> EnsureUtil.ensureNotEmpty("value", (String) null))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() ->
        EnsureUtil.ensureNotEmpty(BadUserRequestException.class, "value", ""))
        .isInstanceOf(BadUserRequestException.class);
  }

  @Test
  public void shouldRefuseAnEmptyCollection() {
    EnsureUtil.ensureNotEmpty("value", Arrays.asList("a"));

    assertThatThrownBy(() -> EnsureUtil.ensureNotEmpty("value", Collections.emptyList()))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() ->
        EnsureUtil.ensureNotEmpty(BadUserRequestException.class, "value",
            Collections.emptyList())).isInstanceOf(BadUserRequestException.class);
  }

  @Test
  public void shouldRefuseAnEmptyMap() {
    Map<String, String> filled = new HashMap<>();
    filled.put("k", "v");
    EnsureUtil.ensureNotEmpty("value", filled);

    assertThatThrownBy(() -> EnsureUtil.ensureNotEmpty("value", new HashMap<>()))
        .isInstanceOf(ProcessEngineException.class);
  }

  // numbers ---------------------------------------------------------------------------------------

  @Test
  public void shouldRefuseANonPositiveNumber() {
    EnsureUtil.ensurePositive("value", 1L);

    assertThatThrownBy(() -> EnsureUtil.ensurePositive("value", 0L))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() -> EnsureUtil.ensurePositive("value", -1L))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() -> EnsureUtil.ensurePositive("value", (Long) null))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() ->
        EnsureUtil.ensurePositive(BadUserRequestException.class, "value", 0L))
        .isInstanceOf(BadUserRequestException.class);
  }

  @Test
  public void shouldCompareTwoNumbers() {
    EnsureUtil.ensureEquals("value", 1L, 1L);
    EnsureUtil.ensureLessThan("msg", "value", 1L, 2L);
    EnsureUtil.ensureGreaterThanOrEqual("value", 2L, 1L);
    EnsureUtil.ensureGreaterThanOrEqual("value", 1L, 1L);

    assertThatThrownBy(() -> EnsureUtil.ensureEquals("value", 1L, 2L))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() -> EnsureUtil.ensureLessThan("msg", "value", 2L, 1L))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() -> EnsureUtil.ensureGreaterThanOrEqual("value", 1L, 2L))
        .isInstanceOf(ProcessEngineException.class);
  }

  // types -------------------------------------------------------------------------------------------

  @Test
  public void shouldCheckTheTypeOfAValue() {
    EnsureUtil.ensureInstanceOf("value", "text", String.class);

    assertThatThrownBy(() -> EnsureUtil.ensureInstanceOf("value", "text", Integer.class))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("has class java.lang.String");
  }

  // combinations ------------------------------------------------------------------------------------------

  @Test
  public void shouldRequireExactlyOneValue() {
    EnsureUtil.ensureOnlyOneNotNull("msg", "a", null, null);

    assertThatThrownBy(() -> EnsureUtil.ensureOnlyOneNotNull("msg", "a", "b"))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() -> EnsureUtil.ensureOnlyOneNotNull("msg", null, null))
        .isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldRequireAtLeastOneValue() {
    EnsureUtil.ensureAtLeastOneNotNull("msg", null, "b");

    assertThatThrownBy(() -> EnsureUtil.ensureAtLeastOneNotNull("msg", null, null))
        .isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldRequireAtLeastOneNonEmptyString() {
    EnsureUtil.ensureAtLeastOneNotEmpty("msg", "", "b");

    assertThatThrownBy(() -> EnsureUtil.ensureAtLeastOneNotEmpty("msg", "", null))
        .isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldRefuseACollectionHoldingAnEmptyString() {
    EnsureUtil.ensureNotContainsEmptyString("value", Arrays.asList("a", "b"));

    assertThatThrownBy(() ->
        EnsureUtil.ensureNotContainsEmptyString("value", Arrays.asList("a", "")))
        .isInstanceOf(ProcessEngineException.class);
  }
}
