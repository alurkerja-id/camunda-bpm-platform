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
package org.camunda.bpm.engine.impl.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.delegate.Expression;
import org.camunda.bpm.engine.delegate.VariableScope;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class VariableDeclarationTest {

  protected VariableDeclaration declaration;
  protected VariableScope inner;
  protected VariableScope outer;

  @Before
  public void setUp() {
    declaration = new VariableDeclaration("order", "string");
    declaration.setDestinationVariableName("target");
    declaration.setSourceVariableName("source");
    inner = mock(VariableScope.class);
    outer = mock(VariableScope.class);
  }

  @Test
  public void shouldExposeItsProperties() {
    Expression source = mock(Expression.class);
    Expression destination = mock(Expression.class);
    Expression linkExpression = mock(Expression.class);

    declaration.setName("renamed");
    declaration.setType("long");
    declaration.setSourceExpression(source);
    declaration.setDestinationExpression(destination);
    declaration.setLink("link");
    declaration.setLinkExpression(linkExpression);

    assertThat(declaration.getName()).isEqualTo("renamed");
    assertThat(declaration.getType()).isEqualTo("long");
    assertThat(declaration.getSourceVariableName()).isEqualTo("source");
    assertThat(declaration.getSourceExpression()).isSameAs(source);
    assertThat(declaration.getDestinationVariableName()).isEqualTo("target");
    assertThat(declaration.getDestinationExpression()).isSameAs(destination);
    assertThat(declaration.getLink()).isEqualTo("link");
    assertThat(declaration.getLinkExpression()).isSameAs(linkExpression);
    assertThat(declaration).hasToString("VariableDeclaration[renamed:long]");
  }

  @Test
  public void shouldCopySourceVariableOnInitialize() {
    when(outer.hasVariable("source")).thenReturn(true);
    when(outer.getVariable("source")).thenReturn("value");

    declaration.initialize(inner, outer);

    verify(inner).setVariable("target", "value");
  }

  @Test
  public void shouldRefuseInitializeWithMissingSourceVariable() {
    when(outer.hasVariable("source")).thenReturn(false);

    assertThatThrownBy(() -> declaration.initialize(inner, outer))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("target")
        .hasMessageContaining("source");
  }

  @Test
  public void shouldEvaluateSourceExpressionOnInitialize() {
    Expression source = mock(Expression.class);
    when(source.getValue(outer)).thenReturn(42);
    declaration.setSourceVariableName(null);
    declaration.setSourceExpression(source);

    declaration.initialize(inner, outer);

    verify(inner).setVariable("target", 42);
  }

  @Test
  public void shouldCopyLinkedVariableOnInitialize() {
    when(outer.hasVariable("source")).thenReturn(true);
    when(outer.getVariable("source")).thenReturn("linked");
    declaration.setSourceVariableName("source");
    declaration.setLink("link");

    declaration.initialize(inner, outer);

    verify(inner, org.mockito.Mockito.times(2)).setVariable("target", "linked");
  }

  @Test
  public void shouldRefuseInitializeWithMissingLinkedVariable() {
    declaration.setSourceVariableName(null);
    declaration.setLink("link");

    assertThatThrownBy(() -> declaration.initialize(inner, outer))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  public void shouldEvaluateSourceExpressionForLinkExpressionOnInitialize() {
    Expression source = mock(Expression.class);
    when(source.getValue(outer)).thenReturn("from-source");
    declaration.setSourceVariableName(null);
    declaration.setSourceExpression(source);
    declaration.setLinkExpression(mock(Expression.class));

    declaration.initialize(inner, outer);

    verify(inner, org.mockito.Mockito.times(2)).setVariable("target", "from-source");
  }

  @Test
  public void shouldIgnoreLinkExpressionWithoutSourceExpressionOnInitialize() {
    declaration.setSourceVariableName(null);
    declaration.setLinkExpression(mock(Expression.class));

    declaration.initialize(inner, outer);

    verify(inner, never()).setVariable(any(), any());
  }

  @Test
  public void shouldCopyVariableBackOnDestroy() {
    when(inner.hasVariable("source")).thenReturn(true);
    when(inner.getVariable("source")).thenReturn("result");

    declaration.destroy(inner, outer);

    verify(outer).setVariable("target", "result");
  }

  @Test
  public void shouldRefuseDestroyWithMissingVariable() {
    when(inner.hasVariable("source")).thenReturn(false);

    assertThatThrownBy(() -> declaration.destroy(inner, outer))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Couldn't destroy variable source");
  }

  @Test
  public void shouldEvaluateDestinationExpressionOnDestroy() {
    Expression destination = mock(Expression.class);
    when(destination.getValue(inner)).thenReturn("computed");
    declaration.setDestinationVariableName("target");
    declaration.setSourceVariableName(null);
    declaration.setDestinationExpression(destination);
    when(inner.hasVariable(null)).thenReturn(true);

    declaration.destroy(inner, outer);

    verify(outer, org.mockito.Mockito.atLeastOnce()).setVariable("target", "computed");
  }

  @Test
  public void shouldCopyLinkedVariableBackOnDestroy() {
    declaration.setDestinationVariableName(null);
    declaration.setLink("link");
    when(inner.hasVariable("source")).thenReturn(true);
    when(inner.getVariable("source")).thenReturn("linked");

    declaration.destroy(inner, outer);

    verify(outer).setVariable(null, "linked");
  }

  @Test
  public void shouldRefuseDestroyWithMissingLinkedVariable() {
    declaration.setDestinationVariableName(null);
    declaration.setLink("link");

    assertThatThrownBy(() -> declaration.destroy(inner, outer))
        .isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldEvaluateSourceExpressionForLinkExpressionOnDestroy() {
    Expression source = mock(Expression.class);
    when(source.getValue(inner)).thenReturn("from-source");
    declaration.setDestinationVariableName(null);
    declaration.setSourceExpression(source);
    declaration.setLinkExpression(mock(Expression.class));

    declaration.destroy(inner, outer);

    verify(outer).setVariable(null, "from-source");
  }

}
