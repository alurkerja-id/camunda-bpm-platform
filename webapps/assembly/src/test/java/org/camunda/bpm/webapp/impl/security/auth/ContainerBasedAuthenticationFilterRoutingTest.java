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
package org.camunda.bpm.webapp.impl.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter.AUTHENTICATION_PROVIDER_PARAM;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.security.auth.impl.ContainerBasedAuthenticationProvider;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

/** Covers URL routing, engine lookup and init failure paths of {@link ContainerBasedAuthenticationFilter}. */
public class ContainerBasedAuthenticationFilterRoutingTest {

  public static class NotAProvider {
  }

  public static class NoDefaultConstructorProvider extends ContainerBasedAuthenticationProvider {
    public NoDefaultConstructorProvider(String ignored) {
    }
  }

  public static class ThrowingProvider extends ContainerBasedAuthenticationProvider {
    public ThrowingProvider() {
      throw new IllegalStateException("boom");
    }
  }

  public static abstract class AbstractProvider extends ContainerBasedAuthenticationProvider {
  }

  protected ContainerBasedAuthenticationFilter filter = new ContainerBasedAuthenticationFilter();

  @Before
  public void setUp() {
    filter.authenticationProvider = new ContainerBasedAuthenticationProvider();
  }

  protected MockHttpServletRequest request(String method, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest(new MockServletContext(), method, uri);
    request.setRequestURI(uri);
    return request;
  }

  protected void assertInitFails(String className, String message) {
    MockFilterConfig config = new MockFilterConfig();
    if (className != null) {
      config.addInitParameter(AUTHENTICATION_PROVIDER_PARAM, className);
    }
    assertThatThrownBy(() -> new ContainerBasedAuthenticationFilter().init(config))
        .isInstanceOf(ServletException.class).hasMessageContaining(message);
  }

  @Test
  public void shouldFailInitWithoutProvider() {
    assertInitFails(null, "no authentication provider set");
  }

  @Test
  public void shouldFailInitForUnknownClass() {
    assertInitFails("does.not.Exist", "authentication provider not found");
  }

  @Test
  public void shouldFailInitForNonProviderClass() {
    assertInitFails(NotAProvider.class.getName(), "does not implement interface");
  }

  @Test
  public void shouldFailInitWithoutDefaultConstructor() {
    assertInitFails(NoDefaultConstructorProvider.class.getName(), "no default constructor");
  }

  @Test
  public void shouldFailInitWhenConstructorThrows() {
    assertInitFails(ThrowingProvider.class.getName(), "cannot instantiate authentication provider");
  }

  @Test
  public void shouldFailInitForAbstractProvider() {
    assertInitFails(AbstractProvider.class.getName(), "cannot instantiate authentication provider");
  }

  @Test
  public void shouldInitWithValidProvider() throws ServletException {
    MockFilterConfig config = new MockFilterConfig();
    config.addInitParameter(AUTHENTICATION_PROVIDER_PARAM, ContainerBasedAuthenticationProvider.class.getName());
    ContainerBasedAuthenticationFilter initialized = new ContainerBasedAuthenticationFilter();

    initialized.init(config);
    initialized.destroy();

    assertThat(initialized.authenticationProvider).isInstanceOf(ContainerBasedAuthenticationProvider.class);
  }

  @Test
  public void shouldExtractEngineNameFromAppUrl() {
    assertThat(filter.extractEngineName(request("GET", "/app/cockpit/default/"))).isEqualTo("default");
    assertThat(filter.extractEngineName(request("GET", "/app/welcome/other/"))).isEqualTo("other");
  }

  @Test
  public void shouldExtractEngineNameFromEngineApiUrl() {
    assertThat(filter.extractEngineName(request("POST", "/api/engine/engine/custom/process-definition"))).isEqualTo("custom");
  }

  @Test
  public void shouldExtractEngineNameFromPluginApiUrl() {
    assertThat(filter.extractEngineName(request("GET", "/api/cockpit/plugin/base/default/process"))).isEqualTo("default");
  }

  @Test
  public void shouldIgnoreStaticPluginAssetsOnGetOnly() {
    String uri = "/api/cockpit/plugin/base/static/app/plugin.js";

    assertThat(filter.extractEngineName(request("GET", uri))).isNull();
    // for non-GET the static pattern is skipped and the generic plugin pattern applies
    assertThat(filter.extractEngineName(request("POST", uri))).isEqualTo("static");
  }

  @Test
  public void shouldIgnoreUnknownUrls() {
    assertThat(filter.extractEngineName(request("GET", "/index.html"))).isNull();
  }

  @Test
  public void shouldStripContextAndApplicationPathFromRequestUri() {
    MockServletContext servletContext = new MockServletContext();
    servletContext.setAttribute("org.camunda.bpm.spring.boot.starter.webapp.applicationPath", "/camunda");
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", "/ctx/camunda/app/admin/default/");
    request.setContextPath("/ctx");
    request.setRequestURI("/ctx/camunda/app/admin/default/");

    String uri = filter.getRequestUri(request);

    assertThat(uri).isEqualTo("/app/admin/default/");
  }

  @Test
  public void shouldPassThroughRequestsWithoutEngine() throws IOException, ServletException {
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletRequest request = request("GET", "/index.html");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  public void shouldRespondNotFoundForUnavailableEngine() throws IOException, ServletException {
    FilterChain chain = mock(FilterChain.class);
    ContainerBasedAuthenticationFilter missing = new ContainerBasedAuthenticationFilter() {
      @Override
      protected ProcessEngine getAddressedEngine(String engineName) {
        return null;
      }
    };
    MockHttpServletResponse response = new MockHttpServletResponse();

    missing.doFilter(request("GET", "/app/cockpit/ghost/"), response, chain);

    assertThat(response.getStatus()).isEqualTo(404);
    verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  public void shouldMatchAuthenticationByEngineAndIdentity() {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getProcessEngineName()).thenReturn("default");
    when(authentication.getIdentityId()).thenReturn("demo");
    Authentications authentications = mock(Authentications.class);
    when(authentications.getAuthenticationForProcessEngine("default")).thenReturn(authentication);

    assertThat(filter.existisAuthentication(authentications, "default", "demo")).isTrue();
    assertThat(filter.existisAuthentication(authentications, "default", "other")).isFalse();
    assertThat(filter.existisAuthentication(authentications, "another", "demo")).isFalse();
    assertThat(filter.isAuthenticated(authentication, "another", "demo")).isFalse();
  }
}
