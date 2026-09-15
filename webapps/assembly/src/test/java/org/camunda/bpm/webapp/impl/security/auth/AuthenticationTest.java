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

import java.security.Principal;
import org.junit.Test;

/**
 * Covers {@link Authentication}, the pairing of an identity with a process engine that the webapps
 * pass around as a {@link Principal}.
 *
 * <p>Both fields are compared behind their own null guard, and the shared ANONYMOUS instance is
 * built with both of them null - so the null side of every guard is reachable and worth pinning.
 */
public class AuthenticationTest {

  @Test
  public void shouldCarryIdentityAndEngine() {
    Authentication authentication = new Authentication("ana", "default");

    assertThat(authentication.getIdentityId()).isEqualTo("ana");
    assertThat(authentication.getProcessEngineName()).isEqualTo("default");
  }

  /** Principal.getName is the identity id - that is what makes it usable as a Principal. */
  @Test
  public void shouldAnswerTheIdentityIdAsItsPrincipalName() {
    Principal principal = new Authentication("ana", "default");

    assertThat(principal.getName()).isEqualTo("ana");
  }

  @Test
  public void shouldOfferAnAnonymousAuthenticationWithNothingSet() {
    assertThat(Authentication.ANONYMOUS.getIdentityId()).isNull();
    assertThat(Authentication.ANONYMOUS.getProcessEngineName()).isNull();
    assertThat(Authentication.ANONYMOUS.getName()).isNull();
  }

  // equality ------------------------------------------------------------------------------------

  @Test
  public void shouldEqualItselfAndAnIdenticalAuthentication() {
    Authentication authentication = new Authentication("ana", "default");

    assertThat(authentication.equals(authentication)).isTrue();
    assertThat(authentication).isEqualTo(new Authentication("ana", "default"));
  }

  @Test
  public void shouldNotEqualNullOrAnotherType() {
    Authentication authentication = new Authentication("ana", "default");

    assertThat(authentication.equals(null)).isFalse();
    assertThat(authentication.equals("ana")).isFalse();
  }

  @Test
  public void shouldDifferOnEitherField() {
    Authentication authentication = new Authentication("ana", "default");

    assertThat(authentication.equals(new Authentication("budi", "default"))).isFalse();
    assertThat(authentication.equals(new Authentication("ana", "second"))).isFalse();
  }

  /** Each field guards for null on both sides, so null is driven in both directions. */
  @Test
  public void shouldCompareAgainstAuthenticationsCarryingNulls() {
    Authentication full = new Authentication("ana", "default");
    Authentication noIdentity = new Authentication(null, "default");
    Authentication noEngine = new Authentication("ana", null);

    assertThat(noIdentity).isEqualTo(new Authentication(null, "default"));
    assertThat(noEngine).isEqualTo(new Authentication("ana", null));

    assertThat(noIdentity.equals(full)).isFalse();
    assertThat(full.equals(noIdentity)).isFalse();
    assertThat(noEngine.equals(full)).isFalse();
    assertThat(full.equals(noEngine)).isFalse();

    assertThat(Authentication.ANONYMOUS).isEqualTo(new Authentication(null, null));
  }

  @Test
  public void shouldHashEqualAuthenticationsAlike() {
    assertThat(new Authentication("ana", "default").hashCode())
        .isEqualTo(new Authentication("ana", "default").hashCode());
    assertThat(new Authentication(null, null).hashCode())
        .isEqualTo(Authentication.ANONYMOUS.hashCode());
  }

  @Test
  public void shouldHashDifferentAuthenticationsApart() {
    assertThat(new Authentication("ana", "default").hashCode())
        .isNotEqualTo(new Authentication("budi", "default").hashCode());
  }
}
