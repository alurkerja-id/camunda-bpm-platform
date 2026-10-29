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
package org.camunda.bpm.engine.impl.db;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.camunda.bpm.engine.ProcessEngineException;
import org.junit.Test;

/** The command line schema tools validate their arguments before touching any engine. */
public class DbSchemaToolsTest {

  @Test
  public void shouldRejectMissingArguments() {
    assertThatThrownBy(() -> DbSchemaExecuteFile.main(new String[0]))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-");
  }

  @Test
  public void shouldRejectTooManyArguments() {
    assertThatThrownBy(() -> DbSchemaExecuteFile.main(new String[] {"a", "b", "c"}))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("3");
  }

  @Test
  public void shouldFailOnUnknownConfigurationResource() {
    assertThatThrownBy(() -> DbSchemaExecuteFile.main(new String[] {"no-such-config.xml", "schema.sql"}))
        .isInstanceOf(RuntimeException.class);
  }
}
