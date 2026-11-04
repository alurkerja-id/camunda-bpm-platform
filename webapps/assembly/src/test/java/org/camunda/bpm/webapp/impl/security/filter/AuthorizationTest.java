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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import javax.servlet.http.HttpServletResponse;
import org.camunda.bpm.webapp.impl.security.auth.Authentication;
import org.camunda.bpm.webapp.impl.security.auth.UserAuthentication;
import org.junit.Test;

/**
 * Covers {@link Authorization}: the verdict of a security filter rule plus the response headers
 * that tell the frontend who is logged in. Headers are attached defensively - every null identity
 * or engine is skipped - and only a {@link UserAuthentication} carries the authorized apps.
 */
public class AuthorizationTest {

  private final HttpServletResponse response = mock(HttpServletResponse.class);

  private static UserAuthentication user(String id, String engine, String... apps) {
    UserAuthentication auth = new UserAuthentication(id, engine);
    auth.setAuthorizedApps(new HashSet<>(Arrays.asList(apps)));
    return auth;
  }

  @Test
  public void shouldBuildGrantedAndDeniedVerdicts() {
    Authentication auth = new Authentication("ana", "default");

    assertThat(Authorization.granted(auth).isGranted()).isTrue();
    assertThat(Authorization.granted(auth).getAuthentication()).isSameAs(auth);
    assertThat(Authorization.denied(auth).isGranted()).isFalse();
    assertThat(Authorization.denied(auth).getAuthentication()).isSameAs(auth);
  }

  @Test
  public void shouldGrantUnlessTheAuthenticationIsNull() {
    Authentication auth = new Authentication("ana", "default");

    assertThat(Authorization.grantedUnlessNull(auth).isGranted()).isTrue();
    assertThat(Authorization.grantedUnlessNull(null).isGranted()).isFalse();
    assertThat(Authorization.grantedUnlessNull(null).getAuthentication()).isNull();
  }

  @Test
  public void shouldRememberTheApplicationItWasRestrictedTo() {
    Authorization authorization = Authorization.denied(new Authentication("ana", "default"));

    assertThat(authorization.getApplication()).isNull();
    assertThat(authorization.forApplication("cockpit")).isSameAs(authorization);
    assertThat(authorization.getApplication()).isEqualTo("cockpit");
  }

  @Test
  public void shouldTreatOnlyARealIdentityAsAuthenticated() {
    assertThat(Authorization.granted(new Authentication("ana", "default")).isAuthenticated()).isTrue();
    assertThat(Authorization.granted(Authentication.ANONYMOUS).isAuthenticated()).isFalse();
    assertThat(Authorization.denied(null).isAuthenticated()).isFalse();
  }

  // headers -------------------------------------------------------------------------------------

  @Test
  public void shouldAttachUserEngineAndAppsForAUserAuthentication() {
    Authorization.granted(user("ana", "default", "cockpit")).attachHeaders(response);

    verify(response).addHeader("X-Authorized-User", "ana");
    verify(response).addHeader("X-Authorized-Engine", "default");
    verify(response).addHeader("X-Authorized-Apps", "cockpit");
  }

  @Test
  public void shouldJoinMultipleAppsWithCommas() {
    UserAuthentication auth = new UserAuthentication("ana", "default");
    auth.setAuthorizedApps(new java.util.LinkedHashSet<>(Arrays.asList("cockpit", "admin", "tasklist")));

    Authorization.granted(auth).attachHeaders(response);

    verify(response).addHeader("X-Authorized-Apps", "cockpit,admin,tasklist");
  }

  @Test
  public void shouldSendAnEmptyAppsHeaderForAUserWithNoApps() {
    UserAuthentication auth = new UserAuthentication("ana", "default");
    auth.setAuthorizedApps(Collections.<String>emptySet());

    Authorization.granted(auth).attachHeaders(response);

    verify(response).addHeader("X-Authorized-Apps", "");
  }

  /** A plain Authentication has no app list, so only identity and engine are advertised. */
  @Test
  public void shouldNotAttachAppsForAPlainAuthentication() {
    Authorization.granted(new Authentication("ana", "default")).attachHeaders(response);

    verify(response).addHeader("X-Authorized-User", "ana");
    verify(response).addHeader("X-Authorized-Engine", "default");
    verify(response, never()).addHeader(org.mockito.ArgumentMatchers.eq("X-Authorized-Apps"), anyString());
  }

  @Test
  public void shouldSkipNullIdentityAndEngine() {
    Authorization.granted(Authentication.ANONYMOUS).attachHeaders(response);

    verifyNoInteractions(response);
  }

  @Test
  public void shouldSkipOnlyTheNullEngine() {
    Authorization.granted(new Authentication("ana", null)).attachHeaders(response);

    verify(response).addHeader("X-Authorized-User", "ana");
    verify(response, never()).addHeader(org.mockito.ArgumentMatchers.eq("X-Authorized-Engine"), anyString());
  }

  @Test
  public void shouldSkipOnlyTheNullIdentity() {
    Authorization.granted(new Authentication(null, "default")).attachHeaders(response);

    verify(response).addHeader("X-Authorized-Engine", "default");
    verify(response, never()).addHeader(org.mockito.ArgumentMatchers.eq("X-Authorized-User"), anyString());
  }

  @Test
  public void shouldAttachNothingWithoutAnAuthentication() {
    Authorization.denied(null).attachHeaders(response);

    verifyNoInteractions(response);
  }
}
