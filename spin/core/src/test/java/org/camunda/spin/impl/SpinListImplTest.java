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
package org.camunda.spin.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;

import org.camunda.spin.json.SpinJsonNode;
import org.junit.Test;

public class SpinListImplTest {

  protected SpinJsonNode a = mock(SpinJsonNode.class);
  protected SpinJsonNode b = mock(SpinJsonNode.class);

  @Test
  public void shouldCreateEmptyList() {
    assertThat(new SpinListImpl<SpinJsonNode>()).isEmpty();
  }

  @Test
  public void shouldCreateListWithInitialCapacity() {
    SpinListImpl<SpinJsonNode> list = new SpinListImpl<>(5);

    assertThat(list).isEmpty();
    list.add(a);
    assertThat(list).containsExactly(a);
  }

  @Test
  public void shouldCreateListFromCollection() throws Exception {
    List<SpinJsonNode> source = Arrays.asList(a, b);

    // the Collection and Iterable constructors are ambiguous for compile-time callers, so use reflection
    Object list = SpinListImpl.class.getConstructor(java.util.Collection.class).newInstance(source);

    assertThat((List<Object>) list).containsExactly(a, b);
  }

  @Test
  public void shouldCreateListFromIterable() {
    Iterable<SpinJsonNode> iterable = new Iterable<SpinJsonNode>() {
      @Override
      public java.util.Iterator<SpinJsonNode> iterator() {
        return Arrays.asList(b, a).iterator();
      }
    };

    assertThat(new SpinListImpl<SpinJsonNode>((Iterable<SpinJsonNode>) iterable)).containsExactly(b, a);
  }
}
