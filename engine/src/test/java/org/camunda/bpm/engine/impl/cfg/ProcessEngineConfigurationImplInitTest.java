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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.RepositoryServiceImpl;
import org.camunda.bpm.engine.impl.ServiceImpl;
import org.camunda.bpm.engine.impl.batch.BatchJobHandler;
import org.camunda.bpm.engine.impl.cfg.auth.DefaultPermissionProvider;
import org.camunda.bpm.engine.impl.event.EventHandler;
import org.camunda.bpm.engine.impl.form.engine.FormEngine;
import org.camunda.bpm.engine.impl.form.type.AbstractFormFieldType;
import org.camunda.bpm.engine.impl.form.validator.FormFieldValidator;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.camunda.bpm.engine.impl.jobexecutor.DefaultJobPriorityProvider;
import org.camunda.bpm.engine.impl.persistence.deploy.Deployer;
import org.camunda.bpm.engine.impl.externaltask.DefaultExternalTaskPriorityProvider;
import org.camunda.bpm.engine.impl.scripting.engine.ScriptEngineResolver;
import org.camunda.bpm.engine.impl.scripting.engine.ScriptingEngines;
import org.camunda.bpm.engine.impl.util.ReflectUtil;
import org.camunda.bpm.engine.impl.variable.serializer.TypedValueSerializer;
import org.camunda.bpm.engine.impl.variable.serializer.VariableSerializers;
import org.camunda.bpm.engine.impl.db.sql.DbSqlSessionFactory;
import org.camunda.bpm.engine.impl.cfg.multitenancy.TenantCommandChecker;
import org.camunda.bpm.engine.impl.migration.validation.activity.MigrationActivityValidator;
import org.camunda.bpm.engine.impl.migration.validation.instance.MigratingActivityInstanceValidator;
import org.camunda.bpm.engine.impl.migration.validation.instruction.MigrationInstructionValidator;
import org.camunda.bpm.engine.impl.util.ParseUtil;
import org.camunda.bpm.engine.impl.jobexecutor.JobHandler;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.impl.jobexecutor.RejectedJobsHandler;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.auth.ResourceAuthorizationProvider;
import org.camunda.bpm.engine.impl.Page;
import org.junit.Test;

/** Drives the individual init steps of the configuration in isolation, one branch at a time. */
public class ProcessEngineConfigurationImplInitTest {

  private static ProcessEngineConfigurationImpl config() {
    return new StandaloneInMemProcessEngineConfiguration();
  }

  // -- deserialization type validator -------------------------------------------------------------

  @Test
  public void shouldCreateDefaultTypeValidator() {
    ProcessEngineConfigurationImpl config = config();

    config.initTypeValidator();

    assertThat(config.getDeserializationTypeValidator()).isNotNull();
  }

  @Test
  public void shouldPassAllowListsToWhitelistingValidator() {
    ProcessEngineConfigurationImpl config = config();
    org.camunda.bpm.engine.runtime.WhitelistingDeserializationTypeValidator validator =
        mock(org.camunda.bpm.engine.runtime.WhitelistingDeserializationTypeValidator.class);
    config.setDeserializationTypeValidator(validator);
    config.setDeserializationAllowedClasses("a.B,c.D");
    config.setDeserializationAllowedPackages("org.example");

    config.initTypeValidator();

    assertThat(config.getDeserializationTypeValidator()).isSameAs(validator);
    verify(validator).setAllowedClasses("a.B,c.D");
    verify(validator).setAllowedPackages("org.example");
  }

  // -- history clean-up -----------------------------------------------------------------------------

