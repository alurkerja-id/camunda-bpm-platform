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
package org.camunda.bpm.admin.impl.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.AuthorizationQuery;
import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.UserQuery;
import org.junit.Before;
import org.junit.Test;

/**
 * Covers the guards of {@link SetupResource}: setup is a one-time action, so it must refuse to run
 * once an administrator exists or the identity service is read-only, and creating the admin group
 * must not duplicate what is already there.
 */
public class SetupResourceTest {

  protected SetupResource resource;
  protected ProcessEngine engine;
  protected IdentityService identityService;
  protected AuthorizationService authorizationService;

  @Before
  public void setUp() {
    resource = new SetupResource();
    identityService = mock(IdentityService.class);
    authorizationService = mock(AuthorizationService.class);
    engine = mock(ProcessEngine.class);
    when(engine.getIdentityService()).thenReturn(identityService);
    when(engine.getAuthorizationService()).thenReturn(authorizationService);
  }

  protected void withAdministrators(long count) {
    UserQuery userQuery = mock(UserQuery.class);
    when(identityService.createUserQuery()).thenReturn(userQuery);
    when(userQuery.memberOfGroup(Groups.CAMUNDA_ADMIN)).thenReturn(userQuery);
    when(userQuery.count()).thenReturn(count);
  }

  protected void withAdminGroups(long count) {
    GroupQuery groupQuery = mock(GroupQuery.class);
    when(identityService.createGroupQuery()).thenReturn(groupQuery);
    when(groupQuery.groupId(Groups.CAMUNDA_ADMIN)).thenReturn(groupQuery);
    when(groupQuery.count()).thenReturn(count);
  }

  protected void withExistingAuthorizations(long count) {
    AuthorizationQuery authorizationQuery = mock(AuthorizationQuery.class);
    when(authorizationService.createAuthorizationQuery()).thenReturn(authorizationQuery);
    when(authorizationQuery.groupIdIn(Groups.CAMUNDA_ADMIN)).thenReturn(authorizationQuery);
    when(authorizationQuery.resourceType(any(org.camunda.bpm.engine.authorization.Resource.class)))
        .thenReturn(authorizationQuery);
    when(authorizationQuery.resourceId(any(String.class))).thenReturn(authorizationQuery);
    when(authorizationQuery.count()).thenReturn(count);
  }

  // the setup guard ------------------------------------------------------------------------------

  @Test
  public void shouldAllowSetupOnAFreshEngine() {
    when(identityService.isReadOnly()).thenReturn(false);
    withAdministrators(0);

    resource.ensureSetupAvailable(engine);
  }

  @Test
  public void shouldRefuseSetupOnceAnAdministratorExists() {
    when(identityService.isReadOnly()).thenReturn(false);
    withAdministrators(1);

    assertThatThrownBy(() -> resource.ensureSetupAvailable(engine))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  public void shouldRefuseSetupOnAReadOnlyIdentityService() {
    when(identityService.isReadOnly()).thenReturn(true);

    assertThatThrownBy(() -> resource.ensureSetupAvailable(engine))
        .isInstanceOf(RuntimeException.class);
  }

  // the admin group ---------------------------------------------------------------------------------

  @Test
  public void shouldCreateTheAdminGroupWhenItIsMissing() {
    Group group = mock(Group.class);
    withAdminGroups(0);
    withExistingAuthorizations(1);
    when(identityService.newGroup(Groups.CAMUNDA_ADMIN)).thenReturn(group);

    resource.ensureCamundaAdminGroupExists(engine);

    verify(group).setName("camunda BPM Administrators");
    verify(group).setType(Groups.GROUP_TYPE_SYSTEM);
    verify(identityService).saveGroup(group);
  }

  @Test
  public void shouldLeaveAnExistingAdminGroupAlone() {
    withAdminGroups(1);
    withExistingAuthorizations(1);

    resource.ensureCamundaAdminGroupExists(engine);

    verify(identityService, never()).newGroup(any(String.class));
    verify(identityService, never()).saveGroup(any(Group.class));
  }

  /** One grant per built-in resource, and only for the resources that have none yet. */
  @Test
  public void shouldGrantAdminRightsOnEveryResourceThatHasNone() {
    withAdminGroups(1);
    withExistingAuthorizations(0);

    resource.ensureCamundaAdminGroupExists(engine);

    verify(authorizationService,
        org.mockito.Mockito.atLeast(org.camunda.bpm.engine.authorization.Resources.values().length))
        .saveAuthorization(any());
  }

  @Test
  public void shouldNotGrantAnythingTwice() {
    withAdminGroups(1);
    withExistingAuthorizations(1);

    resource.ensureCamundaAdminGroupExists(engine);

    verify(authorizationService, never()).saveAuthorization(any());
  }

  // object mapper -------------------------------------------------------------------------------------

  @Test
  public void shouldAnswerNullWhenThereIsNoProvidersContext() {
    assertThat(resource.getObjectMapper()).isNull();
  }
}
