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
package org.camunda.bpm.webapp.impl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.camunda.bpm.admin.Admin;
import org.camunda.bpm.admin.AdminRuntimeDelegate;
import org.camunda.bpm.cockpit.Cockpit;
import org.camunda.bpm.cockpit.CockpitRuntimeDelegate;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.identity.UserQuery;
import org.camunda.bpm.engine.rest.util.WebApplicationUtil;
import org.camunda.bpm.tasklist.Tasklist;
import org.camunda.bpm.tasklist.TasklistRuntimeDelegate;
import org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl.ContentSecurityPolicyProvider;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.camunda.bpm.webapp.plugin.AppPluginRegistry;
import org.camunda.bpm.webapp.plugin.spi.AppPlugin;
import org.camunda.bpm.welcome.Welcome;
import org.camunda.bpm.welcome.WelcomeRuntimeDelegate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

/**
 * Drives {@link ProcessEnginesFilter#doFilter} end to end against mocked servlet objects: every
 * routing branch (redirects, index page, setup page, html templates, pass-through) plus the
 * placeholder substitution of the served index page.
 */
public class ProcessEnginesFilterTest {

  protected static final String INDEX_HTML =
      "root=$APP_ROOT base=$BASE pkgs=$PLUGIN_PACKAGES deps=$PLUGIN_DEPENDENCIES nonce=[$CSP_NONCE]";

  protected CockpitRuntimeDelegate cockpit = mock(CockpitRuntimeDelegate.class);
  protected AdminRuntimeDelegate admin = mock(AdminRuntimeDelegate.class);
  protected TasklistRuntimeDelegate tasklist = mock(TasklistRuntimeDelegate.class);
  protected WelcomeRuntimeDelegate welcome = mock(WelcomeRuntimeDelegate.class);

  protected ServletContext servletContext = mock(ServletContext.class);
  protected HttpServletRequest request = mock(HttpServletRequest.class);
  protected HttpServletResponse response = mock(HttpServletResponse.class);
  protected FilterChain chain = mock(FilterChain.class);
  protected Map<String, Object> attributes = new HashMap<>();
  protected StringWriter body = new StringWriter();

  protected ProcessEnginesFilter filter;
  protected MockedStatic<WebApplicationUtil> webApplicationUtil;

  @Before
  public void setUp() throws Exception {
    Cockpit.setCockpitRuntimeDelegate(cockpit);
    Admin.setAdminRuntimeDelegate(admin);
    Tasklist.setTasklistRuntimeDelegate(tasklist);
    Welcome.setRuntimeDelegate(welcome);

    when(servletContext.getAttribute(anyString())).thenAnswer(i -> attributes.get(i.<String>getArgument(0)));
    org.mockito.Mockito.doAnswer(i -> attributes.put(i.getArgument(0), i.getArgument(1)))
        .when(servletContext).setAttribute(anyString(), any());
    when(servletContext.getResourceAsStream("/app/cockpit/index.html"))
        .thenAnswer(i -> new ByteArrayInputStream(INDEX_HTML.getBytes(StandardCharsets.UTF_8)));
    when(request.getServletContext()).thenReturn(servletContext);
    when(request.getContextPath()).thenReturn("/camunda");
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    when(cockpit.getProcessEngineNames()).thenReturn(Set.of("default"));
    ProcessEngine defaultEngine = mock(ProcessEngine.class);
    when(defaultEngine.getName()).thenReturn("default");
    when(cockpit.getDefaultProcessEngine()).thenReturn(defaultEngine);

    FilterConfig config = mock(FilterConfig.class);
    when(config.getServletContext()).thenReturn(servletContext);
    filter = new ProcessEnginesFilter();
    filter.init(config);

    webApplicationUtil = mockStatic(WebApplicationUtil.class);
  }

