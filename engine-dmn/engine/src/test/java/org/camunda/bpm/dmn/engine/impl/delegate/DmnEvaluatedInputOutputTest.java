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
package org.camunda.bpm.dmn.engine.impl.delegate;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.dmn.engine.impl.DmnDecisionTableInputImpl;
import org.camunda.bpm.dmn.engine.impl.DmnDecisionTableOutputImpl;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.Test;

/**
 * Covers the two delegate objects a decision evaluation hands to listeners.
 *
 * <p>Both are plain carriers whose branches live entirely in equals and hashCode: four fields, each
 * compared through its own null guard. The tests vary one field at a time from both sides of every
 * guard, which is what those branches need.
 */
public class DmnEvaluatedInputOutputTest {

  protected DmnEvaluatedInputImpl input() {
    DmnDecisionTableInputImpl definition = new DmnDecisionTableInputImpl();
    definition.setId("id");
    definition.setName("name");
    definition.setInputVariable("variable");

    DmnEvaluatedInputImpl input = new DmnEvaluatedInputImpl(definition);
    input.setValue(Variables.stringValue("value"));
    return input;
  }

  protected DmnEvaluatedOutputImpl output() {
    DmnDecisionTableOutputImpl definition = new DmnDecisionTableOutputImpl();
    definition.setId("id");
    definition.setName("name");
    definition.setOutputName("output");

    return new DmnEvaluatedOutputImpl(definition, Variables.stringValue("value"));
  }

  // input -----------------------------------------------------------------------------------

  @Test
  public void shouldCopyTheInputDefinition() {
    DmnEvaluatedInputImpl input = input();

    assertThat(input.getId()).isEqualTo("id");
    assertThat(input.getName()).isEqualTo("name");
    assertThat(input.getInputVariable()).isEqualTo("variable");
    assertThat(input.getValue().getValue()).isEqualTo("value");
  }

  @Test
  public void shouldAllowEveryInputFieldToBeReplaced() {
    DmnEvaluatedInputImpl input = input();
    TypedValue other = Variables.integerValue(1);

    input.setId("otherId");
    input.setName("otherName");
    input.setInputVariable("otherVariable");
    input.setValue(other);

    assertThat(input.getId()).isEqualTo("otherId");
    assertThat(input.getName()).isEqualTo("otherName");
    assertThat(input.getInputVariable()).isEqualTo("otherVariable");
    assertThat(input.getValue()).isSameAs(other);
  }

  @Test
  public void shouldCompareInputsFieldByField() {
    DmnEvaluatedInputImpl input = input();

    assertThat(input.equals(input)).isTrue();
    assertThat(input).isEqualTo(input());
    assertThat(input.equals(null)).isFalse();
    assertThat(input.equals("text")).isFalse();

    DmnEvaluatedInputImpl differentId = input();
    differentId.setId("other");
    assertThat(input.equals(differentId)).isFalse();

    DmnEvaluatedInputImpl differentName = input();
    differentName.setName("other");
    assertThat(input.equals(differentName)).isFalse();

    DmnEvaluatedInputImpl differentVariable = input();
    differentVariable.setInputVariable("other");
    assertThat(input.equals(differentVariable)).isFalse();

    DmnEvaluatedInputImpl differentValue = input();
    differentValue.setValue(Variables.stringValue("other"));
    assertThat(input.equals(differentValue)).isFalse();
  }

  /** Each field is guarded for null on both sides, so null must be driven both ways. */
  @Test
  public void shouldCompareInputsThatCarryNulls() {
    DmnEvaluatedInputImpl empty = new DmnEvaluatedInputImpl(new DmnDecisionTableInputImpl());
    DmnEvaluatedInputImpl alsoEmpty = new DmnEvaluatedInputImpl(new DmnDecisionTableInputImpl());

    assertThat(empty).isEqualTo(alsoEmpty);
    assertThat(empty.equals(input())).isFalse();
    assertThat(input().equals(empty)).isFalse();

    DmnEvaluatedInputImpl noValue = input();
    noValue.setValue(null);
    assertThat(noValue.equals(input())).isFalse();
    assertThat(input().equals(noValue)).isFalse();
  }

