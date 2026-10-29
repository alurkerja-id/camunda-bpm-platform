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
package org.camunda.bpm.engine.impl.cfg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import javax.naming.NamingException;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.jta.JakartaTransactionContextFactory;
import org.camunda.bpm.engine.impl.cfg.jta.JtaTransactionContextFactory;
import org.camunda.bpm.engine.impl.cfg.standalone.StandaloneTransactionContextFactory;
import org.camunda.bpm.engine.impl.interceptor.CommandContextFactory;
import org.camunda.bpm.engine.impl.interceptor.CommandContextInterceptor;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.camunda.bpm.engine.impl.interceptor.ExceptionCodeInterceptor;
import org.camunda.bpm.engine.impl.interceptor.JakartaTransactionInterceptor;
import org.camunda.bpm.engine.impl.interceptor.JtaTransactionInterceptor;
import org.camunda.bpm.engine.impl.interceptor.LogInterceptor;
import org.camunda.bpm.engine.impl.interceptor.TxContextCommandContextFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TransactionProcessEngineConfigurationTest {

  @Parameters(name = "{0}")
  public static Collection<Object[]> variants() {
    return Arrays.asList(new Object[][] {
      {"jta", (Supplier<AbstractTransactionProcessEngineConfiguration>) JtaProcessEngineConfiguration::new,
        javax.transaction.TransactionManager.class, JtaTransactionInterceptor.class, JtaTransactionContextFactory.class},
      {"jakarta", (Supplier<AbstractTransactionProcessEngineConfiguration>) JakartaTransactionProcessEngineConfiguration::new,
        jakarta.transaction.TransactionManager.class, JakartaTransactionInterceptor.class, JakartaTransactionContextFactory.class}
    });
  }

  private final Supplier<AbstractTransactionProcessEngineConfiguration> factory;
  private final Class<?> transactionManagerType;
  private final Class<?> interceptorType;
  private final Class<?> contextFactoryType;

  public TransactionProcessEngineConfigurationTest(String name, Supplier<AbstractTransactionProcessEngineConfiguration> factory,
      Class<?> transactionManagerType, Class<?> interceptorType, Class<?> contextFactoryType) {
    this.factory = factory;
    this.transactionManagerType = transactionManagerType;
    this.interceptorType = interceptorType;
    this.contextFactoryType = contextFactoryType;
  }

  private Object transactionManager(AbstractTransactionProcessEngineConfiguration config) throws Exception {
    return config.getClass().getMethod("getTransactionManager").invoke(config);
  }

  private Object withMockTransactionManager(AbstractTransactionProcessEngineConfiguration config) throws Exception {
    Object tm = mock(transactionManagerType);
    Method setter = config.getClass().getMethod("setTransactionManager", transactionManagerType);
    setter.invoke(config, tm);
    return tm;
  }

  @Test
  public void shouldDefaultToExternallyManagedTransactions() {
    AbstractTransactionProcessEngineConfiguration config = factory.get();

    assertThat(config.isTransactionsExternallyManaged()).isTrue();
    assertThat(config.getTransactionManagerJndiName()).isNull();
    assertThat(config.getDbSchemaOperationsCommandContextFactory()).isNull();
  }

  @Test
  public void shouldStoreJndiNameAndSchemaFactory() {
    AbstractTransactionProcessEngineConfiguration config = factory.get();
    CommandContextFactory schemaFactory = mock(CommandContextFactory.class);

    config.setTransactionManagerJndiName("java:/TransactionManager");
    config.setDbSchemaOperationsCommandContextFactory(schemaFactory);

    assertThat(config.getTransactionManagerJndiName()).isEqualTo("java:/TransactionManager");
    assertThat(config.getDbSchemaOperationsCommandContextFactory()).isSameAs(schemaFactory);
  }

  @Test
  public void shouldRejectMissingTransactionManagerAndJndiName() {
    AbstractTransactionProcessEngineConfiguration config = factory.get();

    assertThatThrownBy(config::initTransactionManager)
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("Property 'transactionManager' is null and 'transactionManagerJndiName' is not set");

    config.setTransactionManagerJndiName("");
    assertThatThrownBy(config::initTransactionManager)
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("Property 'transactionManager' is null");
  }

  @Test
  public void shouldFailWhenJndiLookupCannotResolve() {
    AbstractTransactionProcessEngineConfiguration config = factory.get();
    config.setTransactionManagerJndiName("java:/does/not/exist");

    assertThatThrownBy(config::initTransactionManager)
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("Cannot lookup instance of Jta Transaction manager in JNDI using name 'java:/does/not/exist'")
      .hasCauseInstanceOf(NamingException.class);
  }

  @Test
  public void shouldKeepConfiguredTransactionManager() throws Exception {
    AbstractTransactionProcessEngineConfiguration config = factory.get();
    Object tm = withMockTransactionManager(config);

    config.initTransactionManager();

    assertThat(transactionManager(config)).isSameAs(tm);
  }

  @Test
  public void shouldFailInitWithoutTransactionManager() {
    AbstractTransactionProcessEngineConfiguration config = factory.get();

    assertThatThrownBy(config::init).isInstanceOf(ProcessEngineException.class).hasMessageContaining("transactionManager");
    assertThat(config.getDbSchemaOperationsCommandContextFactory()).isNull();
  }

  @Test
  public void shouldCreateTransactionContextFactoryOnce() throws Exception {
    AbstractTransactionProcessEngineConfiguration config = factory.get();
    withMockTransactionManager(config);

    config.initTransactionContextFactory();
    Object created = config.getTransactionContextFactory();
    config.initTransactionContextFactory();

    assertThat(created).isInstanceOf(contextFactoryType);
    assertThat(config.getTransactionContextFactory()).isSameAs(created);
  }

  @Test
  public void shouldKeepCustomTransactionContextFactory() throws Exception {
    AbstractTransactionProcessEngineConfiguration config = factory.get();
    withMockTransactionManager(config);
    StandaloneTransactionContextFactory custom = new StandaloneTransactionContextFactory();
    config.setTransactionContextFactory(custom);

    config.initTransactionContextFactory();

    assertThat(config.getTransactionContextFactory()).isSameAs(custom);
  }

  @Test
  public void shouldCreateTransactionInterceptorForBothPropagations() throws Exception {
    AbstractTransactionProcessEngineConfiguration config = factory.get();
    withMockTransactionManager(config);

    assertThat(config.createTransactionInterceptor(false)).isInstanceOf(interceptorType);
    assertThat(config.createTransactionInterceptor(true)).isInstanceOf(interceptorType);
  }

  private void assertChain(List<CommandInterceptor> chain, boolean exceptionCodeExpected) {
    int offset = exceptionCodeExpected ? 1 : 0;
    assertThat(chain).hasSize(5 + offset);
    if (exceptionCodeExpected) {
      assertThat(chain.get(0)).isInstanceOf(ExceptionCodeInterceptor.class);
    }
    assertThat(chain.get(offset)).isInstanceOf(LogInterceptor.class);
    assertThat(chain.get(offset + 3)).isInstanceOf(interceptorType);
    assertThat(chain.get(offset + 4)).isInstanceOf(CommandContextInterceptor.class);
  }

  @Test
  public void shouldBuildDefaultInterceptorsForTxRequired() throws Exception {
    AbstractTransactionProcessEngineConfiguration config = factory.get();
    withMockTransactionManager(config);

    config.setDisableExceptionCode(false);
    assertChain(new ArrayList<>(config.getDefaultCommandInterceptorsTxRequired()), true);

    config.setDisableExceptionCode(true);
    assertChain(new ArrayList<>(config.getDefaultCommandInterceptorsTxRequired()), false);
  }

  @Test
  public void shouldBuildDefaultInterceptorsForTxRequiresNew() throws Exception {
    AbstractTransactionProcessEngineConfiguration config = factory.get();
    withMockTransactionManager(config);

    config.setDisableExceptionCode(false);
    assertChain(new ArrayList<>(config.getDefaultCommandInterceptorsTxRequiresNew()), true);

    config.setDisableExceptionCode(true);
    assertChain(new ArrayList<>(config.getDefaultCommandInterceptorsTxRequiresNew()), false);
  }

  @Test
  public void shouldCreateSchemaOperationsCommandContextFactoryOnce() {
    AbstractTransactionProcessEngineConfiguration config = factory.get();

    config.initDbSchemaOperationsCommandContextFactory();
    CommandContextFactory created = config.getDbSchemaOperationsCommandContextFactory();
    config.initDbSchemaOperationsCommandContextFactory();

    assertThat(created).isInstanceOf(TxContextCommandContextFactory.class);
    assertThat(config.getDbSchemaOperationsCommandContextFactory()).isSameAs(created);
  }

  @Test
  public void shouldKeepCustomSchemaOperationsCommandContextFactory() {
    AbstractTransactionProcessEngineConfiguration config = factory.get();
    CommandContextFactory custom = mock(CommandContextFactory.class);
    config.setDbSchemaOperationsCommandContextFactory(custom);

    config.initDbSchemaOperationsCommandContextFactory();

    assertThat(config.getDbSchemaOperationsCommandContextFactory()).isSameAs(custom);
  }

  @Test
  public void shouldChainSchemaOperationsExecutorOnce() {
    AbstractTransactionProcessEngineConfiguration config = factory.get();
    config.initDbSchemaOperationsCommandContextFactory();
    config.actualCommandExecutor = mock(CommandInterceptor.class);

    config.initCommandExecutorDbSchemaOperations();
    CommandExecutor chain = config.commandExecutorSchemaOperations;
    config.initCommandExecutorDbSchemaOperations();

    assertThat(chain).isInstanceOf(LogInterceptor.class);
    assertThat(config.commandExecutorSchemaOperations).isSameAs(chain);
  }
}