  @After
  public void tearDown() {
    webApplicationUtil.close();
    Cockpit.setCockpitRuntimeDelegate(null);
    Admin.setAdminRuntimeDelegate(null);
    Tasklist.setTasklistRuntimeDelegate(null);
    Welcome.setRuntimeDelegate(null);
  }

  protected void request(String uriWithoutContext) throws Exception {
    when(request.getRequestURI()).thenReturn("/camunda" + uriWithoutContext);
    filter.doFilter(request, response, chain);
  }

  /** Registers an engine whose identity service has the given number of camunda-admin users. */
  protected ProcessEngine engineWithAdmins(String name, boolean readOnly, long admins) {
    ProcessEngine engine = mock(ProcessEngine.class);
    IdentityService identityService = mock(IdentityService.class);
    UserQuery userQuery = mock(UserQuery.class);
    when(engine.getIdentityService()).thenReturn(identityService);
    when(identityService.isReadOnly()).thenReturn(readOnly);
    when(identityService.createUserQuery()).thenReturn(userQuery);
    when(userQuery.memberOfGroup(Groups.CAMUNDA_ADMIN)).thenReturn(userQuery);
    when(userQuery.count()).thenReturn(admins);
    when(cockpit.getProcessEngine(name)).thenReturn(engine);
    return engine;
  }

