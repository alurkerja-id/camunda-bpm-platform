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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Response;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.junit.Test;

/**
 * Covers the parts of {@link UserAuthenticationResource} that stand on their own: the three
 * canned error responses, and the two lookups that flatten a query result into a list of ids.
 *
 * <p>Login and logout need a registered process engine and the current Authentications, so they
 * stay with the integration tests.
 */
public class UserAuthenticationResourceTest {

  protected final UserAuthenticationResource resource = new UserAuthenticationResource();

  // canned responses -------------------------------------------------------------------------

  @Test
  public void shouldAnswerWithTheExpectedStatusCodes() {
    assertThat(resource.unauthorized().getStatus())
        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
    assertThat(resource.forbidden().getStatus())
        .isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(resource.notFound().getStatus())
        .isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void shouldCarryNoEntityOnTheErrorResponses() {
    assertThat(resource.unauthorized().getEntity()).isNull();
    assertThat(resource.forbidden().getEntity()).isNull();
    assertThat(resource.notFound().getEntity()).isNull();
  }

  // lookups -----------------------------------------------------------------------------------

  @Test
  public void shouldListTheGroupIdsOfAUser() {
    // the mocked entries are built first: Mockito refuses stubbing nested inside a when(...) call
    List<Group> found = Arrays.asList(group("admin"), group("sales"));

    assertThat(resource.getGroupsOfUser(engineReturning(found, null), "ana"))
        .containsExactly("admin", "sales");
  }

  @Test
  public void shouldAnswerAnEmptyListWhenTheUserHasNoGroups() {
    assertThat(resource.getGroupsOfUser(engineReturning(Collections.<Group>emptyList(), null),
        "ana")).isEmpty();
  }

  @Test
  public void shouldListTheTenantIdsOfAUser() {
    List<Tenant> found = Arrays.asList(tenant("acme"), tenant("globex"));

    assertThat(resource.getTenantsOfUser(engineReturning(null, found), "ana"))
        .containsExactly("acme", "globex");
  }

  @Test
  public void shouldAnswerAnEmptyListWhenTheUserBelongsToNoTenant() {
    assertThat(resource.getTenantsOfUser(engineReturning(null, Collections.<Tenant>emptyList()),
        "ana")).isEmpty();
  }

  protected ProcessEngine engineReturning(List<Group> groups, List<Tenant> tenants) {
    ProcessEngine engine = mock(ProcessEngine.class);
    IdentityService identityService = mock(IdentityService.class);
    when(engine.getIdentityService()).thenReturn(identityService);

    if (groups != null) {
      GroupQuery groupQuery = mock(GroupQuery.class);
      when(identityService.createGroupQuery()).thenReturn(groupQuery);
      when(groupQuery.groupMember("ana")).thenReturn(groupQuery);
      when(groupQuery.list()).thenReturn(groups);
    }

    if (tenants != null) {
      TenantQuery tenantQuery = mock(TenantQuery.class);
      when(identityService.createTenantQuery()).thenReturn(tenantQuery);
      when(tenantQuery.userMember("ana")).thenReturn(tenantQuery);
      when(tenantQuery.includingGroupsOfUser(true)).thenReturn(tenantQuery);
      when(tenantQuery.list()).thenReturn(tenants);
    }

    return engine;
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
}
