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
package org.camunda.bpm.spring.boot.starter.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.junit.Test;

/**
 * Covers {@link CamundaSpringBootUtil}.
 *
 * <p>initCustomFields is twenty-six null checks in a row, so it is driven twice: once on a fresh
 * configuration, where every branch fills in an empty collection, and once on a configuration that
 * already has all of them, where every branch must leave what is there untouched.
 */
public class CamundaSpringBootUtilTest {

  // cast ----------------------------------------------------------------------------------------

  @Test
  public void shouldCastOnlyWhenTheTypeMatches() {
    assertThat(CamundaSpringBootUtil.cast("text", String.class)).contains("text");
    assertThat(CamundaSpringBootUtil.cast("text", Integer.class)).isEmpty();
    assertThat(CamundaSpringBootUtil.cast(null, String.class)).isEmpty();
  }

  @Test
  public void shouldCastProcessEngineToItsImplementation() {
    ProcessEngineImpl engine = mock(ProcessEngineImpl.class);

    assertThat(CamundaSpringBootUtil.processEngineImpl(engine)).contains(engine);
    assertThat(CamundaSpringBootUtil.processEngineImpl(mock(ProcessEngine.class))).isEmpty();
    assertThat(CamundaSpringBootUtil.processEngineImpl(null)).isEmpty();
  }

  @Test
  public void shouldCastConfigurationToTheSpringOne() {
    SpringProcessEngineConfiguration springConfiguration = new SpringProcessEngineConfiguration();
    ProcessEngineConfiguration plainConfiguration = new StandaloneProcessEngineConfiguration();

    assertThat(CamundaSpringBootUtil.springProcessEngineConfiguration(springConfiguration))
        .contains(springConfiguration);
    assertThat(CamundaSpringBootUtil.springProcessEngineConfiguration(plainConfiguration))
        .isEmpty();
    assertThat(CamundaSpringBootUtil.springProcessEngineConfiguration(null)).isEmpty();
  }

  @Test
  public void shouldReadTheSpringConfigurationOffAnEngine() {
    SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();
    ProcessEngine engine = mock(ProcessEngine.class);
    when(engine.getProcessEngineConfiguration()).thenReturn(configuration);

    assertThat(CamundaSpringBootUtil.get(engine)).isSameAs(configuration);
  }

  // join ----------------------------------------------------------------------------------------

  @Test
  public void shouldJoinBothSides() {
    assertThat(CamundaSpringBootUtil.join(Arrays.asList("a"), Arrays.asList("b")))
        .containsExactly("a", "b");
  }

  @Test
  public void shouldJoinWhenEitherSideIsNullOrEmpty() {
    assertThat(CamundaSpringBootUtil.join(null, Arrays.asList("b"))).containsExactly("b");
    assertThat(CamundaSpringBootUtil.join(Arrays.asList("a"), null)).containsExactly("a");
    assertThat(CamundaSpringBootUtil.join(Collections.emptyList(), Arrays.asList("b")))
        .containsExactly("b");
    assertThat(CamundaSpringBootUtil.join(Arrays.asList("a"), Collections.emptyList()))
        .containsExactly("a");
    assertThat(CamundaSpringBootUtil.join(null, null)).isEmpty();
  }

  @Test
  public void shouldReturnANewListRatherThanReuseAnInput() {
    List<String> existing = new ArrayList<>(Arrays.asList("a"));

    List<String> joined = CamundaSpringBootUtil.join(existing, null);
    joined.add("b");

    assertThat(existing).containsExactly("a");
  }

  // initCustomFields ------------------------------------------------------------------------------

