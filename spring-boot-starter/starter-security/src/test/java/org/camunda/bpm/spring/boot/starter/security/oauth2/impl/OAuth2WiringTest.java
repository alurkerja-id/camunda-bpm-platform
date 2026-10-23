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

import java.util.List;
import java.util.Map;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.plugin.SsoLogoutPluginConstants;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.plugin.admin.SsoLogoutAdminPlugin;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.plugin.admin.SsoLogoutAdminPluginRootResource;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.plugin.cockpit.SsoLogoutCockpitPlugin;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.plugin.cockpit.SsoLogoutCockpitPluginRootResource;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.plugin.tasklist.SsoLogoutTasklistPlugin;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.plugin.tasklist.SsoLogoutTasklistPluginRootResource;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.plugin.welcome.SsoLogoutWelcomePlugin;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.plugin.welcome.SsoLogoutWelcomePluginRootResource;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.identity.WritableIdentityProvider;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/**
 * Covers the small wiring classes of the OAuth2 integration: the authentication provider used by
 * the Camunda web apps, the identity provider plugin/factory and the SSO logout plugins that
 * expose the logout button in each web app.
 */
public class OAuth2WiringTest {

  @After
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  protected AuthenticationResult extract() {
    return new OAuth2AuthenticationProvider().extractAuthenticatedUser(new MockHttpServletRequest(), null);
  }

  protected OAuth2AuthenticationToken oauth2Token(String nameAttribute, String value) {
    var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("r")), Map.of(nameAttribute, value), nameAttribute);
    return new OAuth2AuthenticationToken(user, user.getAuthorities(), "kc");
  }

  @Test
  public void shouldAuthenticateOAuth2UserByName() {
    SecurityContextHolder.getContext().setAuthentication(oauth2Token("sub", "ana"));

    AuthenticationResult result = extract();

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(result.getAuthenticatedUser()).isEqualTo("ana");
  }

  @Test
  public void shouldRefuseWithoutAuthentication() {
    assertThat(extract().isAuthenticated()).isFalse();
  }

  @Test
  public void shouldRefuseNonOAuth2Authentication() {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", "pw", "r"));

    assertThat(extract().isAuthenticated()).isFalse();
  }

  @Test
  public void shouldRefuseEmptyUserName() {
    SecurityContextHolder.getContext().setAuthentication(oauth2Token("sub", ""));

    assertThat(extract().isAuthenticated()).isFalse();
  }

  @Test
  public void shouldRegisterIdentityProviderFactoryOnPreInit() {
    var configuration = new StandaloneInMemProcessEngineConfiguration();

    new OAuth2IdentityProviderPlugin().preInit(configuration);

    assertThat(configuration.getIdentityProviderSessionFactory()).isInstanceOf(OAuth2IdentityProviderFactory.class);
  }

  @Test
  public void shouldOpenOAuth2IdentityProviderSessions() {
    var factory = new OAuth2IdentityProviderFactory();

    assertThat(factory.getSessionType()).isEqualTo(WritableIdentityProvider.class);
    assertThat(factory.openSession()).isInstanceOf(OAuth2IdentityProvider.class);
  }

  @Test
  public void shouldExposeLogoutPluginOnEveryWebapp() {
    assertThat(new SsoLogoutAdminPlugin().getId()).isEqualTo(SsoLogoutPluginConstants.ID);
    assertThat(new SsoLogoutAdminPlugin().getResourceClasses()).containsExactly(SsoLogoutAdminPluginRootResource.class);
    assertThat(new SsoLogoutCockpitPlugin().getId()).isEqualTo(SsoLogoutPluginConstants.ID);
    assertThat(new SsoLogoutCockpitPlugin().getResourceClasses()).containsExactly(SsoLogoutCockpitPluginRootResource.class);
    assertThat(new SsoLogoutTasklistPlugin().getId()).isEqualTo(SsoLogoutPluginConstants.ID);
    assertThat(new SsoLogoutTasklistPlugin().getResourceClasses()).containsExactly(SsoLogoutTasklistPluginRootResource.class);
    assertThat(new SsoLogoutWelcomePlugin().getId()).isEqualTo(SsoLogoutPluginConstants.ID);
    assertThat(new SsoLogoutWelcomePlugin().getResourceClasses()).containsExactly(SsoLogoutWelcomePluginRootResource.class);
  }

  @Test
  public void shouldBuildPluginRootResources() {
    assertThat(new SsoLogoutAdminPluginRootResource()).isNotNull();
    assertThat(new SsoLogoutCockpitPluginRootResource()).isNotNull();
    assertThat(new SsoLogoutTasklistPluginRootResource()).isNotNull();
    assertThat(new SsoLogoutWelcomePluginRootResource()).isNotNull();
  }
}
