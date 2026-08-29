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
package org.camunda.bpm.webapp.impl.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import javax.servlet.ServletException;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.webapp.impl.security.auth.Authentications;
import org.junit.Test;
import org.mockito.InOrder;

/**
 * Covers {@link SecurityActions}, the wrapper that lends an action the caller's engine
 * authentication and puts things back afterwards.
 *
 * <p>What matters is the "afterwards": the restore sits in a finally block, so it has to happen
 * even when the action throws. Both paths are driven here.
 *
 * <p>runWithAuthentications is only exercised with an empty set, because a populated one reaches
 * for the engine through the static Cockpit registry.
 */
public class SecurityActionsTest {

  @Test
  public void shouldRunWithoutAuthenticationAndPutTheOldOneBack() throws Exception {
    Authentication previous = new Authentication("ana", null);
    IdentityService identityService = mock(IdentityService.class);
    when(identityService.getCurrentAuthentication()).thenReturn(previous);
    ProcessEngine engine = mock(ProcessEngine.class);
    when(engine.getIdentityService()).thenReturn(identityService);

    String result = SecurityActions.runWithoutAuthentication(() -> "done", engine);

    assertThat(result).isEqualTo("done");
    InOrder order = inOrder(identityService);
    order.verify(identityService).clearAuthentication();
    order.verify(identityService).setAuthentication(previous);
  }

  /** The restore is in a finally block, so a failing action must not leave the engine cleared. */
  @Test
  public void shouldPutTheOldAuthenticationBackEvenWhenTheActionFails() {
    Authentication previous = new Authentication("ana", null);
    IdentityService identityService = mock(IdentityService.class);
    when(identityService.getCurrentAuthentication()).thenReturn(previous);
    ProcessEngine engine = mock(ProcessEngine.class);
    when(engine.getIdentityService()).thenReturn(identityService);

    assertThatThrownBy(() -> SecurityActions.runWithoutAuthentication(() -> {
      throw new IOException("boom");
    }, engine)).isInstanceOf(IOException.class);

    verify(identityService).clearAuthentication();
    verify(identityService).setAuthentication(previous);
  }

  @Test
  public void shouldRestoreEvenWhenThereWasNoAuthenticationToBeginWith() throws Exception {
    IdentityService identityService = mock(IdentityService.class);
    when(identityService.getCurrentAuthentication()).thenReturn(null);
    ProcessEngine engine = mock(ProcessEngine.class);
    when(engine.getIdentityService()).thenReturn(identityService);

    SecurityActions.runWithoutAuthentication(() -> null, engine);

    verify(identityService).setAuthentication(null);
  }

  @Test
  public void shouldLetAServletExceptionThrough() {
    IdentityService identityService = mock(IdentityService.class);
    ProcessEngine engine = mock(ProcessEngine.class);
    when(engine.getIdentityService()).thenReturn(identityService);

    assertThatThrownBy(() -> SecurityActions.runWithoutAuthentication(() -> {
      throw new ServletException("boom");
    }, engine)).isInstanceOf(ServletException.class);

    verify(identityService).setAuthentication(null);
  }

  @Test
  public void shouldRunTheActionWhenThereIsNothingToAuthenticate() throws Exception {
    String result = SecurityActions.runWithAuthentications(() -> "done", new Authentications());

    assertThat(result).isEqualTo("done");
  }

  @Test
  public void shouldLetAFailureThroughWhenThereIsNothingToAuthenticate() {
    assertThatThrownBy(() -> SecurityActions.runWithAuthentications(() -> {
      throw new IOException("boom");
    }, new Authentications())).isInstanceOf(IOException.class);
  }
}
