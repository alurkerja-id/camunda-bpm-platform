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
package org.camunda.bpm.engine.rest.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

/** Covers the request flow of {@link ProcessEngineAuthenticationFilter} with the engine mocked. */
public class ProcessEngineAuthenticationFilterFlowTest {

  protected ProcessEngine engine;
  protected IdentityService identityService;
  protected AuthenticationProvider provider;
  protected ProcessEngineAuthenticationFilter filter;
  protected HttpServletRequest request;
  protected HttpServletResponse response;
  protected FilterChain chain;
  protected StringWriter body;
  protected String addressedEngineName;

  @Before
  public void setUp() throws IOException {
    engine = mock(ProcessEngine.class);
    identityService = mock(IdentityService.class);
    when(engine.getIdentityService()).thenReturn(identityService);
    provider = mock(AuthenticationProvider.class);

    filter = new ProcessEngineAuthenticationFilter() {
      @Override
      protected ProcessEngine getAddressedEngine(String engineName) {
        addressedEngineName = engineName;
        return "default".equals(engineName) || "other".equals(engineName) ? engine : null;
      }
    };
    filter.authenticationProvider = provider;

    request = mock(HttpServletRequest.class);
    when(request.getContextPath()).thenReturn("/ctx");
    when(request.getServletPath()).thenReturn("/rest");
    response = mock(HttpServletResponse.class);
    body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    chain = mock(FilterChain.class);
  }

  protected void requestUrl(String url) {
    when(request.getRequestURI()).thenReturn("/ctx/rest" + url);
  }

  @Test
  public void shouldPassTheEngineListThroughWithoutAuthentication() throws Exception {
    requestUrl("/engine");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(provider, never()).extractAuthenticatedUser(any(), any());
  }

  @Test
  public void shouldUseTheConfiguredPrefixInsteadOfTheServletPath() throws Exception {
    filter.servletPathPrefix = "/custom";
    when(request.getRequestURI()).thenReturn("/ctx/custom/engine/");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  public void shouldAnswerNotFoundForAnUnknownEngine() throws Exception {
    requestUrl("/engine/missing/process-definition");

    filter.doFilter(request, response, chain);

    verify(response).setStatus(404);
    verify(response).setContentType("application/json");
    assertThat(addressedEngineName).isEqualTo("missing");
    assertThat(body.toString()).contains("InvalidRequestException")
        .contains("Process engine missing not available");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  public void shouldChallengeAnUnauthenticatedRequest() throws Exception {
    requestUrl("/process-definition");
    when(provider.extractAuthenticatedUser(request, engine)).thenReturn(AuthenticationResult.unsuccessful());

    filter.doFilter(request, response, chain);

    verify(response).setStatus(401);
    verify(provider).augmentResponseByAuthenticationChallenge(response, engine);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  public void shouldAuthenticateWithTheProvidedGroupsAndTenantsAndClearAfterwards() throws Exception {
    requestUrl("/engine/other/process-definition");
    AuthenticationResult result = AuthenticationResult.successful("ana");
    result.setGroups(Arrays.asList("g1", "g2"));
    result.setTenants(Collections.singletonList("t1"));
    when(provider.extractAuthenticatedUser(request, engine)).thenReturn(result);

    filter.doFilter(request, response, chain);

    assertThat(addressedEngineName).isEqualTo("other");
    InOrder order = inOrder(identityService, chain);
    order.verify(identityService).setAuthentication("ana", Arrays.asList("g1", "g2"),
        Collections.singletonList("t1"));
    order.verify(chain).doFilter(request, response);
    order.verify(identityService).clearAuthentication();
  }

  @Test
  public void shouldLookUpGroupsAndTenantsWhenTheProviderGivesNone() throws Exception {
    requestUrl("/process-definition");
    when(provider.extractAuthenticatedUser(request, engine)).thenReturn(AuthenticationResult.successful("ana"));

    GroupQuery groupQuery = mock(GroupQuery.class);
    Group group = mock(Group.class);
    when(group.getId()).thenReturn("g1");
    when(identityService.createGroupQuery()).thenReturn(groupQuery);
    when(groupQuery.groupMember("ana")).thenReturn(groupQuery);
    when(groupQuery.list()).thenReturn(Collections.singletonList(group));

    TenantQuery tenantQuery = mock(TenantQuery.class);
    Tenant tenant = mock(Tenant.class);
    when(tenant.getId()).thenReturn("t1");
    when(identityService.createTenantQuery()).thenReturn(tenantQuery);
    when(tenantQuery.userMember("ana")).thenReturn(tenantQuery);
    when(tenantQuery.includingGroupsOfUser(true)).thenReturn(tenantQuery);
    when(tenantQuery.list()).thenReturn(Collections.singletonList(tenant));

    filter.doFilter(request, response, chain);

    verify(identityService).setAuthentication("ana", Collections.singletonList("g1"),
        Collections.singletonList("t1"));
    verify(identityService).clearAuthentication();
  }

  @Test
  public void shouldClearTheAuthenticationEvenWhenTheChainFails() throws Exception {
    requestUrl("/process-definition");
    AuthenticationResult result = AuthenticationResult.successful("ana");
    result.setGroups(Collections.<String>emptyList());
    result.setTenants(Collections.<String>emptyList());
    when(provider.extractAuthenticatedUser(request, engine)).thenReturn(result);
    ServletException failure = new ServletException("chain failed");
    org.mockito.Mockito.doThrow(failure).when(chain).doFilter(request, response);

    try {
      filter.doFilter(request, response, chain);
    } catch (ServletException e) {
      assertThat(e).isSameAs(failure);
    }

    verify(identityService).clearAuthentication();
  }

  @Test
  public void shouldExtractTheEngineNameFromTheUrl() {
    assertThat(filter.extractEngineName("/engine/foo/task")).isEqualTo("foo");
    assertThat(filter.extractEngineName("/engine/foo")).isEqualTo("foo");
    assertThat(filter.extractEngineName("/task")).isEqualTo("default");
  }

  @Test
  public void shouldRequireAuthenticationOnlyOutsideTheEngineList() {
    assertThat(filter.requiresEngineAuthentication("/engine")).isFalse();
    assertThat(filter.requiresEngineAuthentication("/engine/")).isFalse();
    assertThat(filter.requiresEngineAuthentication("/engine/default/task")).isTrue();
    assertThat(filter.requiresEngineAuthentication("/task")).isTrue();
  }

  @Test
  public void shouldDoNothingOnDestroy() {
    filter.destroy();
  }

}
