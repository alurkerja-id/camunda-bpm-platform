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
package org.camunda.bpm.container.impl.threading.ra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.resource.ResourceException;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.WorkManager;

import org.camunda.bpm.container.impl.threading.ra.commonj.CommonJWorkManagerExecutorService;
import org.camunda.bpm.container.impl.threading.ra.inflow.JobExecutionHandlerActivation;
import org.camunda.bpm.container.impl.threading.ra.inflow.JobExecutionHandlerActivationSpec;
import org.junit.Before;
import org.junit.Test;

public class JcaExecutorServiceConnectorTest {

  protected JcaExecutorServiceConnector connector;
  protected BootstrapContext context;
  protected WorkManager workManager;

  @Before
  public void setUp() throws Exception {
    connector = new JcaExecutorServiceConnector();
    context = mock(BootstrapContext.class);
    workManager = mock(WorkManager.class);
    when(context.getWorkManager()).thenReturn(workManager);
  }

  @Test
  public void shouldStartWithJcaWorkManagerByDefault() throws Exception {
    connector.start(context);

    Object executor = connector.getExecutorServiceWrapper().getExecutorService();
    assertThat(executor).isInstanceOf(JcaWorkManagerExecutorService.class);
    assertThat(((JcaWorkManagerExecutorService) executor).getWorkManager()).isSameAs(workManager);
    connector.stop();
  }

  @Test
  public void shouldStartWithCommonJWorkManagerWhenConfigured() throws Exception {
    connector.setIsUseCommonJWorkManager(true);
    connector.setCommonJWorkManagerName("wm/custom");

    connector.start(context);

    assertThat(connector.getIsUseCommonJWorkManager()).isTrue();
    assertThat(connector.getCommonJWorkManagerName()).isEqualTo("wm/custom");
    assertThat(connector.getExecutorServiceWrapper().getExecutorService()).isInstanceOf(CommonJWorkManagerExecutorService.class);
  }

  @Test
  public void shouldRejectCommonJConfigurationWithEmptyName() {
    connector.setIsUseCommonJWorkManager(true);
    connector.setCommonJWorkManagerName("");

    assertThatThrownBy(() -> connector.start(context)).isInstanceOf(RuntimeException.class)
        .hasMessageContaining("commonJWorkManagerName");
  }

  @Test
  public void shouldAllowSingleEndpointActivationAndDeactivation() throws Exception {
    MessageEndpointFactory factory = mock(MessageEndpointFactory.class);
    JobExecutionHandlerActivationSpec spec = new JobExecutionHandlerActivationSpec();

    connector.endpointActivation(factory, spec);

    JobExecutionHandlerActivation activation = connector.getJobHandlerActivation();
    assertThat(activation).isNotNull();
    assertThat(activation.getMessageEndpointFactory()).isSameAs(factory);
    assertThat(activation.getActivationSpec()).isSameAs(spec);
    assertThatThrownBy(() -> connector.endpointActivation(factory, spec)).isInstanceOf(ResourceException.class);

    connector.endpointDeactivation(factory, spec);
    assertThat(connector.getJobHandlerActivation()).isNull();
    connector.endpointDeactivation(factory, spec);
  }

  @Test
  public void shouldTreatAllConnectorsAsEqualAndHaveNoXaResources() throws Exception {
    JcaExecutorServiceConnector other = new JcaExecutorServiceConnector();

    assertThat(connector).isEqualTo(other).isEqualTo(connector).isNotEqualTo(null).isNotEqualTo("x");
    assertThat(connector.hashCode()).isEqualTo(other.hashCode());
    assertThat(connector.getXAResources(null)).isNull();
  }

  @Test
  public void shouldExposeActivationSpecProperties() throws Exception {
    JobExecutionHandlerActivationSpec spec = new JobExecutionHandlerActivationSpec();
    spec.setResourceAdapter(connector);
    spec.setDummyPojo("dummy");
    spec.validate();
    JobExecutionHandlerActivation activation = new JobExecutionHandlerActivation();
    activation.start();
    activation.stop();

    assertThat(spec.getResourceAdapter()).isSameAs(connector);
    assertThat(spec.getDummyPojo()).isEqualTo("dummy");
    assertThat(activation.getActivationSpec()).isNull();
  }

}