  @Test
  public void shouldBuildBatchWindowsForEveryConfiguredWeekday() {
    ProcessEngineConfigurationImpl config = config();
    config.setHistoryCleanupBatchWindowStartTime("20:00");
    config.setHistoryCleanupBatchWindowEndTime("23:00");
    config.setMondayHistoryCleanupBatchWindowStartTime("01:00");
    config.setTuesdayHistoryCleanupBatchWindowStartTime("02:00");
    config.setWednesdayHistoryCleanupBatchWindowStartTime("03:00");
    config.setThursdayHistoryCleanupBatchWindowStartTime("04:00");
    config.setFridayHistoryCleanupBatchWindowStartTime("05:00");
    config.setSaturdayHistoryCleanupBatchWindowStartTime("06:00");
    config.setSundayHistoryCleanupBatchWindowStartTime("07:00");

    config.initHistoryCleanup();

    assertThat(config.getHistoryCleanupBatchWindows()).containsOnlyKeys(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY);
    assertThat(config.getHistoryCleanupBatchWindows().get(Calendar.WEDNESDAY).getStartTime()).isEqualTo("03:00");
    assertThat(config.getHistoryCleanupBatchWindowStartTimeAsDate()).isNotNull();
    assertThat(config.getHistoryCleanupBatchWindowEndTimeAsDate()).isNotNull();
  }

  @Test
  public void shouldRejectInvalidJobLogTimeToLive() {
    ProcessEngineConfigurationImpl config = config();
    config.setHistoryCleanupJobLogTimeToLive("not-a-ttl");

    assertThatThrownBy(config::initHistoryCleanupJobLogTimeToLive)
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("historyCleanupJobLogTimeToLive");
  }

  // -- handlers, interceptors -------------------------------------------------------------------------

  @Test
  public void shouldRegisterCustomBatchJobHandlers() {
    ProcessEngineConfigurationImpl config = config();
    BatchJobHandler<?> custom = mock(BatchJobHandler.class);
    when(custom.getType()).thenReturn("custom-batch");
    config.setCustomBatchJobHandlers(Collections.<BatchJobHandler<?>>singletonList(custom));

    config.initBatchHandlers();

    assertThat(config.getBatchHandlers().get("custom-batch")).isSameAs(custom);
  }

  @Test
  public void shouldWrapCustomInterceptorsAroundDefaultsForTxRequired() {
    ProcessEngineConfigurationImpl config = config();
    CommandInterceptor pre = mock(CommandInterceptor.class);
    CommandInterceptor post = mock(CommandInterceptor.class);
    config.setCustomPreCommandInterceptorsTxRequired(Collections.singletonList(pre));
    config.setCustomPostCommandInterceptorsTxRequired(Collections.singletonList(post));
    config.actualCommandExecutor = mock(CommandInterceptor.class);

    config.initCommandInterceptorsTxRequired();

    List<CommandInterceptor> chain = config.commandInterceptorsTxRequired;
    assertThat(chain.get(0)).isSameAs(pre);
    assertThat(chain.get(chain.size() - 2)).isSameAs(post);
    assertThat(chain.get(chain.size() - 1)).isSameAs(config.actualCommandExecutor);
  }

  @Test
  public void shouldWrapCustomInterceptorsAroundDefaultsForTxRequiresNew() {
    ProcessEngineConfigurationImpl config = config();
    CommandInterceptor pre = mock(CommandInterceptor.class);
    CommandInterceptor post = mock(CommandInterceptor.class);
    config.setCustomPreCommandInterceptorsTxRequiresNew(Collections.singletonList(pre));
    config.setCustomPostCommandInterceptorsTxRequiresNew(Collections.singletonList(post));
    config.actualCommandExecutor = mock(CommandInterceptor.class);

    config.initCommandInterceptorsTxRequiresNew();

    List<CommandInterceptor> chain = config.commandInterceptorsTxRequiresNew;
    assertThat(chain.get(0)).isSameAs(pre);
    assertThat(chain.get(chain.size() - 2)).isSameAs(post);
    assertThat(chain.get(chain.size() - 1)).isSameAs(config.actualCommandExecutor);
  }

  @Test
  public void shouldDefaultToEmptyCustomInterceptorLists() {
    ProcessEngineConfigurationImpl config = config();
    config.actualCommandExecutor = mock(CommandInterceptor.class);

    config.initCommandInterceptorsTxRequired();
    config.initCommandInterceptorsTxRequiresNew();

    assertThat(config.commandInterceptorsTxRequired).isNotEmpty().endsWith(config.actualCommandExecutor);
    assertThat(config.commandInterceptorsTxRequiresNew).isNotEmpty().endsWith(config.actualCommandExecutor);
  }

