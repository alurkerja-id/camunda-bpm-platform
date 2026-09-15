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
package org.camunda.bpm.spring.boot.starter.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.spring.boot.starter.security.oauth2.OAuth2Properties.OAuth2IdentityProviderProperties;
import org.camunda.bpm.spring.boot.starter.security.oauth2.OAuth2Properties.OAuth2SSOLogoutProperties;
import org.junit.Test;

/**
 * Pins the defaults of {@link OAuth2Properties}, which decide behaviour whenever an application
 * configures nothing: SSO logout stays off, the identity provider stays on, and group names are
 * split on a comma.
 */
public class OAuth2PropertiesTest {

  @Test
  public void shouldStartWithBothSectionsInPlace() {
    OAuth2Properties properties = new OAuth2Properties();

    assertThat(properties.getSsoLogout()).isNotNull();
    assertThat(properties.getIdentityProvider()).isNotNull();
  }

  @Test
  public void shouldKeepSsoLogoutOffByDefault() {
    OAuth2SSOLogoutProperties ssoLogout = new OAuth2Properties().getSsoLogout();

    assertThat(ssoLogout.isEnabled()).isFalse();
    assertThat(ssoLogout.getPostLogoutRedirectUri()).isEqualTo("{baseUrl}");
  }

  @Test
  public void shouldKeepTheIdentityProviderOnByDefault() {
    OAuth2IdentityProviderProperties identityProvider =
        new OAuth2Properties().getIdentityProvider();

    assertThat(identityProvider.isEnabled()).isTrue();
    assertThat(identityProvider.getGroupNameAttribute()).isNull();
    assertThat(identityProvider.getGroupNameDelimiter()).isEqualTo(",");
  }

  @Test
  public void shouldAcceptSsoLogoutSettings() {
    OAuth2SSOLogoutProperties ssoLogout = new OAuth2SSOLogoutProperties();

    ssoLogout.setEnabled(true);
    ssoLogout.setPostLogoutRedirectUri("https://example.com/bye");

    assertThat(ssoLogout.isEnabled()).isTrue();
    assertThat(ssoLogout.getPostLogoutRedirectUri()).isEqualTo("https://example.com/bye");
  }

  @Test
  public void shouldAcceptIdentityProviderSettings() {
    OAuth2IdentityProviderProperties identityProvider = new OAuth2IdentityProviderProperties();

    identityProvider.setEnabled(false);
    identityProvider.setGroupNameAttribute("groups");
    identityProvider.setGroupNameDelimiter(";");

    assertThat(identityProvider.isEnabled()).isFalse();
    assertThat(identityProvider.getGroupNameAttribute()).isEqualTo("groups");
    assertThat(identityProvider.getGroupNameDelimiter()).isEqualTo(";");
  }

  @Test
  public void shouldAllowReplacingWholeSections() {
    OAuth2Properties properties = new OAuth2Properties();
    OAuth2SSOLogoutProperties ssoLogout = new OAuth2SSOLogoutProperties();
    OAuth2IdentityProviderProperties identityProvider = new OAuth2IdentityProviderProperties();

    properties.setSsoLogout(ssoLogout);
    properties.setIdentityProvider(identityProvider);

    assertThat(properties.getSsoLogout()).isSameAs(ssoLogout);
    assertThat(properties.getIdentityProvider()).isSameAs(identityProvider);
  }

  @Test
  public void shouldSitUnderTheCamundaPrefix() {
    assertThat(OAuth2Properties.PREFIX).isEqualTo("camunda.bpm.oauth2");
  }
}
