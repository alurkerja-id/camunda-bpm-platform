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

import java.io.IOException;
import javax.servlet.ServletException;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.webapp.impl.security.filter.util.CsrfConstants;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/**
 * Covers the Same-Origin verification, the token validation branches and the session-mutex
 * handling of {@link CsrfPreventionFilter} - the paths the parameterized request tests never reach
 * because they run without a target origin.
 */
public class CsrfPreventionFilterRequestFlowTest {

  protected CsrfPreventionFilter filter;
  protected MockHttpServletResponse response;

  @Before
  public void setup() throws Exception {
    filter = new CsrfPreventionFilter();
    filter.init(new MockFilterConfig());
    filter.setTargetOrigin("https://app.example.com:8443");
    response = new MockHttpServletResponse();
  }

  protected MockHttpServletRequest post(MockHttpSession session) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/camunda/api/engine/x");
    request.setServletPath("/api/engine/x");
    request.setSession(session);
    return request;
  }

  // same origin ------------------------------------------------------------------------------

  @Test
  public void shouldSkipTheOriginCheckWithoutATargetOrigin() throws IOException {
    CsrfPreventionFilter noOrigin = new CsrfPreventionFilter();

    assertThat(noOrigin.doSameOriginStandardHeadersVerification(post(new MockHttpSession()), response)).isTrue();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void shouldAcceptAMatchingOriginHeader() throws IOException {
    MockHttpServletRequest request = post(new MockHttpSession());
    request.addHeader("Origin", "https://app.example.com:8443/some/page");

    assertThat(filter.doSameOriginStandardHeadersVerification(request, response)).isTrue();
    assertThat(response.getErrorMessage()).isNull();
  }

  @Test
  public void shouldFallBackToTheRefererWhenTheOriginIsBlank() throws IOException {
    MockHttpServletRequest request = post(new MockHttpSession());
    request.addHeader("Origin", "  ");
    request.addHeader("Referer", "https://app.example.com:8443/camunda/app/cockpit");

    assertThat(filter.doSameOriginStandardHeadersVerification(request, response)).isTrue();
  }

  @Test
  public void shouldRejectARequestWithNeitherOriginNorReferer() throws IOException {
    boolean valid = filter.doSameOriginStandardHeadersVerification(post(new MockHttpSession()), response);

    assertThat(valid).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getErrorMessage()).contains("ORIGIN and REFERER request headers are not present");
  }

  @Test
  public void shouldRejectAnUnparsableOrigin() throws IOException {
    MockHttpServletRequest request = post(new MockHttpSession());
    request.addHeader("Origin", "not a url");

    assertThat(filter.doSameOriginStandardHeadersVerification(request, response)).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getErrorMessage()).contains("Could not parse ORIGIN/REFERER request header value: not a url");
  }

  @Test
  public void shouldRejectAnOriginWithAnUnknownProtocol() throws IOException {
    MockHttpServletRequest request = post(new MockHttpSession());
    request.addHeader("Origin", "nosuchscheme://app.example.com:8443");

    assertThat(filter.doSameOriginStandardHeadersVerification(request, response)).isFalse();
    assertThat(response.getErrorMessage()).contains("Could not parse ORIGIN/REFERER");
  }

  @Test
  public void shouldRejectAProtocolMismatch() throws IOException {
    assertOriginRejected("http://app.example.com:8443");
  }

  @Test
  public void shouldRejectAHostMismatch() throws IOException {
    assertOriginRejected("https://evil.example.com:8443");
  }

  @Test
  public void shouldRejectAPortMismatch() throws IOException {
    assertOriginRejected("https://app.example.com:9443");
  }

  protected void assertOriginRejected(String origin) throws IOException {
    MockHttpServletRequest request = post(new MockHttpSession());
    request.addHeader("Origin", origin);

    assertThat(filter.doSameOriginStandardHeadersVerification(request, response)).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getErrorMessage()).contains("Protocol/Host/Port does not fully match");
  }

  // token validation -------------------------------------------------------------------------

  @Test
  public void shouldDemandATokenHeaderAndInvalidateTheSession() throws IOException {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(CsrfConstants.CSRF_TOKEN_SESSION_ATTR_NAME, "tok");

    boolean valid = filter.doTokenValidation(post(session), response);

    assertThat(valid).isFalse();
    assertThat(session.isInvalid()).isTrue();
    assertThat(response.getHeader(CsrfConstants.CSRF_TOKEN_HEADER_NAME)).isEqualTo(CsrfConstants.CSRF_TOKEN_HEADER_REQUIRED);
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getErrorMessage()).contains("absent/empty");
  }

  @Test
  public void shouldRejectATokenThatDiffersFromTheSessionToken() throws IOException {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(CsrfConstants.CSRF_TOKEN_SESSION_ATTR_NAME, "tok");
    MockHttpServletRequest request = post(session);
    request.addHeader(CsrfConstants.CSRF_TOKEN_HEADER_NAME, "other");

    assertThat(filter.doTokenValidation(request, response)).isFalse();
    assertThat(session.isInvalid()).isTrue();
    assertThat(response.getErrorMessage()).contains("Invalid HTTP Header Token");
  }

  @Test
  public void shouldRejectATokenWhenTheSessionHasNone() throws IOException {
    MockHttpSession session = new MockHttpSession();
    MockHttpServletRequest request = post(session);
    request.addHeader(CsrfConstants.CSRF_TOKEN_HEADER_NAME, "tok");

    assertThat(filter.doTokenValidation(request, response)).isFalse();
    assertThat(response.getErrorMessage()).contains("Invalid HTTP Header Token");
  }

  @Test
  public void shouldAcceptAMatchingToken() throws IOException {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(CsrfConstants.CSRF_TOKEN_SESSION_ATTR_NAME, "tok");
    MockHttpServletRequest request = post(session);
    request.addHeader(CsrfConstants.CSRF_TOKEN_HEADER_NAME, "tok");

    assertThat(filter.doTokenValidation(request, response)).isTrue();
    assertThat(session.isInvalid()).isFalse();
  }

  @Test
  public void shouldUseTheConfiguredDenyStatusForTokenFailures() throws IOException {
    filter.setDenyStatus(401);

    filter.doTokenValidation(post(new MockHttpSession()), response);

    assertThat(response.getStatus()).isEqualTo(401);
  }

  // full chain -------------------------------------------------------------------------------

  @Test
  public void shouldNotReachTheChainWhenTheOriginIsWrong() throws IOException, ServletException {
    MockHttpServletRequest request = post(new MockHttpSession());
    request.addHeader("Origin", "https://evil.example.com:8443");
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  public void shouldNotReachTheChainWhenTheOriginIsRightButTheTokenMissing() throws IOException, ServletException {
    MockHttpServletRequest request = post(new MockHttpSession());
    request.addHeader("Origin", "https://app.example.com:8443");
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getErrorMessage()).contains("absent/empty");
  }

  @Test
  public void shouldReachTheChainWhenOriginAndTokenAreRight() throws IOException, ServletException {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(CsrfConstants.CSRF_TOKEN_SESSION_ATTR_NAME, "tok");
    MockHttpServletRequest request = post(session);
    request.addHeader("Origin", "https://app.example.com:8443");
    request.addHeader(CsrfConstants.CSRF_TOKEN_HEADER_NAME, "tok");
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  // session mutex ----------------------------------------------------------------------------

  @Test
  public void shouldIssueATokenOnlyOnceForAFetchRequest() throws IOException, ServletException {
    MockHttpSession session = new MockHttpSession();
    MockHttpServletRequest first = new MockHttpServletRequest("GET", "/camunda/app/cockpit/");
    first.setSession(session);

    filter.doFilter(first, response, new MockFilterChain());
    String token = (String) session.getAttribute(CsrfConstants.CSRF_TOKEN_SESSION_ATTR_NAME);
    assertThat(token).isNotBlank();
    assertThat(response.getHeader(CsrfConstants.CSRF_TOKEN_HEADER_NAME)).isEqualTo(token);

    MockHttpServletResponse second = new MockHttpServletResponse();
    MockHttpServletRequest again = new MockHttpServletRequest("GET", "/camunda/app/cockpit/");
    again.setSession(session);
    filter.doFilter(again, second, new MockFilterChain());

    assertThat(session.getAttribute(CsrfConstants.CSRF_TOKEN_SESSION_ATTR_NAME)).isEqualTo(token);
    assertThat(second.getHeader(CsrfConstants.CSRF_TOKEN_HEADER_NAME)).isNull();
  }

  @Test
  public void shouldSynchronizeOnTheSessionMutexAttributeWhenPresent() throws IOException {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(CsrfConstants.CSRF_SESSION_MUTEX, new Object());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/camunda/app/cockpit/");
    request.setSession(session);

    filter.setCSRFToken(request, response);

    assertThat(session.getAttribute(CsrfConstants.CSRF_TOKEN_SESSION_ATTR_NAME)).isNotNull();
  }

  @Test
  public void shouldRefuseToIssueATokenWithoutASession() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/camunda/app/cockpit/") {
      @Override
      public javax.servlet.http.HttpSession getSession() {
        return null;
      }
    };

    assertThatThrownBy(() -> filter.setCSRFToken(request, response))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("HttpSession is missing");
  }
}