  @Test
  public void shouldRejectEmptyInterceptorChain() {
    ProcessEngineConfigurationImpl config = config();

    assertThatThrownBy(() -> config.initInterceptorChain(null))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessage("invalid command interceptor chain configuration: null");
    assertThatThrownBy(() -> config.initInterceptorChain(new ArrayList<>()))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessage("invalid command interceptor chain configuration: []");
  }

  @Test
  public void shouldInjectCommandExecutorAndCharsetIntoServices() {
    ProcessEngineConfigurationImpl config = config();
    config.commandExecutorTxRequired = mock(CommandExecutor.class);
    config.initDefaultCharset();
    ServiceImpl plain = mock(ServiceImpl.class);
    RepositoryServiceImpl repository = mock(RepositoryServiceImpl.class);

    config.initService(plain);
    config.initService(repository);
    config.initService(new Object());

    verify(plain).setCommandExecutor(config.commandExecutorTxRequired);
    verify(repository).setCommandExecutor(config.commandExecutorTxRequired);
    verify(repository).setDeploymentCharset(config.getDefaultCharset());
  }

  // -- data source ------------------------------------------------------------------------------------

  @Test
  public void shouldFailWhenDataSourceJndiNameCannotBeResolved() {
    ProcessEngineConfigurationImpl config = config();
    config.setDataSourceJndiName("java:/jdbc/missing");

    assertThatThrownBy(config::initDataSource)
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("couldn't lookup datasource from java:/jdbc/missing");
  }

  @Test
  public void shouldRequireDriverAndUserForJdbcUrl() {
    ProcessEngineConfigurationImpl config = config();
    config.setJdbcUrl("jdbc:h2:mem:init-test");
    config.setJdbcDriver(null);

    assertThatThrownBy(config::initDataSource)
      .isInstanceOf(ProcessEngineException.class)
      .hasMessage("DataSource or JDBC properties have to be specified in a process engine configuration");
  }

  @Test
  public void shouldConfigurePooledDataSourceFromJdbcProperties() {
    ProcessEngineConfigurationImpl config = config();
    config.setJdbcUrl("jdbc:h2:mem:init-test-pool");
    config.setJdbcDriver("org.h2.Driver");
    config.setJdbcUsername("sa");
    config.setJdbcPassword("");
    config.setJdbcMaxActiveConnections(7);
    config.setJdbcMaxIdleConnections(3);
    config.setJdbcMaxCheckoutTime(11000);
    config.setJdbcMaxWaitTime(12000);
    config.setJdbcPingEnabled(true);
    config.setJdbcPingQuery("select 1");
    config.setJdbcPingConnectionNotUsedFor(13);

    config.initDataSource();

    assertThat(config.getDataSource()).isInstanceOf(PooledDataSource.class);
    PooledDataSource pool = (PooledDataSource) config.getDataSource();
    assertThat(pool.getPoolMaximumActiveConnections()).isEqualTo(7);
    assertThat(pool.getPoolMaximumIdleConnections()).isEqualTo(3);
    assertThat(pool.getPoolMaximumCheckoutTime()).isEqualTo(11000);
    assertThat(pool.getPoolTimeToWait()).isEqualTo(12000);
    assertThat(pool.isPoolPingEnabled()).isTrue();
    assertThat(pool.getPoolPingQuery()).isEqualTo("select 1");
    assertThat(pool.getPoolPingConnectionsNotUsedFor()).isEqualTo(13);
  }

  @Test
  public void shouldForceCloseOnlyPooledDataSources() {
    ProcessEngineConfigurationImpl config = config();
    PooledDataSource pool = mock(PooledDataSource.class);
    config.setDataSource(pool);

    config.setForceCloseMybatisConnectionPool(false);
    config.close();
    config.setForceCloseMybatisConnectionPool(true);
    config.close();

    verify(pool, org.mockito.Mockito.times(1)).forceCloseAll();

    config.setDataSource(mock(javax.sql.DataSource.class));
    config.close();
  }

