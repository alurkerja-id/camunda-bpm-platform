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
package org.camunda.bpm.engine.rest.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.util.EngineUtil;
import org.junit.Test;

/**
 * Covers the two parts of {@link ProcessEngineAuthenticationFilter} the flow tests stub out:
 * the engine lookup through the SPI and the init failures for abstract or throwing providers.
 */
public class ProcessEngineAuthenticationFilterLookupTest {

  /** Abstract, so reflection cannot instantiate it. */
  public abstract static class AbstractProvider extends ProcessEngineAuthenticationFilterInitTest.TestAuthenticationProvider {
  }

  /** The constructor itself fails. */
  public static class ThrowingProvider extends ProcessEngineAuthenticationFilterInitTest.TestAuthenticationProvider {
    public ThrowingProvider() {
      throw new IllegalStateException("constructor failed");
    }
  }

  protected FilterConfig config(String providerClassName) {
    FilterConfig config = mock(FilterConfig.class);
    when(config.getInitParameter(ProcessEngineAuthenticationFilter.AUTHENTICATION_PROVIDER_PARAM))
        .thenReturn(providerClassName);
    return config;
  }

  @Test
  public void shouldRefuseAnAbstractProvider() {
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter();

    assertThatThrownBy(() -> filter.init(config(AbstractProvider.class.getName())))
        .isInstanceOf(ServletException.class)
        .hasMessage("Cannot instantiate authentication filter: cannot instantiate authentication provider")
        .hasCauseInstanceOf(InstantiationException.class);
    assertThat(filter.authenticationProvider).isNull();
  }

  @Test
  public void shouldRefuseAProviderWhoseConstructorFails() {
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter();

    assertThatThrownBy(() -> filter.init(config(ThrowingProvider.class.getName())))
        .isInstanceOf(ServletException.class)
        .hasMessage("Cannot instantiate authentication filter: cannot instantiate authentication provider")
        .hasCauseInstanceOf(java.lang.reflect.InvocationTargetException.class);
    assertThat(filter.authenticationProvider).isNull();
  }

  @Test
  public void shouldLookUpTheAddressedEngineThroughTheEngineSpi() {
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter();

    ProcessEngine engine = filter.getAddressedEngine("default");

    assertThat(engine).isNotNull();
    assertThat(engine).isSameAs(EngineUtil.lookupProcessEngine("default"));
  }

  @Test
  public void shouldPassTheEngineNameOnToTheSpi() {
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter();

    ProcessEngine engine = filter.getAddressedEngine("named-engine");

    assertThat(engine.getName()).isEqualTo("named-engine");
  }
}
