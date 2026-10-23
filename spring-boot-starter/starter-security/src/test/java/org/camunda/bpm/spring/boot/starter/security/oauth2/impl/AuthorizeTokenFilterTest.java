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
package org.camunda.bpm.spring.boot.starter.security.oauth2.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Covers {@link AuthorizeTokenFilter}: a session whose access token cannot be renewed must lose
 * its security context and HTTP session, while a valid or refreshed token leaves it untouched.
 * The request is always passed down the chain afterwards.
 */
public class AuthorizeTokenFilterTest {

  protected OAuth2AuthenticationToken authentication;
  protected MockHttpServletRequest request;
  protected MockHttpServletResponse response;
  protected MockFilterChain chain;
  protected AtomicInteger authorizeCalls;

  @Before
  public void setUp() {
    OAuth2User user = new DefaultOAuth2User(java.util.List.of(new SimpleGrantedAuthority("r")),
        java.util.Map.of("sub", "ana"), "sub");
    authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "kc");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    request = new MockHttpServletRequest();
    request.getSession(true);
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
    authorizeCalls = new AtomicInteger();
  }

  @After
  public void tearDown() {
    SecurityContextHolder.clearContext();
    ClockUtil.reset();
  }

  protected OAuth2AuthorizedClient clientExpiringAt(Instant expiry) {
    var registration = ClientRegistration.withRegistrationId("kc")
        .clientId("c").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://localhost/cb").authorizationUri("http://idp/auth").tokenUri("http://idp/token").build();
    var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "at",
        expiry == null ? null : expiry.minusSeconds(60), expiry);
    return new OAuth2AuthorizedClient(registration, "ana", accessToken);
  }

  protected AuthorizeTokenFilter filterReturning(OAuth2AuthorizedClient client) {
    OAuth2AuthorizedClientManager manager = req -> {
      authorizeCalls.incrementAndGet();
      return client;
    };
    return new AuthorizeTokenFilter(manager);
  }

  @Test
  public void shouldKeepSessionWhenAccessTokenIsValid() throws Exception {
    filterReturning(clientExpiringAt(Instant.now().plusSeconds(600))).doFilter(request, response, chain);

    assertThat(authorizeCalls).hasValue(1);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
    assertThat(request.getSession(false)).isNotNull();
    assertThat(((MockHttpServletRequest) chain.getRequest())).isSameAs(request);
  }

  @Test
  public void shouldClearContextWhenManagerReturnsNull() throws Exception {
    filterReturning(null).doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  public void shouldClearContextWhenAccessTokenExpired() throws Exception {
    filterReturning(clientExpiringAt(Instant.now().minusSeconds(5))).doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getSession(false)).isNull();
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  public void shouldClearContextWhenAuthorizationFails() throws Exception {
    OAuth2AuthorizedClientManager failing = req -> {
      throw new OAuth2AuthorizationException(new OAuth2Error("invalid_grant"));
    };

    new AuthorizeTokenFilter(failing).doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  public void shouldIgnoreRequestsWithoutOAuth2Authentication() throws Exception {
    var other = new TestingAuthenticationToken("bob", "pw", "r");
    SecurityContextHolder.getContext().setAuthentication(other);

    filterReturning(null).doFilter(request, response, chain);

    assertThat(authorizeCalls).hasValue(0);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(other);
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  public void shouldTreatTokenWithoutExpiryAsExpired() {
    var filter = filterReturning(null);

    assertThat(filter.hasTokenExpired(clientExpiringAt(null).getAccessToken())).isTrue();
    assertThat(filter.hasTokenExpired(clientExpiringAt(Instant.now().plusSeconds(60)).getAccessToken())).isFalse();
  }

  @Test
  public void shouldSurviveSessionInvalidationFailure() {
    var brokenRequest = new MockHttpServletRequest() {
      @Override
      public jakarta.servlet.http.HttpSession getSession() {
        throw new IllegalStateException("no session");
      }
    };

    filterReturning(null).clearContext(brokenRequest);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