  @Test
  public void shouldPickTransactionFactoryByTransactionManagement() {
    ProcessEngineConfigurationImpl managed = config();
    managed.setTransactionsExternallyManaged(true);
    managed.initTransactionFactory();

    ProcessEngineConfigurationImpl standalone = config();
    standalone.setTransactionsExternallyManaged(false);
    standalone.initTransactionFactory();

    assertThat(managed.transactionFactory).isInstanceOf(ManagedTransactionFactory.class);
    assertThat(standalone.transactionFactory).isInstanceOf(JdbcTransactionFactory.class);
  }

  @Test
  public void shouldRejectPrefixThatDoesNotStartWithSchema() {
    ProcessEngineConfigurationImpl config = config();

    config.ensurePrefixAndSchemaFitToegether("any.", null);
    config.ensurePrefixAndSchemaFitToegether("schema.", "schema");

    assertThatThrownBy(() -> config.ensurePrefixAndSchemaFitToegether("other.", "schema")).isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("Received schema: schema prefix: other.");
    assertThatThrownBy(() -> config.ensurePrefixAndSchemaFitToegether(null, "schema")).isInstanceOf(ProcessEngineException.class);
  }

  // -- migration validators ---------------------------------------------------------------------------

  // -- deployers ----------------------------------------------------------------------------------------

  @Test
  public void shouldHonourCustomDeployersJobHandlersAndRejectionHandlerOnEngineBuild() {
    ProcessEngineConfigurationImpl config = new StandaloneInMemProcessEngineConfiguration();
    config.setJdbcUrl("jdbc:h2:mem:init-test-engine-build;DB_CLOSE_DELAY=-1");
    config.setJobExecutorActivate(false);
    Deployer pre = mock(Deployer.class);
    Deployer post = mock(Deployer.class);
    config.setCustomPreDeployers(new ArrayList<>(Collections.singletonList(pre)));
    config.setCustomPostDeployers(new ArrayList<>(Collections.singletonList(post)));
    JobHandler<?> jobHandler = mock(JobHandler.class);
    when(jobHandler.getType()).thenReturn("custom-job");
    config.setCustomJobHandlers(new ArrayList<JobHandler>(Collections.<JobHandler>singletonList(jobHandler)));
    RejectedJobsHandler rejected = mock(RejectedJobsHandler.class);
    config.setCustomRejectedJobsHandler(rejected);
    BpmnParseListener preListener = mock(BpmnParseListener.class);
    BpmnParseListener postListener = mock(BpmnParseListener.class);
    config.setPreParseListeners(new ArrayList<>(Collections.singletonList(preListener)));
    config.setPostParseListeners(new ArrayList<>(Collections.singletonList(postListener)));
    MigrationInstructionValidator preInstruction = mock(MigrationInstructionValidator.class);
    MigrationInstructionValidator postInstruction = mock(MigrationInstructionValidator.class);
    MigratingActivityInstanceValidator preInstance = mock(MigratingActivityInstanceValidator.class);
    MigratingActivityInstanceValidator postInstance = mock(MigratingActivityInstanceValidator.class);
    config.setCustomPreMigrationInstructionValidators(new ArrayList<>(Collections.singletonList(preInstruction)));
    config.setCustomPostMigrationInstructionValidators(new ArrayList<>(Collections.singletonList(postInstruction)));
    config.setCustomPreMigratingActivityInstanceValidators(new ArrayList<>(Collections.singletonList(preInstance)));
    config.setCustomPostMigratingActivityInstanceValidators(new ArrayList<>(Collections.singletonList(postInstance)));
    config.setCustomPreMigrationActivityValidators(new ArrayList<>(Collections.singletonList(mock(MigrationActivityValidator.class))));
    config.setCustomPostMigrationActivityValidators(new ArrayList<>(Collections.singletonList(mock(MigrationActivityValidator.class))));

    org.camunda.bpm.engine.ProcessEngine engine = config.buildProcessEngine();
    try {
      assertThat(config.getDeployers()).startsWith(pre).endsWith(post);
      assertThat(config.getJobHandlers().get("custom-job")).isSameAs(jobHandler);
      assertThat(config.getJobExecutor().getRejectedJobsHandler()).isSameAs(rejected);
      List<BpmnParseListener> listeners = config.getBpmnDeployer().getBpmnParser().getParseListeners();
      assertThat(listeners).contains(preListener, postListener);
      assertThat(listeners.indexOf(preListener)).isLessThan(listeners.indexOf(postListener));
      assertThat(config.getMigrationInstructionValidators()).startsWith(preInstruction).endsWith(postInstruction);
      assertThat(config.getMigratingActivityInstanceValidators()).startsWith(preInstance).endsWith(postInstance);
    } finally {
      engine.close();
    }
  }

