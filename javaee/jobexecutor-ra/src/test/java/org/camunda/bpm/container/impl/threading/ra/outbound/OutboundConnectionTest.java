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
package org.camunda.bpm.container.impl.threading.ra.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnection;

import org.camunda.bpm.container.ExecutorService;
import org.camunda.bpm.container.impl.threading.ra.JcaExecutorServiceConnector;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class OutboundConnectionTest {

  protected ExecutorService delegate;
  protected JcaExecutorServiceConnector connector;
  protected JcaExecutorServiceManagedConnectionFactory factory;
  protected JcaExecutorServiceManagedConnection managedConnection;

  @Before
  public void setUp() throws Exception {
    delegate = mock(ExecutorService.class);
    connector = new JcaExecutorServiceConnector();
    java.lang.reflect.Method setter = connector.getExecutorServiceWrapper().getClass().getDeclaredMethod("setExecutorService", Object.class);
    setter.setAccessible(true);
    setter.invoke(connector.getExecutorServiceWrapper(), delegate);
    factory = new JcaExecutorServiceManagedConnectionFactory();
    factory.setResourceAdapter(connector);
    managedConnection = (JcaExecutorServiceManagedConnection) factory.createManagedConnection(null, null);
  }

  @Test
  public void shouldDelegateSchedulingThroughConnectionHandle() throws Exception {
    Runnable runnable = mock(Runnable.class);
    Runnable jobsRunnable = mock(Runnable.class);
    ProcessEngineImpl engine = mock(ProcessEngineImpl.class);
    List<String> jobIds = Collections.singletonList("j");
    when(delegate.schedule(runnable, true)).thenReturn(true);
    when(delegate.getExecuteJobsRunnable(jobIds, engine)).thenReturn(jobsRunnable);

    JcaExecutorServiceConnection connection = (JcaExecutorServiceConnection) managedConnection.getConnection(null, null);

    assertThat(connection.schedule(runnable, true)).isTrue();
    assertThat(connection.getExecuteJobsRunnable(jobIds, engine)).isSameAs(jobsRunnable);
  }

  @Test
  public void shouldNotifyListenersWhenHandleIsClosed() throws Exception {
    ConnectionEventListener listener = mock(ConnectionEventListener.class);
    managedConnection.addConnectionEventListener(listener);
    JcaExecutorServiceConnection connection = (JcaExecutorServiceConnection) managedConnection.getConnection(null, null);

    connection.closeConnection();

    ArgumentCaptor<ConnectionEvent> event = ArgumentCaptor.forClass(ConnectionEvent.class);
    verify(listener).connectionClosed(event.capture());
    assertThat(event.getValue().getId()).isEqualTo(ConnectionEvent.CONNECTION_CLOSED);
    assertThat(event.getValue().getConnectionHandle()).isSameAs(connection);

    managedConnection.removeConnectionEventListener(listener);
    connection.closeConnection();
    org.mockito.Mockito.verifyNoMoreInteractions(listener);
  }

  @Test
  public void shouldRejectNullListenersAndInvalidHandles() throws Exception {
    assertThatThrownBy(() -> managedConnection.addConnectionEventListener(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> managedConnection.removeConnectionEventListener(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> managedConnection.associateConnection(null)).isInstanceOf(ResourceException.class);
    assertThatThrownBy(() -> managedConnection.associateConnection("wrong")).isInstanceOf(ResourceException.class);

    Object handle = managedConnection.getConnection(null, null);
    managedConnection.associateConnection(handle);
  }

  @Test
  public void shouldDeclareUnsupportedManagedConnectionFeatures() throws Exception {
    StringWriter sink = new StringWriter();
    PrintWriter writer = new PrintWriter(sink);
    managedConnection.setLogWriter(writer);

    assertThat(managedConnection.getLogWriter()).isSameAs(writer);
    assertThat(managedConnection.getMetaData()).isNull();
    assertThatThrownBy(managedConnection::getLocalTransaction).isInstanceOf(NotSupportedException.class);
    assertThatThrownBy(managedConnection::getXAResource).isInstanceOf(NotSupportedException.class);
    managedConnection.cleanup();
    managedConnection.destroy();
  }

  @Test
  public void shouldCreateConnectionFactoryAndAllocateThroughConnectionManager() throws Exception {
    ConnectionManager manager = mock(ConnectionManager.class);
    JcaExecutorServiceConnection allocated = mock(JcaExecutorServiceConnection.class);
    when(manager.allocateConnection(factory, null)).thenReturn(allocated);

    JcaExecutorServiceConnectionFactoryImpl connectionFactory =
        (JcaExecutorServiceConnectionFactoryImpl) factory.createConnectionFactory(manager);
    javax.naming.Reference reference = new javax.naming.Reference("t");
    connectionFactory.setReference(reference);

    assertThat(connectionFactory.getConnection()).isSameAs(allocated);
    assertThat(connectionFactory.getReference()).isSameAs(reference);
    assertThatThrownBy(factory::createConnectionFactory).isInstanceOf(ResourceException.class);
  }

  @Test
  public void shouldMatchOnlyOwnManagedConnectionsAndCompareFactories() throws Exception {
    Set<ManagedConnection> candidates = new HashSet<>();
    candidates.add(mock(ManagedConnection.class));
    assertThat(factory.matchManagedConnections(candidates, null, null)).isNull();
    candidates.add(managedConnection);
    assertThat(factory.matchManagedConnections(candidates, null, null)).isSameAs(managedConnection);

    PrintWriter writer = new PrintWriter(new StringWriter());
    factory.setLogWriter(writer);
    assertThat(factory.getLogWriter()).isSameAs(writer);
    assertThat(factory.getResourceAdapter()).isSameAs(connector);
    JcaExecutorServiceManagedConnectionFactory other = new JcaExecutorServiceManagedConnectionFactory();
    assertThat(factory).isEqualTo(other).isEqualTo(factory).isNotEqualTo(null).isNotEqualTo("x");
    assertThat(factory.hashCode()).isEqualTo(other.hashCode());
  }

}
