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

import java.net.UnknownHostException;

import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.backoff.ExponentialBackoffStrategy;
import org.camunda.bpm.client.exception.ExternalTaskClientException;
import org.camunda.bpm.client.interceptor.auth.BasicAuthProvider;
import org.junit.Test;

public class ExternalTaskClientBuilderValidationTest {

  protected ExternalTaskClientBuilderImpl builder() {
    return (ExternalTaskClientBuilderImpl) new ExternalTaskClientBuilderImpl().baseUrl("http://localhost:8080/engine-rest")
        .disableAutoFetching();
  }

  @Test
  public void shouldBuildIdleClientWithDefaults() {
    ExternalTaskClientBuilderImpl builder = builder();

    ExternalTaskClient client = builder.build();

    assertThat(client.isActive()).isFalse();
    assertThat(builder.getMaxTasks()).isEqualTo(10);
    assertThat(builder.getLockDuration()).isEqualTo(20_000L);
    assertThat(builder.getAsyncResponseTimeout()).isNull();
    assertThat(builder.isAutoFetchingEnabled()).isFalse();
    assertThat(builder.getBackoffStrategy()).isInstanceOf(ExponentialBackoffStrategy.class);
    assertThat(builder.getWorkerId()).isNotEmpty();
    assertThat(builder.getEngineClient().isUsePriority()).isTrue();
    assertThat(builder.getObjectMapper()).isNotNull();
    assertThat(builder.getValueMappers()).isNotNull();
    assertThat(builder.getTypedValues()).isNotNull();
    assertThat(builder.getDefaultSerializationFormat()).isNotNull();
    assertThat(builder.getDateFormat()).isNotNull();
  }

  @Test
  public void shouldApplyConfiguredValues() {
    ExternalTaskClientBuilderImpl builder = builder();
    BasicAuthProvider auth = new BasicAuthProvider("u", "p");

    builder.workerId("worker-7").maxTasks(3).usePriority(false).asyncResponseTimeout(5000L).lockDuration(9000L)
        .addInterceptor(auth).defaultSerializationFormat("application/json").dateFormat("yyyy-MM-dd")
        .disableBackoffStrategy();
    ExternalTaskClient client = builder.build();

    assertThat(builder.getWorkerId()).isEqualTo("worker-7");
    assertThat(builder.getMaxTasks()).isEqualTo(3);
    assertThat(builder.getAsyncResponseTimeout()).isEqualTo(5000L);
    assertThat(builder.getLockDuration()).isEqualTo(9000L);
    assertThat(builder.getInterceptors()).containsExactly(auth);
    assertThat(builder.getEngineClient().isUsePriority()).isFalse();
    assertThat(builder.getDefaultSerializationFormat()).isEqualTo("application/json");
    assertThat(builder.getDateFormat()).isEqualTo("yyyy-MM-dd");
    assertThat(client.isActive()).isFalse();
  }

  @Test
  public void shouldSanitizeTrailingSlashesAndWhitespaceOfBaseUrl() {
    ExternalTaskClientBuilderImpl builder = (ExternalTaskClientBuilderImpl) new ExternalTaskClientBuilderImpl()
        .baseUrl("  http://localhost:8080/engine-rest//  ").disableAutoFetching();

    builder.build();

    assertThat(builder.getBaseUrl()).isEqualTo("http://localhost:8080/engine-rest");
  }

  @Test
  public void shouldRejectInvalidConfiguration() {
    assertThatThrownBy(() -> builder().maxTasks(0).build()).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> builder().asyncResponseTimeout(0L).build()).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> builder().lockDuration(0L).build()).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> new ExternalTaskClientBuilderImpl().build()).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> new ExternalTaskClientBuilderImpl().baseUrl("").build()).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> new ExternalTaskClientBuilderImpl().urlResolver(null).build())
        .isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> builder().addInterceptor(null).build()).isInstanceOf(ExternalTaskClientException.class);
  }

  @Test
  public void shouldValidateOrderingConfiguration() {
    assertThatThrownBy(() -> builder().orderByCreateTime().build()).isInstanceOf(ExternalTaskClientException.class);
    assertThatThrownBy(() -> builder().asc()).isInstanceOf(ExternalTaskClientException.class);

    builder().orderByCreateTime().asc().build();
    builder().orderByCreateTime().desc().build();
    builder().useCreateTime(true).build();
    builder().useCreateTime(false).build();
  }

  @Test
  public void shouldAcceptCustomUrlResolver() {
    ExternalTaskClientBuilderImpl builder = (ExternalTaskClientBuilderImpl) new ExternalTaskClientBuilderImpl()
        .urlResolver(() -> "http://resolved:1234").disableAutoFetching();

    builder.build();

    assertThat(builder.getBaseUrl()).isEqualTo("http://resolved:1234");
  }

  @Test
  public void shouldFailWorkerIdGenerationWhenHostnameCannotBeResolved() {
    ExternalTaskClientBuilderImpl builder = new ExternalTaskClientBuilderImpl() {
      @Override
      public String getHostname() throws UnknownHostException {
        throw new UnknownHostException("no host");
      }
    };
    builder.baseUrl("http://localhost:8080").disableAutoFetching();

    assertThatThrownBy(builder::build).isInstanceOf(ExternalTaskClientException.class);
  }

  @Test
  public void shouldStartBackgroundFetchingByDefaultAndStopCleanly() {
    ExternalTaskClient client = new ExternalTaskClientBuilderImpl().baseUrl("http://localhost:1/engine-rest").build();

    assertThat(client.isActive()).isTrue();
    client.stop();
    assertThat(client.isActive()).isFalse();
    client.start();
    assertThat(client.isActive()).isTrue();
    client.stop();
  }

}
