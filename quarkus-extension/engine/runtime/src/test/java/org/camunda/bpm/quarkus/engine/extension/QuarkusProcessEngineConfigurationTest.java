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

package org.camunda.bpm.quarkus.engine.extension;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.persistence.StrongUuidGenerator;
import org.junit.Test;

public class QuarkusProcessEngineConfigurationTest {

  private final QuarkusProcessEngineConfiguration configuration = new QuarkusProcessEngineConfiguration();

  @Test
  public void shouldApplyQuarkusDefaults() {
    assertThat(configuration.isJobExecutorActivate()).isTrue();
    assertThat(configuration.getJdbcUrl()).isNull();
    assertThat(configuration.getJdbcUsername()).isNull();
    assertThat(configuration.getJdbcPassword()).isNull();
    assertThat(configuration.getJdbcDriver()).isNull();
    assertThat(configuration.getDatabaseSchemaUpdate()).isEqualTo("true");
    assertThat(configuration.isTransactionsExternallyManaged()).isTrue();
    assertThat(configuration.getIdGenerator()).isInstanceOf(StrongUuidGenerator.class);
    assertThat(configuration.getHistory()).isEqualTo(HistoryLevel.HISTORY_LEVEL_FULL.getName());
  }

  @Test
  public void shouldBuildSchemaOperationsChainOnce() {
    configuration.initCommandExecutorDbSchemaOperations();
    assertThat(configuration.getCommandExecutorSchemaOperations()).isNotNull();

    Object first = configuration.getCommandExecutorSchemaOperations();
    configuration.initCommandExecutorDbSchemaOperations();

    assertThat(configuration.getCommandExecutorSchemaOperations()).isSameAs(first);
  }
}
