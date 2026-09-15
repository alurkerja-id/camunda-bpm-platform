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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Enumeration;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import org.junit.Test;

/**
 * Covers the configuration surface of {@link CsrfPreventionFilter} - init parameters, the setters
 * they drive, and the small helpers around them. The request flow itself is covered by
 * {@link org.camunda.bpm.webapp.impl.security.filter.csrf.CsrfPreventionFilterTest} and its siblings; this fills in the branches those never reach.
 */
public class CsrfPreventionFilterConfigTest {

  protected FilterConfig config(String... keyThenValue) {
    FilterConfig config = mock(FilterConfig.class);
    for (int i = 0; i < keyThenValue.length; i += 2) {
      when(config.getInitParameter(keyThenValue[i])).thenReturn(keyThenValue[i + 1]);
    }
    Enumeration<String> empty = Collections.enumeration(Collections.<String>emptyList());
    when(config.getInitParameterNames()).thenReturn(empty);
    return config;
  }

  // defaults ---------------------------------------------------------------------------------

  @Test
  public void shouldStartFromTheDocumentedDefaults() throws ServletException {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();

    filter.init(config());

    assertThat(filter.getRandomClass()).isEqualTo("java.security.SecureRandom");
    assertThat(filter.getDenyStatus()).isEqualTo(403);
    assertThat(filter.getTargetOrigin()).isNull();
  }

  /** Blank init parameters are treated as absent, so the defaults survive them. */
  @Test
  public void shouldIgnoreBlankInitParameters() throws ServletException {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();

    filter.init(config("randomClass", "  ", "targetOrigin", "", "denyStatus", "   ",
        "entryPoints", ""));

    assertThat(filter.getRandomClass()).isEqualTo("java.security.SecureRandom");
    assertThat(filter.getDenyStatus()).isEqualTo(403);
    assertThat(filter.getTargetOrigin()).isNull();
  }

  // each init parameter ------------------------------------------------------------------------

  @Test
  public void shouldTakeTheConfiguredRandomClass() throws ServletException {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();

    filter.init(config("randomClass", "java.util.Random"));

    assertThat(filter.getRandomClass()).isEqualTo("java.util.Random");
  }

  @Test
  public void shouldTakeTheConfiguredTargetOrigin() throws ServletException {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();

    filter.init(config("targetOrigin", "http://example.com:8080"));

    assertThat(filter.getTargetOrigin().getHost()).isEqualTo("example.com");
    assertThat(filter.getTargetOrigin().getPort()).isEqualTo(8080);
  }

  @Test
  public void shouldTakeTheConfiguredDenyStatus() throws ServletException {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();

    filter.init(config("denyStatus", "404"));

    assertThat(filter.getDenyStatus()).isEqualTo(404);
  }

  // init failures --------------------------------------------------------------------------------

  @Test
  public void shouldRefuseARandomClassThatDoesNotExist() {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();

    assertThatThrownBy(() -> filter.init(config("randomClass", "no.such.RandomClass")))
        .isInstanceOf(ServletException.class)
        .hasMessageContaining("Random class not found");
  }

  @Test
  public void shouldRefuseARandomClassWithoutANoArgConstructor() {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();

    assertThatThrownBy(() -> filter.init(config("randomClass", "java.util.Scanner")))
        .isInstanceOf(ServletException.class)
        .hasMessageContaining("cannot instantiate provided Random class");
  }

  @Test
  public void shouldRefuseAMalformedTargetOrigin() {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();

    assertThatThrownBy(() -> filter.init(config("targetOrigin", "not a url")))
        .isInstanceOf(ServletException.class)
        .hasMessageContaining("Could not read target origin URL");
  }

  // the setters on their own -----------------------------------------------------------------------

  @Test
  public void shouldRefuseAMalformedTargetOriginFromTheSetter() {
    assertThatThrownBy(() -> new CsrfPreventionFilter().setTargetOrigin("not a url"))
        .isInstanceOf(MalformedURLException.class);
  }

  @Test
  public void shouldKeepWhatTheSettersAreGiven() throws MalformedURLException {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();

    filter.setRandomClass("java.util.Random");
    filter.setDenyStatus(418);
    filter.setTargetOrigin("https://example.com");

    assertThat(filter.getRandomClass()).isEqualTo("java.util.Random");
    assertThat(filter.getDenyStatus()).isEqualTo(418);
    assertThat(filter.getTargetOrigin().getProtocol()).isEqualTo("https");
  }

  @Test
  public void shouldSurviveDestroy() {
    new CsrfPreventionFilter().destroy();
  }

  // entry points ---------------------------------------------------------------------------------------

  @Test
  public void shouldSplitEntryPointsOnCommasAndTrimThem() throws ServletException {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();

    filter.init(config("entryPoints", " /api/one , /api/two "));

    assertThat(filter.isNonModifyingRequest(request("POST", "/api/one", null))).isTrue();
    assertThat(filter.isNonModifyingRequest(request("POST", "/api/two", null))).isTrue();
    assertThat(filter.isNonModifyingRequest(request("POST", "/api/three", null))).isFalse();
  }

  @Test
  public void shouldTreatAnEntryPointWithPathInfoAsOnePath() throws ServletException {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();

    filter.init(config("entryPoints", "/api/one/extra"));

    assertThat(filter.isNonModifyingRequest(request("POST", "/api/one", "/extra"))).isTrue();
  }

  @Test
  public void shouldTreatReadOnlyMethodsAsNonModifying() throws ServletException {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();
    filter.init(config());

    assertThat(filter.isNonModifyingRequest(request("GET", "/anything", null))).isTrue();
    assertThat(filter.isNonModifyingRequest(request("HEAD", "/anything", null))).isTrue();
    assertThat(filter.isNonModifyingRequest(request("OPTIONS", "/anything", null))).isTrue();
    assertThat(filter.isNonModifyingRequest(request("POST", "/anything", null))).isFalse();
    assertThat(filter.isNonModifyingRequest(request("DELETE", "/anything", null))).isFalse();
  }

  // token generation ------------------------------------------------------------------------------------

  @Test
  public void shouldGenerateAHexTokenOfThirtyTwoCharacters() throws ServletException {
    CsrfPreventionFilter filter = new CsrfPreventionFilter();
    filter.init(config());

    String token = filter.generateCSRFToken();

    assertThat(token).hasSize(32);
    assertThat(token).matches("[0-9A-F]{32}");
    assertThat(filter.generateCSRFToken()).isNotEqualTo(token);
  }

  protected HttpServletRequest request(String method, String servletPath, String pathInfo) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn(method);
    when(request.getServletPath()).thenReturn(servletPath);
    when(request.getPathInfo()).thenReturn(pathInfo);
    return request;
  }
}
