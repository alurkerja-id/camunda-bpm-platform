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
package org.camunda.bpm.engine.impl.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;

import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.AuthorizationQuery;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.persistence.entity.AuthorizationEntity;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class AdministratorAuthorizationPluginTest {

  protected AdministratorAuthorizationPlugin plugin = new AdministratorAuthorizationPlugin();
  protected ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
  protected ProcessEngine engine = mock(ProcessEngine.class);
  protected AuthorizationService authorizationService = mock(AuthorizationService.class);
  protected AuthorizationQuery query = mock(AuthorizationQuery.class, RETURNS_SELF);

  @Before
  public void setUp() {
    when(configuration.getAdminGroups()).thenReturn(new ArrayList<>());
    when(configuration.getAdminUsers()).thenReturn(new ArrayList<>());
    when(engine.getAuthorizationService()).thenReturn(authorizationService);
    when(authorizationService.createAuthorizationQuery()).thenReturn(query);
    when(query.count()).thenReturn(0L);
  }

  @Test
  public void shouldExposeNames() {
    plugin.setAdministratorGroupName("g");
    plugin.setAdministratorUserName("u");

    assertThat(plugin.getAdministratorGroupName()).isEqualTo("g");
    assertThat(plugin.getAdministratorUserName()).isEqualTo("u");
  }

  @Test
  public void shouldRegisterAdminGroupAndUserInConfiguration() {
    plugin.setAdministratorGroupName("g");
    plugin.setAdministratorUserName("u");

    plugin.postInit(configuration);

    assertThat(configuration.getAdminGroups()).containsExactly("g");
    assertThat(configuration.getAdminUsers()).containsExactly("u");
  }

  @Test
  public void shouldIgnoreNullOrEmptyNamesInConfiguration() {
    plugin.postInit(configuration);
    plugin.setAdministratorGroupName("");
    plugin.setAdministratorUserName("");
    plugin.postInit(configuration);

    assertThat(configuration.getAdminGroups()).isEmpty();
    assertThat(configuration.getAdminUsers()).isEmpty();
  }

  @Test
  public void shouldNotCreateAuthorizationsWhenAuthorizationIsDisabled() {
    plugin.setAdministratorGroupName("g");
    plugin.setAdministratorUserName("u");
    when(configuration.isAuthorizationEnabled()).thenReturn(false);
    plugin.postInit(configuration);

    plugin.postProcessEngineBuild(engine);

    verify(engine, never()).getAuthorizationService();
  }

  @Test
  public void shouldCreateGrantsForGroupAndUserOnEveryResource() {
    plugin.setAdministratorGroupName("g");
    plugin.setAdministratorUserName("u");
    when(configuration.isAuthorizationEnabled()).thenReturn(true);
    plugin.postInit(configuration);

    plugin.postProcessEngineBuild(engine);

    ArgumentCaptor<Authorization> captor = ArgumentCaptor.forClass(Authorization.class);
    verify(authorizationService, times(2 * Resources.values().length)).saveAuthorization(captor.capture());
    HashSet<String> groups = new HashSet<>();
    HashSet<String> users = new HashSet<>();
    for (Authorization auth : captor.getAllValues()) {
      assertThat(auth).isInstanceOf(AuthorizationEntity.class);
      assertThat(auth.getResourceId()).isEqualTo("*");
      if (auth.getGroupId() != null) {
        groups.add(auth.getGroupId());
      }
      if (auth.getUserId() != null) {
        users.add(auth.getUserId());
      }
    }
    assertThat(groups).containsExactly("g");
    assertThat(users).containsExactly("u");
  }

  @Test
  public void shouldSkipResourcesThatAlreadyHaveAnAuthorization() {
    plugin.setAdministratorGroupName("g");
    plugin.setAdministratorUserName("u");
    when(configuration.isAuthorizationEnabled()).thenReturn(true);
    when(query.count()).thenReturn(1L);
    plugin.postInit(configuration);

    plugin.postProcessEngineBuild(engine);

    verify(authorizationService, never()).saveAuthorization(any());
  }

  @Test
  public void shouldNotCreateAnythingWithoutConfiguredNames() {
    when(configuration.isAuthorizationEnabled()).thenReturn(true);
    plugin.postInit(configuration);

    plugin.postProcessEngineBuild(engine);

    verify(authorizationService, never()).saveAuthorization(any());
  }
}