  // -- job executor / prioritisation ----------------------------------------------------------------------

  @Test
  public void shouldCreatePriorityProvidersOnlyWhenEnabled() {
    ProcessEngineConfigurationImpl config = config();
    config.setProducePrioritizedJobs(false);
    config.setProducePrioritizedExternalTasks(false);
    config.initJobProvider();
    config.initExternalTaskPriorityProvider();
    assertThat(config.getJobPriorityProvider()).isNull();
    assertThat(config.getExternalTaskPriorityProvider()).isNull();

    config.setProducePrioritizedJobs(true);
    config.setProducePrioritizedExternalTasks(true);
    config.initJobProvider();
    config.initExternalTaskPriorityProvider();

    assertThat(config.getJobPriorityProvider()).isInstanceOf(DefaultJobPriorityProvider.class);
    assertThat(config.getExternalTaskPriorityProvider()).isInstanceOf(DefaultExternalTaskPriorityProvider.class);
  }

  // -- history level, charset, admin groups -----------------------------------------------------------------

  @Test
  public void shouldRejectUnknownHistoryLevel() {
    ProcessEngineConfigurationImpl config = config();
    config.setHistory("bogus");

    assertThatThrownBy(config::initHistoryLevel)
      .isInstanceOf(ProcessEngineException.class)
      .hasMessage("invalid history level: bogus");
  }

  @Test
  public void shouldReturnDefaultHistoryLevelOnlyWhenLevelsAreKnown() {
    ProcessEngineConfigurationImpl config = config();
    config.historyLevels = null;
    assertThat(config.getDefaultHistoryLevel()).isNull();

    config.historyLevels = new ArrayList<>(Arrays.asList(HistoryLevel.HISTORY_LEVEL_NONE, HistoryLevel.HISTORY_LEVEL_AUDIT, HistoryLevel.HISTORY_LEVEL_FULL));
    assertThat(config.getDefaultHistoryLevel()).isSameAs(HistoryLevel.HISTORY_LEVEL_AUDIT);

    config.historyLevels = Collections.singletonList(HistoryLevel.HISTORY_LEVEL_NONE);
    assertThat(config.getDefaultHistoryLevel()).isNull();
  }

  @Test
  public void shouldDefaultAndOverrideCharset() {
    ProcessEngineConfigurationImpl config = config();
    config.initDefaultCharset();
    assertThat(config.defaultCharsetName).isEqualTo("UTF-8");

    ProcessEngineConfigurationImpl custom = config();
    assertThat(custom.setDefaultCharsetName("ISO-8859-1")).isSameAs(custom);
    custom.initDefaultCharset();
    assertThat(custom.getDefaultCharset().name()).isEqualTo("ISO-8859-1");
  }

