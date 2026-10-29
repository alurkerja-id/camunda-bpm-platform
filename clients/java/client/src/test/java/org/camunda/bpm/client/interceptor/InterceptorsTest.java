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
package org.camunda.bpm.client.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.camunda.bpm.client.exception.ExternalTaskClientException;
import org.camunda.bpm.client.interceptor.auth.BasicAuthProvider;
import org.camunda.bpm.client.interceptor.impl.RequestInterceptorHandler;
import org.junit.Test;

public class InterceptorsTest {

  @Test
  public void shouldAddBasicAuthorizationHeader() {
    ClientRequestContext context = mock(ClientRequestContext.class);

    new BasicAuthProvider("demo", "secret").intercept(context);

    verify(context).addHeader("Authorization", "Basic ZGVtbzpzZWNyZXQ=");
  }

  @Test
  public void shouldRejectMissingCredentials() {
    assertThatThrownBy(() -> new BasicAuthProvider(null, "x")).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> new BasicAuthProvider("x", null)).isInstanceOf(ExternalTaskClientException.class);
  }

  @Test
  public void shouldApplyHeadersOfAllInterceptorsToRequest() throws Exception {
    List<ClientRequestInterceptor> interceptors = Arrays.asList(
        context -> context.addHeader("X-One", "1"),
        context -> context.addHeader("X-Two", "2"));
    RequestInterceptorHandler handler = new RequestInterceptorHandler(interceptors);
    HttpRequest request = new BasicHttpRequest("GET", "/");

    handler.process(request, null, null);

    assertThat(request.getFirstHeader("X-One").getValue()).isEqualTo("1");
    assertThat(request.getFirstHeader("X-Two").getValue()).isEqualTo("2");
    assertThat(handler.getInterceptors()).isSameAs(interceptors);
  }

  @Test
  public void shouldContinueWhenAnInterceptorFails() throws Exception {
    List<ClientRequestInterceptor> interceptors = Arrays.asList(
        context -> { throw new IllegalStateException("broken interceptor"); },
        context -> context.addHeader("X-After", "ok"));
    HttpRequest request = new BasicHttpRequest("GET", "/");

    new RequestInterceptorHandler(interceptors).process(request, null, null);

    assertThat(request.getFirstHeader("X-After").getValue()).isEqualTo("ok");
  }

  @Test
  public void shouldLeaveRequestUntouchedWithoutInterceptors() throws Exception {
    HttpRequest request = new BasicHttpRequest("GET", "/");

    new RequestInterceptorHandler(Collections.emptyList()).process(request, null, null);

    assertThat(request.getHeaders()).isEmpty();
  }

}
