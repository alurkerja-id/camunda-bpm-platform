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
package org.camunda.bpm.spring.boot.starter.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

public class PropertyBeansAccessorsTest {

  @Test
  public void shouldExposeSessionCookieInitParamsOnlyWhenNotDefault() {
    SessionCookieProperties cookie = new SessionCookieProperties();
    assertThat(cookie.getInitParams()).isEmpty();

    cookie.setEnableSecureCookie(true);
    cookie.setEnableSameSiteCookie(false);
    cookie.setSameSiteCookieOption("LAX");
    cookie.setSameSiteCookieValue("Lax");
    cookie.setCookieName("SID");

    assertThat(cookie.isEnableSecureCookie()).isTrue();
    assertThat(cookie.isEnableSameSiteCookie()).isFalse();
    assertThat(cookie.getSameSiteCookieOption()).isEqualTo("LAX");
    assertThat(cookie.getSameSiteCookieValue()).isEqualTo("Lax");
    assertThat(cookie.getCookieName()).isEqualTo("SID");
    assertThat(cookie.getInitParams()).containsEntry("enableSecureCookie", "true")
        .containsEntry("enableSameSiteCookie", "false").containsEntry("sameSiteCookieOption", "LAX")
        .containsEntry("sameSiteCookieValue", "Lax").containsEntry("cookieName", "SID").hasSize(5);
    assertThat(cookie.toString()).contains("cookieName='SID'");
  }

  @Test
  public void shouldExposeFetchAndLockInitParamsOnlyWhenNotDefault() {
    FetchAndLockProperties properties = new FetchAndLockProperties();
    assertThat(properties.getInitParams()).isEmpty();

    properties.setUniqueWorkerRequest(true);
    properties.setQueueCapacity(5);

    assertThat(properties.getInitParams()).containsEntry("fetch-and-lock-unique-worker-request", "true")
        .containsEntry("fetch-and-lock-queue-capacity", "5").hasSize(2);
  }

  @Test
  public void shouldExposeCsrfInitParams() {
    CsrfProperties csrf = new CsrfProperties();
    assertThat(csrf.getInitParams()).isEmpty();

    csrf.setTargetOrigin("https://example.org");
    csrf.setDenyStatus(409);
    csrf.setRandomClass("java.security.SecureRandom");
    csrf.setEntryPoints(Arrays.asList("/a", "/b"));
    csrf.setEnableSecureCookie(true);

    Map<String, String> params = csrf.getInitParams();
    assertThat(params).containsEntry("targetOrigin", "https://example.org").containsEntry("denyStatus", "409")
        .containsEntry("randomClass", "java.security.SecureRandom").containsEntry("entryPoints", "/a,/b")
        .containsEntry("enableSecureCookie", "true");
  }

  @Test
  public void shouldExposeHstsInitParamsWhenOverridden() {
    HeaderSecurityProperties headers = new HeaderSecurityProperties();
    headers.setHstsDisabled(false);
    headers.setHstsMaxAge("31536000");
    headers.setHstsIncludeSubdomainsDisabled(false);

    Map<String, String> params = headers.getInitParams();

    assertThat(params).containsEntry("hstsDisabled", "false").containsEntry("hstsMaxAge", "31536000")
        .containsEntry("hstsIncludeSubdomainsDisabled", "false");
  }

  @Test
  public void shouldRoundTripManagementHealthSettings() {
    ManagementProperties management = new ManagementProperties();
    ManagementProperties.Health health = new ManagementProperties.Health();
    ManagementProperties.Health.Camunda camunda = health.new Camunda();
    camunda.setEnabled(false);
    health.setCamunda(camunda);
    management.setHealth(health);

    assertThat(management.getHealth()).isSameAs(health);
    assertThat(health.getCamunda()).isSameAs(camunda);
    assertThat(camunda.isEnabled()).isFalse();
    assertThat(camunda.toString()).contains("enabled=false");
    assertThat(health.toString()).contains("camunda=");
    assertThat(management.toString()).contains("health=");
  }

  @Test
  public void shouldRoundTripAuthenticationCacheSettings() {
    AuthenticationProperties authentication = new AuthenticationProperties();
    AuthenticationProperties.CacheProperties cache = new AuthenticationProperties.CacheProperties();
    cache.setTtlEnabled(false);
    cache.setTimeToLive(42L);
    authentication.setCache(cache);

    assertThat(authentication.getCache()).isSameAs(cache);
    assertThat(cache.isTtlEnabled()).isFalse();
    assertThat(cache.getTimeToLive()).isEqualTo(42L);
    assertThat(cache.toString()).contains("ttlEnabled=false").contains("timeToLive=42");
  }

  @Test
  public void shouldRoundTripCamundaBpmPropertiesSections() throws Exception {
    CamundaBpmProperties properties = new CamundaBpmProperties();
    MetricsProperty metrics = new MetricsProperty();
    DatabaseProperty database = new DatabaseProperty();
    EventingProperty eventing = new EventingProperty();
    JobExecutionProperty jobExecution = new JobExecutionProperty();
    WebappProperty webapp = new WebappProperty();
    RestApiProperty restApi = new RestApiProperty();
    AuthorizationProperty authorization = new AuthorizationProperty();
    URL license = new URL("http://localhost/license.txt");
    String[] patterns = { "classpath*:/a/**" };
    SessionCookieProperties sessionCookie = new SessionCookieProperties();

    properties.setMetrics(metrics);
    properties.setDatabase(database);
    properties.setEventing(eventing);
    properties.setJobExecution(jobExecution);
    properties.setWebapp(webapp);
    properties.setRestApi(restApi);
    properties.setAuthorization(authorization);
    properties.setLicenseFile(license);
    properties.setDeploymentResourcePattern(patterns);
    properties.setDefaultSerializationFormat("application/xml");
    webapp.setSessionCookie(sessionCookie);

    assertThat(properties.getMetrics()).isSameAs(metrics);
    assertThat(properties.getDatabase()).isSameAs(database);
    assertThat(properties.getEventing()).isSameAs(eventing);
    assertThat(properties.getJobExecution()).isSameAs(jobExecution);
    assertThat(properties.getWebapp()).isSameAs(webapp);
    assertThat(properties.getRestApi()).isSameAs(restApi);
    assertThat(properties.getAuthorization()).isSameAs(authorization);
    assertThat(properties.getLicenseFile()).isSameAs(license);
    assertThat(properties.getDeploymentResourcePattern()).isSameAs(patterns);
    assertThat(properties.getDefaultSerializationFormat()).isEqualTo("application/xml");
    assertThat(webapp.getSessionCookie()).isSameAs(sessionCookie);
  }

  @Test
  public void shouldListSchemaUpdateValuesAndRejectEngineBuildingOnDefaults() {
    assertThat(DatabaseProperty.getSchemaUpdateValues()).contains("true", "false");
    assertThatThrownBy(() -> Defaults.INSTANCE.buildProcessEngine()).isInstanceOf(UnsupportedOperationException.class);
  }

}