  @Test
  public void shouldFillEveryCustomFieldOfAFreshConfiguration() {
    SpringProcessEngineConfiguration configuration =
        CamundaSpringBootUtil.initCustomFields(new SpringProcessEngineConfiguration());

    assertThat(configuration.getCustomPreCommandInterceptorsTxRequired()).isNotNull();
    assertThat(configuration.getCustomPostCommandInterceptorsTxRequired()).isNotNull();
    assertThat(configuration.getCustomPreCommandInterceptorsTxRequiresNew()).isNotNull();
    assertThat(configuration.getCustomPostCommandInterceptorsTxRequiresNew()).isNotNull();
    assertThat(configuration.getCustomSessionFactories()).isNotNull();
    assertThat(configuration.getCustomPreDeployers()).isNotNull();
    assertThat(configuration.getCustomPostDeployers()).isNotNull();
    assertThat(configuration.getCustomJobHandlers()).isNotNull();
    assertThat(configuration.getCustomIncidentHandlers()).isNotNull();
    assertThat(configuration.getCustomBatchJobHandlers()).isNotNull();
    assertThat(configuration.getCustomFormEngines()).isNotNull();
    assertThat(configuration.getCustomFormFieldValidators()).isNotNull();
    assertThat(configuration.getCustomFormTypes()).isNotNull();
    assertThat(configuration.getCustomPreVariableSerializers()).isNotNull();
    assertThat(configuration.getCustomPostVariableSerializers()).isNotNull();
    assertThat(configuration.getCustomHistoryLevels()).isNotNull();
    assertThat(configuration.getCustomPreCmmnTransformListeners()).isNotNull();
    assertThat(configuration.getCustomPostCmmnTransformListeners()).isNotNull();
    assertThat(configuration.getCustomPreBPMNParseListeners()).isNotNull();
    assertThat(configuration.getCustomPostBPMNParseListeners()).isNotNull();
    assertThat(configuration.getCustomEventHandlers()).isNotNull();
    assertThat(configuration.getCustomPreMigrationActivityValidators()).isNotNull();
    assertThat(configuration.getCustomPostMigrationActivityValidators()).isNotNull();
    assertThat(configuration.getCustomPreMigrationInstructionValidators()).isNotNull();
    assertThat(configuration.getCustomPostMigrationInstructionValidators()).isNotNull();
    assertThat(configuration.getCustomPreMigratingActivityInstanceValidators()).isNotNull();
    assertThat(configuration.getCustomPostMigratingActivityInstanceValidators()).isNotNull();
  }

  /**
   * The other half of every guard: a configuration that already carries its collections must come
   * back with exactly those instances, not fresh empty ones.
   */
  @Test
  public void shouldLeaveCustomFieldsThatAreAlreadySet() {
    SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();

    configuration.setCustomPreCommandInterceptorsTxRequired(new ArrayList<>());
    configuration.setCustomPostCommandInterceptorsTxRequired(new ArrayList<>());
    configuration.setCustomPreCommandInterceptorsTxRequiresNew(new ArrayList<>());
    configuration.setCustomPostCommandInterceptorsTxRequiresNew(new ArrayList<>());
    configuration.setCustomSessionFactories(new ArrayList<>());
    configuration.setCustomPreDeployers(new ArrayList<>());
    configuration.setCustomPostDeployers(new ArrayList<>());
    configuration.setCustomJobHandlers(new ArrayList<>());
    configuration.setCustomIncidentHandlers(new ArrayList<>());
    configuration.setCustomBatchJobHandlers(new ArrayList<>());
    configuration.setCustomFormEngines(new ArrayList<>());
    configuration.setCustomFormFieldValidators(new HashMap<>());
    configuration.setCustomFormTypes(new ArrayList<>());
    configuration.setCustomPreVariableSerializers(new ArrayList<>());
    configuration.setCustomPostVariableSerializers(new ArrayList<>());
    configuration.setCustomHistoryLevels(new ArrayList<>());
    configuration.setCustomPreCmmnTransformListeners(new ArrayList<>());
    configuration.setCustomPostCmmnTransformListeners(new ArrayList<>());
    configuration.setCustomPreBPMNParseListeners(new ArrayList<>());
    configuration.setCustomPostBPMNParseListeners(new ArrayList<>());
    configuration.setCustomEventHandlers(new ArrayList<>());
    configuration.setCustomPreMigrationActivityValidators(new ArrayList<>());
    configuration.setCustomPostMigrationActivityValidators(new ArrayList<>());
    configuration.setCustomPreMigrationInstructionValidators(new ArrayList<>());
    configuration.setCustomPostMigrationInstructionValidators(new ArrayList<>());
    configuration.setCustomPreMigratingActivityInstanceValidators(new ArrayList<>());
    configuration.setCustomPostMigratingActivityInstanceValidators(new ArrayList<>());

    Object sessionFactories = configuration.getCustomSessionFactories();
    Object formFieldValidators = configuration.getCustomFormFieldValidators();
    Object eventHandlers = configuration.getCustomEventHandlers();

    CamundaSpringBootUtil.initCustomFields(configuration);

    assertThat(configuration.getCustomSessionFactories()).isSameAs(sessionFactories);
    assertThat(configuration.getCustomFormFieldValidators()).isSameAs(formFieldValidators);
    assertThat(configuration.getCustomEventHandlers()).isSameAs(eventHandlers);
  }

  @Test
  public void shouldBuildAnInitialisedSpringConfiguration() {
    SpringProcessEngineConfiguration configuration =
        CamundaSpringBootUtil.springProcessEngineConfiguration();

    assertThat(configuration).isNotNull();
    assertThat(configuration.getCustomJobHandlers()).isNotNull();
  }
}
