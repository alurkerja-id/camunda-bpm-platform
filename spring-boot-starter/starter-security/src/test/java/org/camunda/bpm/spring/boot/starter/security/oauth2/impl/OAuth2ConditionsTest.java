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

import java.util.Map;
import org.camunda.bpm.spring.boot.starter.security.oauth2.OAuth2Properties;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the two registration conditions that switch the OAuth2 integration on or off, and the
 * logout success handler that redirects to the identity provider.
 */
public class OAuth2ConditionsTest {

  protected ConditionContext contextWith(MockEnvironment env) {
    ConditionContext context = mock(ConditionContext.class);
    when(context.getEnvironment()).thenReturn(env);
    return context;
  }

  protected MockEnvironment configured() {
    return new MockEnvironment()
        .withProperty("spring.security.oauth2.client.registration.kc.client-id", "c")
        .withProperty("spring.security.oauth2.client.registration.gh.client-id", "d");
  }

  @Test
  public void shouldMatchWhenRegistrationsExist() {
    var outcome = new ClientsConfiguredCondition().getMatchOutcome(contextWith(configured()), mock(AnnotatedTypeMetadata.class));

    assertThat(outcome.isMatch()).isTrue();
    assertThat(outcome.getMessage()).contains("kc").contains("gh");
  }

  @Test
  public void shouldNotMatchWithoutRegistrations() {
    var outcome = new ClientsConfiguredCondition().getMatchOutcome(contextWith(new MockEnvironment()), mock(AnnotatedTypeMetadata.class));

    assertThat(outcome.isMatch()).isFalse();
  }

  @Test
  public void shouldInvertOutcomeForNotConfiguredCondition() {
    var meta = mock(AnnotatedTypeMetadata.class);

    assertThat(new ClientsNotConfiguredCondition().getMatchOutcome(contextWith(configured()), meta).isMatch()).isFalse();
    assertThat(new ClientsNotConfiguredCondition().getMatchOutcome(contextWith(new MockEnvironment()), meta).isMatch()).isTrue();
  }

  @Test
  public void shouldRedirectToIdentityProviderOnLogout() throws Exception {
    var registration = ClientRegistration.withRegistrationId("kc").clientId("c")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://localhost/cb").authorizationUri("http://idp/auth").tokenUri("http://idp/token")
        .providerConfigurationMetadata(Map.of("end_session_endpoint", "http://idp/logout")).build();
    var properties = new OAuth2Properties();
    properties.getSsoLogout().setPostLogoutRedirectUri("http://app/");
    var handler = new SsoLogoutSuccessHandler(new InMemoryClientRegistrationRepository(registration), properties);
    var response = new MockHttpServletResponse();

    var idToken = org.springframework.security.oauth2.core.oidc.OidcIdToken.withTokenValue("tok").subject("ana")
        .issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(60)).build();
    var principal = new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(
        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("r")), idToken);
    var authentication = new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
        principal, principal.getAuthorities(), "kc");

    handler.onLogoutSuccess(new MockHttpServletRequest(), response, authentication);

    assertThat(response.getRedirectedUrl()).startsWith("http://idp/logout").contains("id_token_hint=tok")
        .contains("post_logout_redirect_uri=http");
  }
}
