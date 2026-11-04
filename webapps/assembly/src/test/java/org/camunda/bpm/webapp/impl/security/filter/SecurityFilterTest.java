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
package org.camunda.bpm.webapp.impl.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.camunda.bpm.webapp.impl.security.auth.Authentication;
import org.camunda.bpm.webapp.impl.security.auth.Authentications;
import org.junit.After;
import org.junit.Test;

/**
 * Covers {@link SecurityFilter}: the verdict of the first matching rule decides between letting the
 * request through, 401 (not logged in), 403 (logged in but not allowed) and the application-specific
 * 403 that names the app the user lacks rights for. Also covers loading the rule file from the
 * servlet context.
 */
public class SecurityFilterTest {

  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final HttpServletResponse response = mock(HttpServletResponse.class);
  private final FilterChain chain = mock(FilterChain.class);
  private final SecurityFilter filter = new SecurityFilter();

  @After
  public void clearAuthentications() {
    Authentications.clearCurrent();
  }

  private void requestFor(String method, String contextPath, String uri) {
    when(request.getMethod()).thenReturn(method);
    when(request.getContextPath()).thenReturn(contextPath);
    when(request.getRequestURI()).thenReturn(uri);
  }

  private void ruleAnswers(Authorization authorization) {
    SecurityFilterRule rule = mock(SecurityFilterRule.class);
    when(rule.authorize(any(), any())).thenReturn(authorization);
    filter.filterRules = Collections.singletonList(rule);
  }

  // doFilter ------------------------------------------------------------------------------------

  @Test
  public void shouldPassAGrantedRequestOnToTheChain() throws Exception {
    requestFor("GET", "/camunda", "/camunda/app/cockpit");
    ruleAnswers(Authorization.granted(new Authentication("ana", "default")));

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).sendError(org.mockito.ArgumentMatchers.anyInt());
    verify(response).addHeader("X-Authorized-User", "ana");
  }

  @Test
  public void shouldPassARequestWhenNoRuleHasAnOpinion() throws Exception {
    requestFor("GET", "", "/api/engine");
    filter.filterRules = Collections.emptyList();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  public void shouldHandTheRulesTheMethodAndThePathWithoutTheContextPath() throws Exception {
    requestFor("POST", "/camunda", "/camunda/api/admin/auth");
    SecurityFilterRule rule = mock(SecurityFilterRule.class);
    when(rule.authorize("POST", "/api/admin/auth")).thenReturn(Authorization.granted(Authentication.ANONYMOUS));
    filter.filterRules = Collections.singletonList(rule);

    filter.doFilter(request, response, chain);

    verify(rule).authorize("POST", "/api/admin/auth");
    verify(chain).doFilter(request, response);
  }

  @Test
  public void shouldAnswer401ForAnUnauthenticatedDeniedRequest() throws Exception {
    requestFor("GET", "", "/api/secret");
    ruleAnswers(Authorization.denied(Authentication.ANONYMOUS));

    filter.doFilter(request, response, chain);

    verify(response).sendError(401);
    verifyNoInteractions(chain);
  }

  @Test
  public void shouldAnswer401WhenTheDenialCarriesNoAuthenticationAtAll() throws Exception {
    requestFor("GET", "", "/api/secret");
    ruleAnswers(Authorization.denied(null));

    filter.doFilter(request, response, chain);

    verify(response).sendError(401);
    verifyNoInteractions(chain);
  }

  @Test
  public void shouldAnswerPlain403ForALoggedInUserDeniedWithoutAnApplication() throws Exception {
    requestFor("GET", "", "/api/secret");
    ruleAnswers(Authorization.denied(new Authentication("ana", "default")));

    filter.doFilter(request, response, chain);

    verify(response).sendError(403);
    verifyNoInteractions(chain);
  }

  @Test
  public void shouldNameTheApplicationInThe403WhenTheUserLacksAccessToIt() throws Exception {
    requestFor("GET", "", "/app/admin");
    ruleAnswers(Authorization.denied(new Authentication("ana", "default")).forApplication("admin"));

    filter.doFilter(request, response, chain);

    verify(response).sendError(403, "No access rights for admin");
    verifyNoInteractions(chain);
  }

  @Test
  public void shouldUseTheFirstRuleThatHasAnOpinion() throws Exception {
    requestFor("GET", "", "/x");
    SecurityFilterRule abstaining = mock(SecurityFilterRule.class);
    SecurityFilterRule denying = mock(SecurityFilterRule.class);
    SecurityFilterRule neverAsked = mock(SecurityFilterRule.class);
    when(denying.authorize("GET", "/x")).thenReturn(Authorization.denied(Authentication.ANONYMOUS));
    filter.filterRules = java.util.Arrays.asList(abstaining, denying, neverAsked);

    filter.doFilter(request, response, chain);

    verify(response).sendError(401);
    verifyNoInteractions(neverAsked);
  }

  // helpers -------------------------------------------------------------------------------------

  @Test
  public void shouldConsiderTheRequestAuthenticatedOnlyWithACurrentAuthentication() {
    assertThat(filter.isAuthenticated(request)).isFalse();

    Authentications.setCurrent(new Authentications());

    assertThat(filter.isAuthenticated(request)).isTrue();
  }

  // init / loading rules ------------------------------------------------------------------------

  private FilterConfig configFor(String fileName, java.io.InputStream resource, String appPath) {
    FilterConfig config = mock(FilterConfig.class);
    ServletContext context = mock(ServletContext.class);
    when(config.getServletContext()).thenReturn(context);
    when(config.getInitParameter("configFile")).thenReturn(fileName);
    when(context.getResourceAsStream(fileName)).thenReturn(resource);
    when(context.getAttribute(org.mockito.ArgumentMatchers.anyString())).thenReturn(appPath);
    return config;
  }

  private static java.io.InputStream json(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void shouldLoadRulesFromTheConfiguredServletContextResource() throws Exception {
    filter.init(configFor("/WEB-INF/rules.json", json("{\"pathFilter\":{\"deniedPaths\":[{\"path\":\"/api/.*\",\"methods\":\"*\"}],"
        + "\"allowedPaths\":[]}}"), "/camunda"));

    assertThat(filter.filterRules).hasSize(1);
    // the app path is prefixed to the configured rule paths: a denied path nobody allows gets no
    // verdict, a path outside the denied list is granted anonymously
    assertThat(filter.filterRules.get(0).authorize("GET", "/camunda/api/x")).isNull();
    assertThat(filter.filterRules.get(0).authorize("GET", "/api/x").isGranted()).isTrue();
  }

  @Test
  public void shouldFailInitWhenTheConfigFileIsNotInTheServletContext() {
    FilterConfig config = configFor("/WEB-INF/missing.json", null, null);

    assertThatThrownBy(() -> filter.init(config))
        .isInstanceOf(ServletException.class)
        .hasMessageContaining("/WEB-INF/missing.json")
        .hasMessageContaining("no such resource");
  }

  @Test
  public void shouldWrapAParseFailureNamingTheConfigFile() {
    FilterConfig config = configFor("/WEB-INF/broken.json", json("{ not json"), "");

    assertThatThrownBy(() -> filter.init(config))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Exception while parsing '/WEB-INF/broken.json'");
  }

  @Test
  public void shouldTolerateDestroy() {
    filter.destroy();
  }
}
