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
package org.camunda.bpm.engine.rest.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import javax.servlet.FilterChain;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

/**
 * Covers {@link AbstractEmptyBodyFilter}: requests with a JSON content type get their body peeked;
 * an empty body is replaced by "{}" so the JSON provider does not choke, everything else is passed
 * through untouched.
 */
public class AbstractEmptyBodyFilterTest {

  /** Records what the template method was called with. */
  static class RecordingFilter extends AbstractEmptyBodyFilter {
    boolean wrapped;
    boolean bodyWasEmpty;
    PushbackInputStream body;

    @Override
    public HttpServletRequestWrapper wrapRequest(HttpServletRequest req, boolean isBodyEmpty, PushbackInputStream requestBody) {
      wrapped = true;
      bodyWasEmpty = isBodyEmpty;
      body = requestBody;
      return new HttpServletRequestWrapper(req);
    }
  }

  private final RecordingFilter filter = new RecordingFilter();
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final ServletResponse response = mock(ServletResponse.class);
  private final FilterChain chain = mock(FilterChain.class);

  private static ServletInputStream streamOf(String content) {
    final ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    return new ServletInputStream() {
      @Override public int read() { return in.read(); }
    };
  }

  private void post(String contentType, String body) throws IOException {
    when(request.getContentType()).thenReturn(contentType);
    when(request.getInputStream()).thenReturn(streamOf(body));
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int b;
    while ((b = in.read()) != -1) {
      out.write(b);
    }
    return out.toByteArray();
  }

  @Test
  public void shouldFlagAnEmptyJsonBodyAndWrapTheRequest() throws Exception {
    post("application/json", "");

    filter.doFilter(request, response, chain);

    assertThat(filter.wrapped).isTrue();
    assertThat(filter.bodyWasEmpty).isTrue();
    verify(chain).doFilter(ArgumentMatchers.any(HttpServletRequestWrapper.class), ArgumentMatchers.eq(response));
  }

  @Test
  public void shouldKeepTheFirstByteOfANonEmptyJsonBody() throws Exception {
    post("application/json", "{\"a\":1}");

    filter.doFilter(request, response, chain);

    assertThat(filter.bodyWasEmpty).isFalse();
    // the byte consumed while peeking was pushed back, so the whole body is still readable
    assertThat(new String(readAll(filter.body), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
  }

  @Test
  public void shouldAcceptJsonContentTypeWithParametersInAnyCase() throws Exception {
    post("Application/JSON; charset=UTF-8", "x");

    filter.doFilter(request, response, chain);

    assertThat(filter.wrapped).isTrue();
  }

  @Test
  public void shouldPassOtherContentTypesThroughUntouched() throws Exception {
    post("text/plain", "");

    filter.doFilter(request, response, chain);

    assertThat(filter.wrapped).isFalse();
    verify(chain).doFilter(request, response);
  }

  @Test
  public void shouldPassARequestWithoutContentTypeThroughUntouched() throws Exception {
    post(null, "");

    filter.doFilter(request, response, chain);

    assertThat(filter.wrapped).isFalse();
    verify(chain).doFilter(request, response);
  }

  @Test
  public void shouldNotTreatSimilarContentTypesAsJson() throws Exception {
    post("application/jsonp", "");
    filter.doFilter(request, response, chain);
    assertThat(filter.wrapped).isFalse();

    post("application/vnd.api+json", "");
    filter.doFilter(request, response, chain);
    assertThat(filter.wrapped).isFalse();
  }

  @Test
  public void shouldSubstituteAnEmptyJsonObjectForAnEmptyBody() throws Exception {
    PushbackInputStream original = new PushbackInputStream(new ByteArrayInputStream(new byte[0]));

    assertThat(new String(readAll(filter.getRequestBody(true, original)), StandardCharsets.UTF_8)).isEqualTo("{}");
  }

  @Test
  public void shouldKeepTheOriginalBodyWhenItIsNotEmpty() {
    PushbackInputStream original = new PushbackInputStream(new ByteArrayInputStream(new byte[] {1}));

    assertThat(filter.getRequestBody(false, original)).isSameAs(original);
  }

  @Test
  public void shouldReadLinesThroughAReaderOverTheServletStream() throws Exception {
    assertThat(filter.getReader(streamOf("one\ntwo")).readLine()).isEqualTo("one");
  }

  @Test
  public void shouldTolerateInitAndDestroy() throws Exception {
    filter.init(null);
    filter.destroy();
  }
}
