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

import org.junit.Test;

/**
 * Pins the defaults of {@link JobExecutionProperty}. They decide the thread pool the job executor
 * runs with when an application configures nothing, so a silent change here would resize a
 * production pool without anyone editing configuration.
 */
public class JobExecutionPropertyTest {

  @Test
  public void shouldDefaultTheThreadPoolToItsDocumentedSize() {
    JobExecutionProperty property = new JobExecutionProperty();

    assertThat(property.getCorePoolSize()).isEqualTo(3);
    assertThat(property.getMaxPoolSize()).isEqualTo(10);
    assertThat(property.getQueueCapacity()).isEqualTo(3);
  }

  @Test
  public void shouldLeaveEverythingElseToTheEngineDefaults() {
    JobExecutionProperty property = new JobExecutionProperty();

    assertThat(property.isEnabled()).isFalse();
    assertThat(property.isDeploymentAware()).isFalse();
    assertThat(property.getKeepAliveSeconds()).isNull();
    assertThat(property.getLockTimeInMillis()).isNull();
    assertThat(property.getMaxJobsPerAcquisition()).isNull();
    assertThat(property.getWaitTimeInMillis()).isNull();
    assertThat(property.getMaxWait()).isNull();
    assertThat(property.getBackoffTimeInMillis()).isNull();
    assertThat(property.getMaxBackoff()).isNull();
    assertThat(property.getBackoffDecreaseThreshold()).isNull();
    assertThat(property.getWaitIncreaseFactor()).isNull();
  }

  @Test
  public void shouldKeepEveryValueItIsGiven() {
    JobExecutionProperty property = new JobExecutionProperty();

    property.setEnabled(true);
    property.setDeploymentAware(true);
    property.setCorePoolSize(5);
    property.setMaxPoolSize(20);
    property.setQueueCapacity(7);
    property.setKeepAliveSeconds(30);
    property.setLockTimeInMillis(300000);
    property.setMaxJobsPerAcquisition(4);
    property.setWaitTimeInMillis(5000);
    property.setMaxWait(60000L);
    property.setBackoffTimeInMillis(10);
    property.setMaxBackoff(4000L);
    property.setBackoffDecreaseThreshold(100);
    property.setWaitIncreaseFactor(2.5f);

    assertThat(property.isEnabled()).isTrue();
    assertThat(property.isDeploymentAware()).isTrue();
    assertThat(property.getCorePoolSize()).isEqualTo(5);
    assertThat(property.getMaxPoolSize()).isEqualTo(20);
    assertThat(property.getQueueCapacity()).isEqualTo(7);
    assertThat(property.getKeepAliveSeconds()).isEqualTo(30);
    assertThat(property.getLockTimeInMillis()).isEqualTo(300000);
    assertThat(property.getMaxJobsPerAcquisition()).isEqualTo(4);
    assertThat(property.getWaitTimeInMillis()).isEqualTo(5000);
    assertThat(property.getMaxWait()).isEqualTo(60000L);
    assertThat(property.getBackoffTimeInMillis()).isEqualTo(10);
    assertThat(property.getMaxBackoff()).isEqualTo(4000L);
    assertThat(property.getBackoffDecreaseThreshold()).isEqualTo(100);
    assertThat(property.getWaitIncreaseFactor()).isEqualTo(2.5f);
  }

  @Test
  public void shouldRenderEveryFieldInToString() {
    JobExecutionProperty property = new JobExecutionProperty();
    property.setCorePoolSize(5);

    String rendered = property.toString();

    assertThat(rendered).contains("JobExecutionProperty");
    assertThat(rendered).contains("enabled=false").contains("corePoolSize=5")
        .contains("maxPoolSize=10").contains("queueCapacity=3")
        .contains("keepAliveSeconds=null").contains("waitIncreaseFactor=null");
  }
}
