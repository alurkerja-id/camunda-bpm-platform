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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.camunda.bpm.webapp.impl.security.filter.util.HttpSessionMutexListener;
import org.junit.Test;

/**
 * Covers the parts of {@link AuthenticationUtil} that do not need a live process engine: the
 * session helpers, and the two lookups that filter out null entries coming back from a query.
 */
public class AuthenticationUtilTest {

  // session helpers ------------------------------------------------------------------------------

  @Test
  public void shouldCreateAuthenticationsWhenTheSessionHasNone() {
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute("authenticatedUser")).thenReturn(null);

    Authentications authentications = AuthenticationUtil.getAuthsFromSession(session);

    assertThat(authentications).isNotNull();
    verify(session).setAttribute("authenticatedUser", authentications);
  }

  @Test
  public void shouldReuseAuthenticationsAlreadyInTheSession() {
    Authentications existing = new Authentications();
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute("authenticatedUser")).thenReturn(existing);

    assertThat(AuthenticationUtil.getAuthsFromSession(session)).isSameAs(existing);
    verify(session, never()).setAttribute(any(), any());
  }

  @Test
  public void shouldStoreAuthenticationsInTheSession() {
    Authentications authentications = new Authentications();
    HttpSession session = mock(HttpSession.class);

    AuthenticationUtil.updateSession(session, authentications);

    verify(session).setAttribute("authenticatedUser", authentications);
  }

  @Test
  public void shouldDoNothingWhenThereIsNoSessionToUpdate() {
    AuthenticationUtil.updateSession(null, new Authentications());
  }

  @Test
  public void shouldFallBackToTheSessionAsItsOwnMutex() {
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(HttpSessionMutexListener.AUTH_TIME_SESSION_MUTEX)).thenReturn(null);

    assertThat(AuthenticationUtil.getSessionMutex(session)).isSameAs(session);
  }

  @Test
  public void shouldUseTheStoredMutexWhenThereIsOne() {
    Object mutex = new Object();
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(HttpSessionMutexListener.AUTH_TIME_SESSION_MUTEX)).thenReturn(mutex);

    assertThat(AuthenticationUtil.getSessionMutex(session)).isSameAs(mutex);
  }

  /** The old session is thrown away and the authentications carried over to the new one. */
  @Test
  public void shouldMoveAuthenticationsToAFreshSession() {
    HttpSession oldSession = mock(HttpSession.class);
    HttpSession newSession = mock(HttpSession.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getSession()).thenReturn(oldSession);
    when(request.getSession(true)).thenReturn(newSession);
    when(oldSession.getAttribute("authenticatedUser")).thenReturn(new Authentications());

    UserAuthentication authentication = new UserAuthentication("ana", "default");
    AuthenticationUtil.revalidateSession(request, authentication);

    verify(oldSession).invalidate();
    verify(newSession).setAttribute(eqKey(), any());
  }

  @Test
  public void shouldOnlyInvalidateWhenThereIsNoNewAuthentication() {
    HttpSession oldSession = mock(HttpSession.class);
    HttpSession newSession = mock(HttpSession.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getSession()).thenReturn(oldSession);
    when(request.getSession(true)).thenReturn(newSession);
    when(oldSession.getAttribute("authenticatedUser")).thenReturn(new Authentications());

    AuthenticationUtil.revalidateSession(request, null);

    verify(oldSession).invalidate();
    verify(newSession, never()).setAttribute(any(), any());
  }

  // lookups -----------------------------------------------------------------------------------------

  @Test
  public void shouldListGroupIdsSkippingEmptyEntries() {
    // the mocked entries are built first: Mockito refuses stubbing nested inside a when(...) call
    List<Group> found = Arrays.asList(group("admin"), null, group(null));

    ProcessEngine engine = mock(ProcessEngine.class);
    IdentityService identityService = mock(IdentityService.class);
    GroupQuery query = mock(GroupQuery.class);
    when(engine.getIdentityService()).thenReturn(identityService);
    when(identityService.createGroupQuery()).thenReturn(query);
    when(query.groupMember("ana")).thenReturn(query);
    when(query.list()).thenReturn(found);

    List<String> groupIds = AuthenticationUtil.getGroupsOfUser(engine, "ana");

    assertThat(groupIds).containsExactly("admin");
  }

  @Test
  public void shouldListTenantIdsSkippingEmptyEntries() {
    List<Tenant> found = Arrays.asList(tenant("acme"), null, tenant(null));

    ProcessEngine engine = mock(ProcessEngine.class);
    IdentityService identityService = mock(IdentityService.class);
    TenantQuery query = mock(TenantQuery.class);
    when(engine.getIdentityService()).thenReturn(identityService);
    when(identityService.createTenantQuery()).thenReturn(query);
    when(query.userMember("ana")).thenReturn(query);
    when(query.includingGroupsOfUser(true)).thenReturn(query);
    when(query.list()).thenReturn(found);

    List<String> tenantIds = AuthenticationUtil.getTenantsOfUser(engine, "ana");

    assertThat(tenantIds).containsExactly("acme");
  }

  protected Group group(String id) {
    Group group = mock(Group.class);
    when(group.getId()).thenReturn(id);
    return group;
  }

  protected Tenant tenant(String id) {
    Tenant tenant = mock(Tenant.class);
    when(tenant.getId()).thenReturn(id);
    return tenant;
  }

  protected String eqKey() {
    return org.mockito.ArgumentMatchers.eq("authenticatedUser");
  }
}