  @Test
  public void shouldHashInputsConsistently() {
    DmnEvaluatedInputImpl allNull = input();
    allNull.setId(null);
    allNull.setName(null);
    allNull.setInputVariable(null);
    allNull.setValue(null);

    assertThat(input().hashCode()).isEqualTo(input().hashCode());
    assertThat(allNull.hashCode()).isZero();
  }

  @Test
  public void shouldRenderEveryInputFieldInToString() {
    String rendered = input().toString();

    assertThat(rendered).startsWith("DmnEvaluatedInputImpl{");
    assertThat(rendered).contains("id='id'").contains("name='name'")
        .contains("inputVariable='variable'").contains("value=");
  }

  // output ----------------------------------------------------------------------------------

  @Test
  public void shouldCopyTheOutputDefinitionAndValue() {
    DmnEvaluatedOutputImpl output = output();

    assertThat(output.getId()).isEqualTo("id");
    assertThat(output.getName()).isEqualTo("name");
    assertThat(output.getOutputName()).isEqualTo("output");
    assertThat(output.getValue().getValue()).isEqualTo("value");
  }

  @Test
  public void shouldAllowEveryOutputFieldToBeReplaced() {
    DmnEvaluatedOutputImpl output = output();
    TypedValue other = Variables.integerValue(1);

    output.setId("otherId");
    output.setName("otherName");
    output.setOutputName("otherOutput");
    output.setValue(other);

    assertThat(output.getId()).isEqualTo("otherId");
    assertThat(output.getName()).isEqualTo("otherName");
    assertThat(output.getOutputName()).isEqualTo("otherOutput");
    assertThat(output.getValue()).isSameAs(other);
  }

  @Test
  public void shouldCompareOutputsFieldByField() {
    DmnEvaluatedOutputImpl output = output();

    assertThat(output.equals(output)).isTrue();
    assertThat(output).isEqualTo(output());
    assertThat(output.equals(null)).isFalse();
    assertThat(output.equals("text")).isFalse();

    DmnEvaluatedOutputImpl differentId = output();
    differentId.setId("other");
    assertThat(output.equals(differentId)).isFalse();

    DmnEvaluatedOutputImpl differentName = output();
    differentName.setName("other");
    assertThat(output.equals(differentName)).isFalse();

    DmnEvaluatedOutputImpl differentOutputName = output();
    differentOutputName.setOutputName("other");
    assertThat(output.equals(differentOutputName)).isFalse();

    DmnEvaluatedOutputImpl differentValue = output();
    differentValue.setValue(Variables.stringValue("other"));
    assertThat(output.equals(differentValue)).isFalse();
  }

  @Test
  public void shouldCompareOutputsThatCarryNulls() {
    DmnEvaluatedOutputImpl empty =
        new DmnEvaluatedOutputImpl(new DmnDecisionTableOutputImpl(), null);
    DmnEvaluatedOutputImpl alsoEmpty =
        new DmnEvaluatedOutputImpl(new DmnDecisionTableOutputImpl(), null);

    assertThat(empty).isEqualTo(alsoEmpty);
    assertThat(empty.equals(output())).isFalse();
    assertThat(output().equals(empty)).isFalse();
  }

  @Test
  public void shouldHashOutputsConsistently() {
    DmnEvaluatedOutputImpl allNull = output();
    allNull.setId(null);
    allNull.setName(null);
    allNull.setOutputName(null);
    allNull.setValue(null);

    assertThat(output().hashCode()).isEqualTo(output().hashCode());
    assertThat(allNull.hashCode()).isZero();
  }

  @Test
  public void shouldRenderEveryOutputFieldInToString() {
    String rendered = output().toString();

    assertThat(rendered).startsWith("DmnEvaluatedOutputImpl{");
    assertThat(rendered).contains("id='id'").contains("name='name'")
        .contains("outputName='output'").contains("value=");
  }
}
