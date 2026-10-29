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
package org.camunda.bpm.engine.impl.bpmn.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.camunda.bpm.engine.delegate.Expression;
import org.junit.Test;

public class DataAssociationTest {

  @Test
  public void shouldHoldSourceAndTarget() {
    DataAssociation association = new DataAssociation("src", "tgt");

    assertThat(association.getSource()).isEqualTo("src");
    assertThat(association.getTarget()).isEqualTo("tgt");
    assertThat(association.getSourceExpression()).isNull();
    assertThat(association.getVariables()).isNull();
    assertThat(association.getBusinessKeyExpression()).isNull();
  }

  @Test
  public void shouldHoldSourceExpressionAndTarget() {
    Expression expression = mock(Expression.class);

    DataAssociation association = new DataAssociation(expression, "tgt");

    assertThat(association.getSourceExpression()).isSameAs(expression);
    assertThat(association.getTarget()).isEqualTo("tgt");
    assertThat(association.getSource()).isNull();
  }

  @Test
  public void shouldHoldVariables() {
    DataAssociation association = new DataAssociation("all");

    assertThat(association.getVariables()).isEqualTo("all");
    assertThat(association.getTarget()).isNull();
  }

  @Test
  public void shouldHoldBusinessKeyExpression() {
    Expression expression = mock(Expression.class);

    DataAssociation association = new DataAssociation(expression);

    assertThat(association.getBusinessKeyExpression()).isSameAs(expression);
    assertThat(association.getSourceExpression()).isNull();
  }
}