  @Test
  public void shouldEnsureCamundaAdminGroupIsPresent() {
    ProcessEngineConfigurationImpl config = config();
    config.setAdminGroups(null);
    config.initAdminGroups();
    assertThat(config.getAdminGroups()).containsExactly(org.camunda.bpm.engine.authorization.Groups.CAMUNDA_ADMIN);

    config.setAdminGroups(new ArrayList<>(Arrays.asList("ops")));
    config.initAdminGroups();
    assertThat(config.getAdminGroups()).containsExactly("ops", org.camunda.bpm.engine.authorization.Groups.CAMUNDA_ADMIN);

    config.initAdminGroups();
    assertThat(config.getAdminGroups()).hasSize(2);
  }

  // -- custom registrations -------------------------------------------------------------------------------------

  @Test
  public void shouldRegisterCustomVariableSerializer() {
    ProcessEngineConfigurationImpl config = config();
    TypedValueSerializer<?> serializer = mock(TypedValueSerializer.class);
    when(serializer.getName()).thenReturn("custom-serializer");
    when(serializer.getType()).thenReturn(org.camunda.bpm.engine.variable.type.ValueType.STRING);
    config.setCustomPostVariableSerializers(Collections.<TypedValueSerializer>singletonList(serializer));

    config.initSerialization();

    assertThat(config.getVariableSerializers().getSerializerByName("custom-serializer")).isSameAs(serializer);
  }

  @Test
  public void shouldAcceptPrebuiltVariableSerializers() {
    ProcessEngineConfigurationImpl config = config();
    VariableSerializers serializers = new org.camunda.bpm.engine.impl.variable.serializer.DefaultVariableSerializers();

    assertThat(config.setVariableTypes(serializers)).isSameAs(config);
    assertThat(config.getVariableSerializers()).isSameAs(serializers);
  }

  @Test
  public void shouldRegisterCustomFormEngineTypeAndValidator() {
    ProcessEngineConfigurationImpl config = config();
    FormEngine engine = mock(FormEngine.class);
    when(engine.getName()).thenReturn("custom-form-engine");
    AbstractFormFieldType type = mock(AbstractFormFieldType.class);
    when(type.getName()).thenReturn("custom-type");
    config.setCustomFormEngines(Collections.singletonList(engine));
    config.setCustomFormTypes(Collections.singletonList(type));
    HashMap<String, Class<? extends FormFieldValidator>> validators = new HashMap<>();
    validators.put("custom-validator", org.camunda.bpm.engine.impl.form.validator.RequiredValidator.class);
    config.setCustomFormFieldValidators(validators);

    config.initFormEngines();
    config.initFormTypes();
    config.initFormFieldValidators();

    assertThat(config.getFormEngines()).containsEntry("custom-form-engine", engine);
    assertThat(config.getFormTypes().getFormType("custom-type")).isSameAs(type);
    org.camunda.bpm.engine.impl.util.xml.Element constraint = mock(org.camunda.bpm.engine.impl.util.xml.Element.class);
    when(constraint.attribute("name")).thenReturn("custom-validator");
    assertThat(config.getFormValidators().createValidator(constraint, null, null))
      .isInstanceOf(org.camunda.bpm.engine.impl.form.validator.RequiredValidator.class);
  }

  @Test
  public void shouldRegisterCustomEventHandler() {
    ProcessEngineConfigurationImpl config = config();
    EventHandler handler = mock(EventHandler.class);
    when(handler.getEventHandlerType()).thenReturn("custom-event");
    config.setCustomEventHandlers(Collections.singletonList(handler));

    config.initEventHandlers();

    assertThat(config.getEventHandlers().get("custom-event")).isSameAs(handler);
  }

  @Test
  public void shouldPropagateScriptEngineResolverToExistingScriptingEngines() {
    ProcessEngineConfigurationImpl config = config();
    ScriptEngineResolver resolver = mock(ScriptEngineResolver.class);
    ScriptingEngines scriptingEngines = mock(ScriptingEngines.class);

    assertThat(config.setScriptEngineResolver(resolver)).isSameAs(config);
    config.setScriptingEngines(scriptingEngines);
    config.setScriptEngineResolver(resolver);

    verify(scriptingEngines).setScriptEngineResolver(resolver);
  }
}
