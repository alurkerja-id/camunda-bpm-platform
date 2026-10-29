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
package org.camunda.bpm.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.camunda.bpm.client.exception.RestException;
import org.camunda.bpm.client.task.impl.ExternalTaskImpl;
import org.camunda.bpm.client.task.impl.dto.LockRequestDto;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

public class RequestExecutorTest {

  protected HttpServer server;
  protected String baseUrl;
  protected RequestExecutor executor;
  protected final AtomicReference<String> lastMethod = new AtomicReference<>();
  protected final AtomicReference<String> lastBody = new AtomicReference<>();
  protected final AtomicReference<String> lastUserAgent = new AtomicReference<>();
  protected final AtomicReference<String> lastContentType = new AtomicReference<>();

  @Before
  public void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/tasks", exchange -> respond(exchange, 200, "[{\"id\":\"t1\",\"topicName\":\"x\"}]"));
    server.createContext("/void", exchange -> respond(exchange, 204, null));
    server.createContext("/binary", exchange -> respond(exchange, 200, "bytes!"));
    server.createContext("/error", exchange -> respond(exchange, 400,
        "{\"type\":\"InvalidRequestException\",\"message\":\"bad input\",\"code\":7}"));
    server.createContext("/garbage", exchange -> respond(exchange, 200, "<<not json>>"));
    server.createContext("/mismatch", exchange -> respond(exchange, 200, "{\"id\":[1,2]}"));
    server.start();
    baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    executor = new RequestExecutor(HttpClients.createDefault(), new ObjectMapper());
  }

  @After
  public void stopServer() {
    server.stop(0);
  }

  protected void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
    lastMethod.set(exchange.getRequestMethod());
    lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    lastUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
    lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
    if (body == null) {
      exchange.sendResponseHeaders(status, -1);
    } else {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    }
    exchange.close();
  }

  @Test
  public void shouldPostJsonAndDeserializeResponse() {
    ExternalTaskImpl[] tasks = executor.postRequest(baseUrl + "/tasks", new LockRequestDto("worker", 500L), ExternalTaskImpl[].class);

    assertThat(tasks).hasSize(1);
    assertThat(tasks[0].getId()).isEqualTo("t1");
    assertThat(lastMethod.get()).isEqualTo("POST");
    assertThat(lastBody.get()).contains("\"workerId\":\"worker\"").contains("\"lockDuration\":500");
    assertThat(lastUserAgent.get()).isEqualTo("Camunda External Task Client");
    assertThat(lastContentType.get()).startsWith("application/json");
  }

  @Test
  public void shouldIgnoreResponseBodyForVoidResponses() {
    Void result = executor.postRequest(baseUrl + "/void", new LockRequestDto("worker", 1L), Void.class);

    assertThat(result).isNull();
  }

  @Test
  public void shouldReadRawBytesOnGet() {
    byte[] result = executor.getRequest(baseUrl + "/binary");

    assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo("bytes!");
    assertThat(lastMethod.get()).isEqualTo("GET");
  }

  @Test
  public void shouldTranslateErrorStatusIntoRestExceptionCause() {
    Throwable thrown = null;
    try {
      executor.postRequest(baseUrl + "/error", new LockRequestDto("w", 1L), Void.class);
    } catch (EngineClientException e) {
      thrown = e;
    }

    assertThat(thrown).isNotNull();
    assertThat(thrown.getCause()).isInstanceOf(RestException.class);
    RestException rest = (RestException) thrown.getCause();
    assertThat(rest.getHttpStatusCode()).isEqualTo(400);
    assertThat(rest.getMessage()).isEqualTo("bad input");
    assertThat(rest.getType()).isEqualTo("InvalidRequestException");
    assertThat(rest.getCode()).isEqualTo(7);
  }

  @Test
  public void shouldFailOnUnparsableOrUnmappableJson() {
    assertThatThrownBy(() -> executor.postRequest(baseUrl + "/garbage", new LockRequestDto("w", 1L), ExternalTaskImpl[].class))
        .isInstanceOf(EngineClientException.class);
    assertThatThrownBy(() -> executor.postRequest(baseUrl + "/mismatch", new LockRequestDto("w", 1L), ExternalTaskImpl.class))
        .isInstanceOf(EngineClientException.class);
  }

  @Test
  public void shouldFailWhenConnectionCannotBeEstablished() {
    server.stop(0);

    assertThatThrownBy(() -> executor.getRequest(baseUrl + "/binary")).isInstanceOf(EngineClientException.class)
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  public void shouldFailWhenRequestCannotBeSerialized() {
    ObjectMapper failing = new ObjectMapper() {
      private static final long serialVersionUID = 1L;
      @Override
      public byte[] writeValueAsBytes(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
        throw new com.fasterxml.jackson.core.JsonGenerationException("cannot", (com.fasterxml.jackson.core.JsonGenerator) null);
      }
    };
    RequestExecutor broken = new RequestExecutor(HttpClients.createDefault(), failing);

    assertThatThrownBy(() -> broken.postRequest(baseUrl + "/tasks", new LockRequestDto("w", 1L), Void.class))
        .isInstanceOf(EngineClientException.class);
  }

}
