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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.identity.IdentityProviderException;
import org.camunda.bpm.engine.impl.persistence.entity.UserEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * Covers {@link OAuth2IdentityProvider} while a Spring Security user is authenticated: identity is
 * served from the security context, filters that cannot be honoured are refused and every write
 * operation is rejected, since the identity data lives in the external provider.
 */
public class OAuth2IdentityProviderAuthenticatedTest {

  protected OAuth2IdentityProvider provider;

  @Before
  public void setUp() {
    provider = new OAuth2IdentityProvider();
    var idToken = OidcIdToken.withTokenValue("t").subject("ana").issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("given_name", "Ana").claim("family_name", "Lopez").claim("email", "ana@example.org").build();
    var principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("admins"), new SimpleGrantedAuthority("devs")),
        idToken);
    SecurityContextHolder.getContext().setAuthentication(
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kc"));
  }

  @After
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  protected OAuth2IdentityProvider.OAuth2UserQuery userQuery() {
    return (OAuth2IdentityProvider.OAuth2UserQuery) provider.createUserQuery();
  }

  protected OAuth2IdentityProvider.OAuth2GroupQuery groupQuery() {
    return (OAuth2IdentityProvider.OAuth2GroupQuery) provider.createGroupQuery();
  }

  protected List<User> users(org.camunda.bpm.engine.identity.UserQuery query) {
    return ((OAuth2IdentityProvider.OAuth2UserQuery) query).executeList(null, null);
  }

  protected List<Group> groups(org.camunda.bpm.engine.identity.GroupQuery query) {
    return ((OAuth2IdentityProvider.OAuth2GroupQuery) query).executeList(null, null);
  }

  @Test
  public void shouldFindOnlyTheAuthenticatedUserById() {
    UserEntity user = provider.findUserById("ana");

    assertThat(user.getId()).isEqualTo("ana");
    assertThat(user.getFirstName()).isEqualTo("Ana");
    assertThat(user.getLastName()).isEqualTo("Lopez");
    assertThat(user.getEmail()).isEqualTo("ana@example.org");
    assertThat(provider.findUserById("someone-else")).isNull();
  }

  @Test
  public void shouldLeaveProfileEmptyWhenPrincipalIsNotOidc() {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", "pw", "r1"));

    UserEntity user = provider.findUserById("bob");

    assertThat(user.getId()).isEqualTo("bob");
    assertThat(user.getFirstName()).isNull();
    assertThat(user.getEmail()).isNull();
  }

  @Test
  public void shouldTreatUnauthenticatedTokenAsNoSecurityContext() {
    var token = new TestingAuthenticationToken("bob", "pw");
    token.setAuthenticated(false);
    SecurityContextHolder.getContext().setAuthentication(token);

    assertThat(provider.springSecurityAuthentication()).isFalse();
  }

  @Test
  public void shouldCreateOAuth2QueriesWhenAuthenticated() {
    assertThat(provider.createUserQuery()).isInstanceOf(OAuth2IdentityProvider.OAuth2UserQuery.class);
    assertThat(provider.createUserQuery(null)).isInstanceOf(OAuth2IdentityProvider.OAuth2UserQuery.class);
    assertThat(provider.createGroupQuery()).isInstanceOf(OAuth2IdentityProvider.OAuth2GroupQuery.class);
    assertThat(provider.createGroupQuery(null)).isInstanceOf(OAuth2IdentityProvider.OAuth2GroupQuery.class);
    assertThat(provider.createTenantQuery()).isInstanceOf(OAuth2IdentityProvider.OAuth2TenantQuery.class);
    assertThat(provider.createTenantQuery(null)).isInstanceOf(OAuth2IdentityProvider.OAuth2TenantQuery.class);
  }

  @Test
  public void shouldRefuseNativeUserQuery() {
    assertThatThrownBy(() -> provider.createNativeUserQuery())
        .isInstanceOf(IdentityProviderException.class).hasMessageContaining("filter is not supported");
  }

  @Test
  public void shouldNeverAcceptPasswordsForOAuth2Users() {
    assertThat(provider.checkPassword("ana", "secret")).isFalse();
  }

  @Test
  public void shouldHaveNoTenants() {
    var query = (OAuth2IdentityProvider.OAuth2TenantQuery) provider.createTenantQuery();

    assertThat(provider.findTenantById("t1")).isNull();
    assertThat(query.executeCount(null)).isZero();
    assertThat(query.executeList(null, null)).isEmpty();
  }

  @Test
  public void shouldSkipFlushAndCloseWithoutTouchingTheDatabase() {
    provider.flush();
    provider.close();
  }

  @Test
  public void shouldCountExactlyOneUser() {
    assertThat(userQuery().executeCount(null)).isEqualTo(1);
  }

  @Test
  public void shouldListAuthenticatedUserWhenNoFilterIsSet() {
    List<User> users = userQuery().executeList(null, null);

    assertThat(users).extracting(User::getId).containsExactly("ana");
  }

  @Test
  public void shouldFilterUsersByIdAndIds() {
    assertThat(users(userQuery().userId("ana"))).hasSize(1);
    assertThat(users(userQuery().userId("zed"))).isEmpty();
    assertThat(users(userQuery().userIdIn("zed", "ana"))).hasSize(1);
    assertThat(users(userQuery().userIdIn("zed", "yan"))).isEmpty();
  }

  @Test
  public void shouldFilterUsersByExactAttributes() {
    assertThat(users(userQuery().userFirstName("Ana"))).hasSize(1);
    assertThat(users(userQuery().userFirstName("Bea"))).isEmpty();
    assertThat(users(userQuery().userLastName("Lopez"))).hasSize(1);
    assertThat(users(userQuery().userLastName("Diaz"))).isEmpty();
    assertThat(users(userQuery().userEmail("ana@example.org"))).hasSize(1);
    assertThat(users(userQuery().userEmail("x@example.org"))).isEmpty();
  }

  @Test
  public void shouldFilterUsersByLikeIgnoringCaseAndWildcards() {
    assertThat(users(userQuery().userFirstNameLike("%AN%"))).hasSize(1);
    assertThat(users(userQuery().userFirstNameLike("%zz%"))).isEmpty();
    assertThat(users(userQuery().userLastNameLike("%OPE%"))).hasSize(1);
    assertThat(users(userQuery().userLastNameLike("%zz%"))).isEmpty();
    assertThat(users(userQuery().userEmailLike("%EXAMPLE%"))).hasSize(1);
    assertThat(users(userQuery().userEmailLike("%nowhere%"))).isEmpty();
  }

  @Test
  public void shouldFilterUsersByGroupMembership() {
    assertThat(users(userQuery().memberOfGroup("admins"))).hasSize(1);
    assertThat(users(userQuery().memberOfGroup("ops"))).isEmpty();
  }

  @Test
  public void shouldRefuseUserQueryByTenant() {
    assertThatThrownBy(() -> users(userQuery().memberOfTenant("t1")))
        .isInstanceOf(IdentityProviderException.class);
  }

  @Test
  public void shouldListUsersEmptyWithoutAuthentication() {
    SecurityContextHolder.clearContext();

    assertThat(new OAuth2IdentityProvider.OAuth2UserQuery().executeList(null, null)).isEmpty();
    assertThat(OAuth2IdentityProvider.transformUser()).isNull();
  }

  @Test
  public void shouldTurnAuthoritiesIntoGroups() {
    List<Group> groups = OAuth2IdentityProvider.transformGroups();

    assertThat(groups).extracting(Group::getId).containsExactly("admins", "devs");
    assertThat(groups).extracting(Group::getName).containsExactly("admins", "devs");
    SecurityContextHolder.clearContext();
    assertThat(OAuth2IdentityProvider.transformGroups()).isEmpty();
  }

  @Test
  public void shouldFindGroupByIdFromAuthorities() {
    assertThat(provider.findGroupById("devs").getId()).isEqualTo("devs");
    assertThat(provider.findGroupById("ops")).isNull();
  }

  @Test
  public void shouldCountAndListGroups() {
    assertThat(groupQuery().executeCount(null)).isEqualTo(2);
    assertThat(groupQuery().executeList(null, null)).hasSize(2);
  }

  @Test
  public void shouldFilterGroups() {
    assertThat(groups(groupQuery().groupId("admins"))).hasSize(1);
    assertThat(groups(groupQuery().groupId("ops"))).isEmpty();
    assertThat(groups(groupQuery().groupIdIn("devs", "ops"))).hasSize(1);
    assertThat(groups(groupQuery().groupIdIn("ops"))).isEmpty();
    assertThat(groups(groupQuery().groupName("devs"))).hasSize(1);
    assertThat(groups(groupQuery().groupName("ops"))).isEmpty();
    assertThat(groups(groupQuery().groupNameLike("%DEV%"))).hasSize(1);
    assertThat(groups(groupQuery().groupNameLike("%ops%"))).isEmpty();
    assertThat(groups(groupQuery().groupMember("ana"))).hasSize(2);
    assertThat(groups(groupQuery().groupMember("other"))).isEmpty();
  }

  @Test
  public void shouldRefuseGroupQueryByTypeOrTenant() {
    assertThatThrownBy(() -> groups(groupQuery().groupType("WORKFLOW")))
        .isInstanceOf(IdentityProviderException.class);
    assertThatThrownBy(() -> groups(groupQuery().memberOfTenant("t1")))
        .isInstanceOf(IdentityProviderException.class);
  }

  @Test
  public void shouldRejectEveryWriteOperation() {
    var tenant = new org.camunda.bpm.engine.impl.persistence.entity.TenantEntity("t1");
    var group = new org.camunda.bpm.engine.impl.persistence.entity.GroupEntity("g1");
    var user = new UserEntity("u1");
    List<org.junit.function.ThrowingRunnable> writes = List.of(
        () -> provider.createNewUser("u1"),
        () -> provider.saveUser(user),
        () -> provider.deleteUser("u1"),
        () -> provider.unlockUser("u1"),
        () -> provider.createNewGroup("g1"),
        () -> provider.saveGroup(group),
        () -> provider.deleteGroup("g1"),
        () -> provider.createNewTenant("t1"),
        () -> provider.saveTenant(tenant),
        () -> provider.deleteTenant("t1"),
        () -> provider.createMembership("u1", "g1"),
        () -> provider.deleteMembership("u1", "g1"),
        () -> provider.createTenantUserMembership("t1", "u1"),
        () -> provider.createTenantGroupMembership("t1", "g1"),
        () -> provider.deleteTenantUserMembership("t1", "u1"),
        () -> provider.deleteTenantGroupMembership("t1", "g1"));

    for (var write : writes) {
      assertThatThrownBy(write::run).isInstanceOf(IdentityProviderException.class)
          .hasMessageContaining("operation is not supported");
    }
    assertThat(writes).hasSize(16);
  }

  @Test
  public void shouldMatchNullOrContainsIgnoreCase() {
    assertThat(OAuth2IdentityProvider.nullOrContainsIgnoreCase(null, "x")).isTrue();
    assertThat(OAuth2IdentityProvider.nullOrContainsIgnoreCase("x", null)).isTrue();
    assertThat(OAuth2IdentityProvider.nullOrContainsIgnoreCase("%B%", "abc")).isTrue();
    assertThat(OAuth2IdentityProvider.nullOrContainsIgnoreCase("%z%", "abc")).isFalse();
  }
}
