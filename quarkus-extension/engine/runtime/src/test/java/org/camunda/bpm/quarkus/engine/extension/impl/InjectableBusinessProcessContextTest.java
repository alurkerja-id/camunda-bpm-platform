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

package org.camunda.bpm.quarkus.engine.extension.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.inject.spi.BeanManager;
import org.camunda.bpm.engine.cdi.impl.util.BeanManagerLookup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InjectableBusinessProcessContextTest {

  private BeanManager previous;
  private final InjectableBusinessProcessContext context = new InjectableBusinessProcessContext();

  @Before
  public void saveLookup() {
    previous = BeanManagerLookup.localInstance;
  }

  @After
  public void restoreLookup() {
    BeanManagerLookup.localInstance = previous;
  }

  @Test
  public void shouldResolveBeanManagerFromLocalLookup() {
    BeanManager beanManager = mock(BeanManager.class);
    BeanManagerLookup.localInstance = beanManager;

    assertThat(context.getBeanManager()).isSameAs(beanManager);
  }

  @Test
  public void shouldRejectGetState() {
    assertThatThrownBy(context::getState)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("getState");
  }

  @Test
  public void shouldRejectDestroyAll() {
    assertThatThrownBy(context::destroy)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("destroy");
  }

  @Test
  public void shouldRejectDestroyContextual() {
    Contextual<?> contextual = mock(Contextual.class);

    assertThatThrownBy(() -> context.destroy(contextual))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("destroy(contextual)");
  }
}
