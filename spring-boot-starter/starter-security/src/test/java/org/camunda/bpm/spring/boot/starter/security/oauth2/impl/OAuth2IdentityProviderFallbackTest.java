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

import java.util.UUID;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.persistence.GenericManagerFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Covers the {@link OAuth2IdentityProvider} fallback to the database provider that applies when
 * the Spring Security context holds no authenticated user, by running it inside a real in-memory
 * engine so every delegation hits the actual persistence layer.
 */
public class OAuth2IdentityProviderFallbackTest {

  protected static ProcessEngine engine;
  protected static IdentityService identityService;

  @BeforeClass
  public static void startEngine() {
    var config = new StandaloneInMemProcessEngineConfiguration();
    config.setJdbcUrl("jdbc:h2:mem:oauth2-fallback-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    config.setProcessEngineName("oauth2-fallback");
    config.setDatabaseSchemaUpdate(ProcessEngineConfigurationImpl.DB_SCHEMA_UPDATE_TRUE);
    config.setJobExecutorActivate(false);
    config.setIdentityProviderSessionFactory(new GenericManagerFactory(OAuth2IdentityProvider.class));
    engine = config.buildProcessEngine();
    identityService = engine.getIdentityService();
  }

  @AfterClass
  public static void stopEngine() {
    engine.close();
  }

  @After
  public void tearDown() {
    SecurityContextHolder.clearContext();
    identityService.clearAuthentication();
    identityService.createUserQuery().list().forEach(u -> identityService.deleteUser(u.getId()));
    identityService.createGroupQuery().list().forEach(g -> identityService.deleteGroup(g.getId()));
    identityService.createTenantQuery().list().forEach(t -> identityService.deleteTenant(t.getId()));
  }

  @Test
  public void shouldPersistAndQueryUsersInTheDatabase() {
    User user = identityService.newUser("u1");
    user.setFirstName("Una");
    user.setPassword("s3cret-pw");
    identityService.saveUser(user);

    assertThat(identityService.createUserQuery().userId("u1").singleResult().getFirstName()).isEqualTo("Una");
    assertThat(identityService.createUserQuery().count()).isEqualTo(1);
    assertThat(identityService.createNativeUserQuery().sql("select * from ACT_ID_USER").list()).hasSize(1);
    assertThat(identityService.checkPassword("u1", "s3cret-pw")).isTrue();
    assertThat(identityService.checkPassword("u1", "wrong")).isFalse();
  }

  @Test
  public void shouldPersistGroupsTenantsAndMemberships() {
    identityService.saveUser(identityService.newUser("u1"));
    identityService.saveGroup(identityService.newGroup("g1"));
    Tenant tenant = identityService.newTenant("t1");
    identityService.saveTenant(tenant);

    identityService.createMembership("u1", "g1");
    identityService.createTenantUserMembership("t1", "u1");
    identityService.createTenantGroupMembership("t1", "g1");

    assertThat(identityService.createGroupQuery().groupMember("u1").list()).extracting(Group::getId)
        .containsExactly("g1");
    assertThat(identityService.createUserQuery().memberOfTenant("t1").count()).isEqualTo(1);
    assertThat(identityService.createGroupQuery().memberOfTenant("t1").count()).isEqualTo(1);
    assertThat(identityService.createTenantQuery().tenantId("t1").singleResult().getId()).isEqualTo("t1");

    identityService.deleteTenantUserMembership("t1", "u1");
    identityService.deleteTenantGroupMembership("t1", "g1");
    identityService.deleteMembership("u1", "g1");

    assertThat(identityService.createUserQuery().memberOfTenant("t1").count()).isZero();
    assertThat(identityService.createGroupQuery().memberOfTenant("t1").count()).isZero();
    assertThat(identityService.createGroupQuery().groupMember("u1").count()).isZero();
  }

  @Test
  public void shouldUpdateAndDeleteEntities() {
    identityService.saveUser(identityService.newUser("u1"));
    identityService.saveGroup(identityService.newGroup("g1"));
    identityService.saveTenant(identityService.newTenant("t1"));

    User user = identityService.createUserQuery().userId("u1").singleResult();
    user.setLastName("Updated");
    identityService.saveUser(user);
    assertThat(identityService.createUserQuery().userId("u1").singleResult().getLastName()).isEqualTo("Updated");

    identityService.unlockUser("u1");
    identityService.deleteUser("u1");
    identityService.deleteGroup("g1");
    identityService.deleteTenant("t1");

    assertThat(identityService.createUserQuery().count()).isZero();
    assertThat(identityService.createGroupQuery().count()).isZero();
    assertThat(identityService.createTenantQuery().count()).isZero();
  }

  @Test
  public void shouldFallBackWhenSecurityContextIsUnauthenticated() {
    var provider = new OAuth2IdentityProvider();

    assertThat(provider.springSecurityAuthentication()).isFalse();
    assertThat(provider.createUserQuery(null)).isNotInstanceOf(OAuth2IdentityProvider.OAuth2UserQuery.class);
  }

  @Test
  public void shouldFlushAndCloseThroughTheDatabaseProvider() {
    CommandExecutor executor = ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration())
        .getCommandExecutorTxRequired();
    executor.execute(commandContext -> {
      var provider = new OAuth2IdentityProvider();
      provider.flush();
      provider.close();
      return null;
    });
  }
}