  protected AppPlugin plugin(String id) {
    AppPlugin plugin = mock(AppPlugin.class);
    org.mockito.Mockito.doReturn(id).when(plugin).getId();
    return plugin;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected AppPluginRegistry registry(AppPlugin... plugins) {
    AppPluginRegistry registry = mock(AppPluginRegistry.class);
    when(registry.getPlugins()).thenReturn(List.of(plugins));
    return registry;
  }

  @Test
  public void shouldRedirectApplicationRootToWelcomeOfDefaultEngine() throws Exception {
    request("/app/");

    verify(response).sendRedirect("/camunda/app/welcome/default/");
  }

  @Test
  public void shouldRedirectAppWithoutEngineToDefaultEngine() throws Exception {
    request("/app/cockpit/");

    verify(response).sendRedirect("/camunda/app/cockpit/default/");
  }

  @Test
  public void shouldTreatIndexHtmlAsMissingEngineName() throws Exception {
    request("/app/admin/index.html");

    verify(response).sendRedirect("/camunda/app/admin/default/");
  }

  @Test
  public void shouldFailRedirectWhenNoEngineIsConfigured() {
    when(cockpit.getProcessEngineNames()).thenReturn(Set.of());

    assertThatThrownBy(() -> request("/app/cockpit/"))
        .isInstanceOf(org.camunda.bpm.webapp.impl.IllegalWebAppConfigurationException.class);
  }

  @Test
  public void shouldFallBackToFirstEngineNameWhenThereIsNoDefaultEngine() throws Exception {
    when(cockpit.getDefaultProcessEngine()).thenReturn(null);
    when(cockpit.getProcessEngineNames()).thenReturn(Set.of("only"));

    request("/app/cockpit/");

    verify(response).sendRedirect("/camunda/app/cockpit/only/");
  }

  @Test
  public void shouldHonourConfiguredApplicationPath() throws Exception {
    attributes.put("org.camunda.bpm.spring.boot.starter.webapp.applicationPath", "/webapp");
    String appPath = ServletContextUtil.getAppPath(servletContext);
    assertThat(appPath).isEqualTo("/webapp");

    request("/webapp/app/cockpit/");

    verify(response).sendRedirect("/camunda/webapp/app/cockpit/default/");
  }

  @Test
  public void shouldServeIndexPageWithSubstitutedPlaceholders() throws Exception {
    org.mockito.Mockito.doReturn(registry(plugin("a"), plugin("b"))).when(cockpit).getAppPluginRegistry();
    attributes.put(ContentSecurityPolicyProvider.ATTR_CSP_FILTER_NONCE, "N0NCE");
    webApplicationUtil.when(() -> WebApplicationUtil.setWebapp("default", "cockpit")).thenReturn(true);

    request("/app/cockpit/default/");

    String html = body.toString();
    assertThat(html).contains("root=/camunda ").contains("base=/camunda/app/cockpit/default/ ")
        .contains("nonce=[nonce=\"N0NCE\"]")
        .contains("{ name: 'cockpit-plugin-a', location: '/camunda/api/cockpit/plugin/a/static/app', main: 'plugin.js' }, \n"
            + "{ name: 'cockpit-plugin-b', location: '/camunda/api/cockpit/plugin/b/static/app', main: 'plugin.js' }")
        .contains("deps=[{ ngModuleName: 'cockpit.plugin.a', requirePackageName: 'cockpit-plugin-a' }, \n"
            + "{ ngModuleName: 'cockpit.plugin.b', requirePackageName: 'cockpit-plugin-b' }]");
    verify(response).setContentType("text/html");
    verify(response).setContentLength(html.getBytes(StandardCharsets.UTF_8).length);
    assertThat(ServletContextUtil.isTelemetryDataSentAlready("cockpit", "default", servletContext)).isTrue();
  }

  @Test
  public void shouldOmitNonceAndReportTelemetryOnlyOnce() throws Exception {
    org.mockito.Mockito.doReturn(registry()).when(cockpit).getAppPluginRegistry();
    webApplicationUtil.when(() -> WebApplicationUtil.setWebapp("default", "cockpit")).thenReturn(false);

    request("/app/cockpit/default/");
    request("/app/cockpit/default/");

    assertThat(body.toString()).contains("nonce=[]").contains("pkgs=[]").contains("deps=[]");
    assertThat(ServletContextUtil.isTelemetryDataSentAlready("cockpit", "default", servletContext)).isFalse();
    webApplicationUtil.verify(() -> WebApplicationUtil.setWebapp("default", "cockpit"),
        org.mockito.Mockito.times(2));
  }

  @Test
  public void shouldNotReportTelemetryTwiceOnceItWasSent() throws Exception {
    org.mockito.Mockito.doReturn(registry()).when(cockpit).getAppPluginRegistry();
    ServletContextUtil.setTelemetryDataSent("cockpit", "default", servletContext);

    request("/app/cockpit/default/");

    webApplicationUtil.verify(() -> WebApplicationUtil.setWebapp(anyString(), anyString()), never());
  }

  @Test
  public void shouldResolvePluginsPerApplication() {
    org.mockito.Mockito.doReturn(registry(plugin("adm"))).when(admin).getAppPluginRegistry();
    org.mockito.Mockito.doReturn(registry(plugin("tl"))).when(tasklist).getAppPluginRegistry();
    org.mockito.Mockito.doReturn(registry(plugin("wl"))).when(welcome).getAppPluginRegistry();

    assertThat(filter.<AppPlugin>getPlugins("admin")).extracting(AppPlugin::getId).containsExactly("adm");
    assertThat(filter.<AppPlugin>getPlugins("tasklist")).extracting(AppPlugin::getId).containsExactly("tl");
    assertThat(filter.<AppPlugin>getPlugins("welcome")).extracting(AppPlugin::getId).containsExactly("wl");
    assertThat(filter.<AppPlugin>getPlugins("unknown")).isEmpty();
  }

  @Test
  public void shouldRedirectToSetupWhenEngineHasNoAdminUser() throws Exception {
    engineWithAdmins("default", false, 0);

    request("/app/cockpit/default/");

    verify(response).sendRedirect("/camunda/app/admin/default/setup/#/setup");
    verify(response, never()).getWriter();
  }

  @Test
  public void shouldServeIndexAsSetupPageWhenEngineHasNoAdminUser() throws Exception {
    engineWithAdmins("default", false, 0);
    org.mockito.Mockito.doReturn(registry()).when(cockpit).getAppPluginRegistry();

    request("/app/cockpit/default/setup/");

    verify(response, never()).sendRedirect(anyString());
    assertThat(body.toString()).contains("base=/camunda/app/cockpit/default/ ");
  }

  @Test
  public void shouldLeaveSetupPageWhenAdminUserExists() throws Exception {
    engineWithAdmins("default", false, 1);

    request("/app/cockpit/default/setup/");

    verify(response).sendRedirect("/camunda/app/cockpit/default/");
  }

  @Test
  public void shouldNeverAskForSetupWhenIdentityServiceIsReadOnly() throws Exception {
    ProcessEngine engine = engineWithAdmins("default", true, 0);
    org.mockito.Mockito.doReturn(registry()).when(cockpit).getAppPluginRegistry();

    request("/app/cockpit/default/");

    verify(engine.getIdentityService(), never()).createUserQuery();
    assertThat(body.toString()).contains("base=/camunda/app/cockpit/default/ ");
  }

  @Test
  public void shouldRestoreCallerAuthenticationAfterAdminCheck() throws Exception {
    ProcessEngine engine = engineWithAdmins("default", false, 1);
    org.mockito.Mockito.doReturn(registry()).when(cockpit).getAppPluginRegistry();
    org.camunda.bpm.engine.impl.identity.Authentication current =
        mock(org.camunda.bpm.engine.impl.identity.Authentication.class);
    when(engine.getIdentityService().getCurrentAuthentication()).thenReturn(current);

    request("/app/cockpit/default/");

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(engine.getIdentityService());
    order.verify(engine.getIdentityService()).clearAuthentication();
    order.verify(engine.getIdentityService()).setAuthentication(current);
  }

  @Test
  public void shouldRedirectIndexHtmlPageToEngineRoot() throws Exception {
    request("/app/cockpit/default/index.html");

    verify(response).sendRedirect("/camunda/app/cockpit/default/");
  }

  @Test
  public void shouldPassThroughWhenTemplateExistsUnderEngineNamespace() throws Exception {
    when(servletContext.getResource("/app/cockpit/default/views/a.html")).thenReturn(new URL("file:/a.html"));

    request("/app/cockpit/default/views/a.html");

    verify(chain).doFilter(request, response);
    verify(request, never()).getRequestDispatcher(anyString());
  }

  @Test
  public void shouldForwardTemplateToEngineLessPathWhenOnlyThatExists() throws Exception {
    when(servletContext.getResource("/app/cockpit/default/views/a.html")).thenReturn(null);
    when(servletContext.getResource("/app/cockpit/views/a.html")).thenReturn(new URL("file:/a.html"));
    RequestDispatcher dispatcher = mock(RequestDispatcher.class);
    when(request.getRequestDispatcher("/app/cockpit/views/a.html")).thenReturn(dispatcher);

    request("/app/cockpit/default/views/a.html");

    verify(dispatcher).forward(request, response);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  public void shouldPassThroughMissingTemplates() throws Exception {
    request("/app/cockpit/default/views/missing.html");

    verify(chain).doFilter(request, response);
    verify(request, never()).getRequestDispatcher(anyString());
  }

  @Test
  public void shouldPassThroughNonHtmlResourcesAndForeignUris() throws Exception {
    request("/app/cockpit/default/scripts/app.js");
    request("/api/engine/engine/default/process-definition");

    verify(chain, org.mockito.Mockito.times(2)).doFilter(request, response);
    verify(response, never()).sendRedirect(anyString());
  }

  @Test
  public void shouldTreatMissingWebResourceAsAbsent() throws Exception {
    when(servletContext.getResource(anyString())).thenThrow(new java.net.MalformedURLException("bad"));

    request("/app/cockpit/default/views/a.html");

    verify(chain).doFilter(request, response);
  }

  @Test
  public void shouldDropServletContextOnDestroy() {
    filter.destroy();

    assertThatThrownBy(() -> request("/app/cockpit/default/views/a.html")).isInstanceOf(NullPointerException.class);
  }
}
