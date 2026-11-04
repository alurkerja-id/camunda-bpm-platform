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

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.camunda.bpm.engine.authorization.Authorization;
import org.junit.Test;

/**
 * Covers {@link UserAuthentication}: the authorization data cached next to an {@link Authentication}
 * and the value equality built on top of it. equals/hashCode compare four nullable fields behind
 * their own guards, so every field is exercised on both its null and its non-null side.
 */
public class UserAuthenticationTest {

  private static UserAuthentication filled() {
    UserAuthentication auth = new UserAuthentication("ana", "default");
    auth.setGroupIds(Arrays.asList("g1", "g2"));
    auth.setTenantIds(Arrays.asList("t1"));
    auth.setAuthorizedApps(new HashSet<>(Arrays.asList("cockpit", "tasklist")));
    auth.setCacheValidationTime(new Date(1_000L));
    return auth;
  }

  @Test
  public void shouldCarryIdentityEngineAndStartWithoutAuthorizationData() {
    UserAuthentication auth = new UserAuthentication("ana", "default");

    assertThat(auth.getIdentityId()).isEqualTo("ana");
    assertThat(auth.getProcessEngineName()).isEqualTo("default");
    assertThat(auth.getGroupIds()).isNull();
    assertThat(auth.getTenantIds()).isNull();
    assertThat(auth.getAuthorizedApps()).isNull();
    assertThat(auth.getCacheValidationTime()).isNull();
  }

  @Test
  public void shouldReturnWhatTheSettersStored() {
    UserAuthentication auth = filled();

    assertThat(auth.getGroupIds()).containsExactly("g1", "g2");
    assertThat(auth.getTenantIds()).containsExactly("t1");
    assertThat(auth.getAuthorizedApps()).containsExactlyInAnyOrder("cockpit", "tasklist");
    assertThat(auth.getCacheValidationTime()).isEqualTo(new Date(1_000L));
  }

  // authorized apps -----------------------------------------------------------------------------

  @Test
  public void shouldBeAuthorizedOnlyForListedApps() {
    UserAuthentication auth = filled();

    assertThat(auth.isAuthorizedForApp("cockpit")).isTrue();
    assertThat(auth.isAuthorizedForApp("admin")).isFalse();
  }

  @Test
  public void shouldBeAuthorizedForEveryAppWithTheWildcardResource() {
    UserAuthentication auth = new UserAuthentication("ana", "default");
    auth.setAuthorizedApps(Collections.singleton(Authorization.ANY));

    assertThat(auth.isAuthorizedForApp("cockpit")).isTrue();
    assertThat(auth.isAuthorizedForApp("anything-else")).isTrue();
  }

  @Test
  public void shouldBeAuthorizedForNothingWithAnEmptyAppSet() {
    UserAuthentication auth = new UserAuthentication("ana", "default");
    auth.setAuthorizedApps(Collections.<String>emptySet());

    assertThat(auth.isAuthorizedForApp("cockpit")).isFalse();
  }

  // equality ------------------------------------------------------------------------------------

  @Test
  public void shouldEqualItselfAndAnIdenticalCopyWithMatchingHashCode() {
    UserAuthentication auth = filled();
    UserAuthentication copy = filled();

    assertThat(auth.equals(auth)).isTrue();
    assertThat(auth).isEqualTo(copy);
    assertThat(auth.hashCode()).isEqualTo(copy.hashCode());
  }

  @Test
  public void shouldEqualAnotherAuthenticationWhereEveryFieldIsStillNull() {
    UserAuthentication a = new UserAuthentication("ana", "default");
    UserAuthentication b = new UserAuthentication("ana", "default");

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  public void shouldNotEqualNullOrAnotherType() {
    UserAuthentication auth = filled();

    assertThat(auth.equals(null)).isFalse();
    assertThat(auth.equals("ana")).isFalse();
  }

  /** The plain base class has the same identity and engine but lacks the authorization data. */
  @Test
  public void shouldNotEqualThePlainAuthenticationWithTheSameIdentity() {
    UserAuthentication auth = new UserAuthentication("ana", "default");

    assertThat(auth.equals(new Authentication("ana", "default"))).isFalse();
  }

  @Test
  public void shouldDifferWhenIdentityOrEngineDiffer() {
    UserAuthentication auth = filled();

    UserAuthentication otherUser = filled();
    UserAuthentication other = new UserAuthentication("budi", "default");
    other.setGroupIds(otherUser.getGroupIds());
    other.setTenantIds(otherUser.getTenantIds());
    other.setAuthorizedApps(otherUser.getAuthorizedApps());
    other.setCacheValidationTime(otherUser.getCacheValidationTime());

    assertThat(auth.equals(other)).isFalse();
  }

  @Test
  public void shouldDifferWhenGroupsDifferOnEitherSide() {
    UserAuthentication auth = filled();

    UserAuthentication otherGroups = filled();
    otherGroups.setGroupIds(Arrays.asList("g9"));
    UserAuthentication noGroups = filled();
    noGroups.setGroupIds(null);

    assertThat(auth.equals(otherGroups)).isFalse();
    assertThat(auth.equals(noGroups)).isFalse();
    assertThat(noGroups.equals(auth)).isFalse();
    assertThat(auth.hashCode()).isNotEqualTo(noGroups.hashCode());
  }

  @Test
  public void shouldDifferWhenTenantsDifferOnEitherSide() {
    UserAuthentication auth = filled();

    UserAuthentication otherTenants = filled();
    otherTenants.setTenantIds(Arrays.asList("t9"));
    UserAuthentication noTenants = filled();
    noTenants.setTenantIds(null);

    assertThat(auth.equals(otherTenants)).isFalse();
    assertThat(auth.equals(noTenants)).isFalse();
    assertThat(noTenants.equals(auth)).isFalse();
    assertThat(auth.hashCode()).isNotEqualTo(noTenants.hashCode());
  }

  @Test
  public void shouldDifferWhenAuthorizedAppsDifferOnEitherSide() {
    UserAuthentication auth = filled();

    Set<String> apps = new HashSet<>(Arrays.asList("admin"));
    UserAuthentication otherApps = filled();
    otherApps.setAuthorizedApps(apps);
    UserAuthentication noApps = filled();
    noApps.setAuthorizedApps(null);

    assertThat(auth.equals(otherApps)).isFalse();
    assertThat(auth.equals(noApps)).isFalse();
    assertThat(noApps.equals(auth)).isFalse();
    assertThat(auth.hashCode()).isNotEqualTo(noApps.hashCode());
  }

  @Test
  public void shouldDifferWhenCacheValidationTimeDiffersOnEitherSide() {
    UserAuthentication auth = filled();

    UserAuthentication otherTime = filled();
    otherTime.setCacheValidationTime(new Date(2_000L));
    UserAuthentication noTime = filled();
    noTime.setCacheValidationTime(null);

    assertThat(auth.equals(otherTime)).isFalse();
    assertThat(auth.equals(noTime)).isFalse();
    assertThat(noTime.equals(auth)).isFalse();
    assertThat(auth.hashCode()).isNotEqualTo(noTime.hashCode());
  }

  @Test
  public void shouldAcceptListsAsGroupAndTenantViews() {
    List<String> groups = Arrays.asList("g1");
    UserAuthentication auth = new UserAuthentication("ana", "default");
    auth.setGroupIds(groups);
    auth.setTenantIds(groups);

    assertThat(auth.getGroupIds()).isSameAs(groups);
    assertThat(auth.getTenantIds()).isSameAs(groups);
  }
}
