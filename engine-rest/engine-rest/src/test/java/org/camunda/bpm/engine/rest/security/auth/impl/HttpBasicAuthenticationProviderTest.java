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
package org.camunda.bpm.engine.rest.security.auth.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.junit.Test;

/**
 * Covers {@link HttpBasicAuthenticationProvider}: the Authorization header must be "Basic " plus
 * base64("user:password"); the password is checked against the engine's identity service, and only
 * the FIRST colon splits user from password (passwords may contain colons).
 */
public class HttpBasicAuthenticationProviderTest {

  private final HttpBasicAuthenticationProvider provider = new HttpBasicAuthenticationProvider();
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final ProcessEngine engine = mock(ProcessEngine.class);
  private final IdentityService identityService = mock(IdentityService.class);

  public HttpBasicAuthenticationProviderTest() {
    when(engine.getIdentityService()).thenReturn(identityService);
  }

  private void authorization(String header) {
    when(request.getHeader("Authorization")).thenReturn(header);
  }

  private static String basic(String credentials) {
    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void shouldAuthenticateValidCredentials() {
    when(identityService.checkPassword("demo", "secret")).thenReturn(true);
    authorization(basic("demo:secret"));

    AuthenticationResult result = provider.extractAuthenticatedUser(request, engine);

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(result.getAuthenticatedUser()).isEqualTo("demo");
  }

  @Test
  public void shouldRememberTheUserButNotAuthenticateOnAWrongPassword() {
    when(identityService.checkPassword("demo", "wrong")).thenReturn(false);
    authorization(basic("demo:wrong"));

    AuthenticationResult result = provider.extractAuthenticatedUser(request, engine);

    assertThat(result.isAuthenticated()).isFalse();
    assertThat(result.getAuthenticatedUser()).isEqualTo("demo");
  }

  @Test
  public void shouldSplitOnlyOnTheFirstColon() {
    when(identityService.checkPassword("demo", "pa:ss:word")).thenReturn(true);
    authorization(basic("demo:pa:ss:word"));

    AuthenticationResult result = provider.extractAuthenticatedUser(request, engine);

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(result.getAuthenticatedUser()).isEqualTo("demo");
  }

  @Test
  public void shouldAllowAnEmptyPassword() {
    when(identityService.checkPassword("demo", "")).thenReturn(false);
    authorization(basic("demo:"));

    AuthenticationResult result = provider.extractAuthenticatedUser(request, engine);

    assertThat(result.isAuthenticated()).isFalse();
    assertThat(result.getAuthenticatedUser()).isEqualTo("demo");
    verify(identityService).checkPassword("demo", "");
  }

  @Test
  public void shouldRejectCredentialsWithoutAColon() {
    authorization(basic("justausername"));

    AuthenticationResult result = provider.extractAuthenticatedUser(request, engine);

    assertThat(result.isAuthenticated()).isFalse();
    assertThat(result.getAuthenticatedUser()).isNull();
  }

  @Test
  public void shouldRejectAMissingHeader() {
    authorization(null);

    AuthenticationResult result = provider.extractAuthenticatedUser(request, engine);

    assertThat(result.isAuthenticated()).isFalse();
    assertThat(result.getAuthenticatedUser()).isNull();
  }

  @Test
  public void shouldRejectOtherAuthenticationSchemes() {
    authorization("Bearer abc.def.ghi");

    assertThat(provider.extractAuthenticatedUser(request, engine).isAuthenticated()).isFalse();
  }

  /** The scheme match is case-sensitive and requires the trailing space. */
  @Test
  public void shouldRequireTheExactBasicPrefix() {
    authorization(basic("demo:secret").replace("Basic ", "basic "));
    assertThat(provider.extractAuthenticatedUser(request, engine).isAuthenticated()).isFalse();

    authorization("Basic");
    assertThat(provider.extractAuthenticatedUser(request, engine).isAuthenticated()).isFalse();
  }

  @Test
  public void shouldChallengeWithTheEngineNameAsRealm() {
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(engine.getName()).thenReturn("default");

    provider.augmentResponseByAuthenticationChallenge(response, engine);

    verify(response).setHeader("WWW-Authenticate", "Basic realm=\"default\"");
  }
}
