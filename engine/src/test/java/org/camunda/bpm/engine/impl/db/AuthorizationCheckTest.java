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
package org.camunda.bpm.engine.impl.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.junit.Test;

public class AuthorizationCheckTest {

  @Test
  public void shouldHaveCheckDisabledAndAllPermissionsByDefault() {
    AuthorizationCheck check = new AuthorizationCheck();

    assertThat(check.isAuthorizationCheckEnabled()).isFalse();
    assertThat(check.getIsAuthorizationCheckEnabled()).isFalse();
    assertThat(check.isRevokeAuthorizationCheckEnabled()).isFalse();
    assertThat(check.isHistoricInstancePermissionsEnabled()).isFalse();
    assertThat(check.isUseLeftJoin()).isTrue();
    assertThat(check.getAuthDefaultPerm()).isEqualTo(Permissions.ALL.getValue());
    assertThat(check.getAuthUserId()).isNull();
    assertThat(check.getAuthGroupIds()).isEmpty();
  }

  @Test
  public void shouldKeepConstructorArguments() {
    List<String> groups = Arrays.asList("g1", "g2");
    CompositePermissionCheck composite = new CompositePermissionCheck();

    AuthorizationCheck check = new AuthorizationCheck("kermit", groups, composite, true);

    assertThat(check.getAuthUserId()).isEqualTo("kermit");
    assertThat(check.getAuthGroupIds()).containsExactly("g1", "g2");
    assertThat(check.getPermissionChecks()).isSameAs(composite);
    assertThat(check.isRevokeAuthorizationCheckEnabled()).isTrue();
  }

  @Test
  public void shouldRoundTripSetters() {
    AuthorizationCheck check = new AuthorizationCheck();
    CompositePermissionCheck composite = new CompositePermissionCheck(true);

    check.setAuthorizationCheckEnabled(true);
    check.setShouldPerformAuthorizatioCheck(true);
    check.setAuthUserId("gonzo");
    check.setAuthGroupIds(Collections.singletonList("muppets"));
    check.setAuthDefaultPerm(Permissions.READ.getValue());
    check.setPermissionChecks(composite);
    check.setRevokeAuthorizationCheckEnabled(true);
    check.setHistoricInstancePermissionsEnabled(true);
    check.setUseLeftJoin(false);

    assertThat(check.isAuthorizationCheckEnabled()).isTrue();
    assertThat(check.shouldPerformAuthorizatioCheck()).isTrue();
    assertThat(check.getAuthUserId()).isEqualTo("gonzo");
    assertThat(check.getAuthGroupIds()).containsExactly("muppets");
    assertThat(check.getAuthDefaultPerm()).isEqualTo(Permissions.READ.getValue());
    assertThat(check.getPermissionChecks()).isSameAs(composite);
    assertThat(check.isRevokeAuthorizationCheckEnabled()).isTrue();
    assertThat(check.isHistoricInstancePermissionsEnabled()).isTrue();
    assertThat(check.isUseLeftJoin()).isFalse();
  }

  @Test
  public void shouldNotPerformCheckWhenEnabledButNoPermissionChecksExist() {
    AuthorizationCheck check = new AuthorizationCheck();
    check.setAuthorizationCheckEnabled(true);

    assertThat(check.getShouldPerformAuthorizatioCheck()).isFalse();
  }

  @Test
  public void shouldNotPerformCheckWhenDisabledEvenWithPermissionChecks() {
    AuthorizationCheck check = new AuthorizationCheck();
    check.addAtomicPermissionCheck(new PermissionCheck());

    assertThat(check.getShouldPerformAuthorizatioCheck()).isFalse();
  }

  @Test
  public void shouldPerformCheckWhenEnabledWithAtomicCheck() {
    AuthorizationCheck check = new AuthorizationCheck();
    check.setAuthorizationCheckEnabled(true);
    check.addAtomicPermissionCheck(new PermissionCheck());

    assertThat(check.getShouldPerformAuthorizatioCheck()).isTrue();
  }

  @Test
  public void shouldPerformCheckWhenEnabledWithCompositeCheckOnly() {
    AuthorizationCheck check = new AuthorizationCheck();
    check.setAuthorizationCheckEnabled(true);
    check.getPermissionChecks().addCompositeCheck(new CompositePermissionCheck());

    assertThat(check.getShouldPerformAuthorizatioCheck()).isTrue();
  }

  @Test
  public void shouldReplaceAtomicPermissionChecks() {
    AuthorizationCheck check = new AuthorizationCheck();
    PermissionCheck atomic = new PermissionCheck();
    atomic.setResource(Resources.PROCESS_DEFINITION);

    check.setAtomicPermissionChecks(Collections.singletonList(atomic));

    assertThat(check.getPermissionChecks().getAtomicChecks()).containsExactly(atomic);
  }
}
