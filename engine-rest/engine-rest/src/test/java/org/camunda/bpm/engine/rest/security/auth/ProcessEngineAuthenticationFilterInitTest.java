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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.camunda.bpm.engine.ProcessEngine;
import org.junit.Test;

/**
 * Covers how {@link ProcessEngineAuthenticationFilter} reads its init parameters.
 *
 * <p>The provider is loaded by name at startup, so every way that lookup can fail turns into its
 * own ServletException with a distinct message. Those five outcomes are what this pins; the
 * request flow itself needs a live engine and is covered elsewhere.
 */
public class ProcessEngineAuthenticationFilterInitTest {

  /** A provider that does nothing, used only to prove the happy path of the lookup. */
  public static class TestAuthenticationProvider implements AuthenticationProvider {

    @Override
    public AuthenticationResult extractAuthenticatedUser(HttpServletRequest request,
        ProcessEngine engine) {
      return AuthenticationResult.unsuccessful();
    }

    @Override
    public void augmentResponseByAuthenticationChallenge(HttpServletResponse response,
        ProcessEngine engine) {
    }
  }

  /** No public no-arg constructor, so instantiating it fails. */
  public static class UninstantiableProvider extends TestAuthenticationProvider {

    private UninstantiableProvider() {
    }
  }

  protected FilterConfig config(String providerClassName, String pathPrefix) {
    FilterConfig config = mock(FilterConfig.class);
    when(config.getInitParameter(ProcessEngineAuthenticationFilter.AUTHENTICATION_PROVIDER_PARAM))
        .thenReturn(providerClassName);
    when(config.getInitParameter(ProcessEngineAuthenticationFilter.SERVLET_PATH_PREFIX))
        .thenReturn(pathPrefix);
    return config;
  }

  @Test
  public void shouldLoadTheConfiguredProvider() throws ServletException {
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter();

    filter.init(config(TestAuthenticationProvider.class.getName(), null));

    assertThat(filter.authenticationProvider).isInstanceOf(TestAuthenticationProvider.class);
    assertThat(filter.servletPathPrefix).isNull();
  }

  @Test
  public void shouldKeepTheServletPathPrefixWhenGiven() throws ServletException {
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter();

    filter.init(config(TestAuthenticationProvider.class.getName(), "/api"));

    assertThat(filter.servletPathPrefix).isEqualTo("/api");
  }

  @Test
  public void shouldRefuseStartingWithoutAProvider() {
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter();

    assertThatThrownBy(() -> filter.init(config(null, null)))
        .isInstanceOf(ServletException.class)
        .hasMessageContaining("no authentication provider set");
  }

  @Test
  public void shouldRefuseAProviderClassThatIsNotOnTheClasspath() {
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter();

    assertThatThrownBy(() -> filter.init(config("no.such.Provider", null)))
        .isInstanceOf(ServletException.class)
        .hasMessageContaining("authentication provider not found");
  }

  @Test
  public void shouldRefuseAProviderThatCannotBeInstantiated() {
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter();

    assertThatThrownBy(() -> filter.init(config(UninstantiableProvider.class.getName(), null)))
        .isInstanceOf(ServletException.class)
        .hasMessageContaining("constructor not accessible");
  }

  @Test
  public void shouldRefuseAClassThatIsNotAnAuthenticationProvider() {
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter();

    assertThatThrownBy(() -> filter.init(config("java.lang.String", null)))
        .isInstanceOf(ServletException.class)
        .hasMessageContaining("does not implement interface");
  }

  @Test
  public void shouldSurviveDestroy() {
    new ProcessEngineAuthenticationFilter().destroy();
  }
}
