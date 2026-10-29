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
package org.camunda.bpm.engine.impl.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cmmn.behavior.CaseControlRuleImpl;
import org.camunda.bpm.engine.impl.db.DbIdGenerator;
import org.camunda.bpm.engine.impl.db.entitymanager.DbEntityManager;
import org.camunda.bpm.engine.impl.db.sql.DbSqlSession;
import org.camunda.bpm.engine.impl.db.sql.DbSqlSessionFactory;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.impl.persistence.entity.PropertyEntity;
import org.camunda.bpm.engine.impl.db.PersistenceSession;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.JobQuery;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.Test;

public class TestHelperCoverageTest {

  private static final String EXPLICIT = "org/camunda/bpm/engine/impl/test/TestHelperCoverage.bpmn20.xml";

  private final ProcessEngine engine = PluggableProcessEngineTestCase.getProcessEngine();

  // -- deployment annotations -------------------------------------------------------------

  @Deployment(resources = EXPLICIT)
  public void explicitOnMethod() {
  }

  public void undecorated() {
  }

  @Deployment
  public void conventionMethod() {
  }

  @Deployment(resources = EXPLICIT)
  static class ClassLevel {
    public void somewhere() {
    }
  }

  static class InheritsClassLevel extends ClassLevel {
  }

  private long deploymentCount() {
    return engine.getRepositoryService().createDeploymentQuery().count();
  }

  @Test
  public void shouldDeployFromAnnotationOnMethod() {
    String id = TestHelper.annotationDeploymentSetUp(engine, TestHelperCoverageTest.class, "explicitOnMethod");
    try {
      assertThat(id).isNotNull();
      assertThat(engine.getRepositoryService().createDeploymentQuery().deploymentId(id).singleResult().getName())
        .isEqualTo("TestHelperCoverageTest.explicitOnMethod");
    } finally {
      TestHelper.annotationDeploymentTearDown(engine, id, TestHelperCoverageTest.class, "explicitOnMethod");
    }
    assertThat(engine.getRepositoryService().createDeploymentQuery().deploymentId(id).count()).isZero();
  }

  @Test
  public void shouldDeployByNamingConventionForMethod() {
    String id = TestHelper.annotationDeploymentSetUp(engine, TestHelperCoverageTest.class, "conventionMethod");
    try {
      assertThat(engine.getRepositoryService().getDeploymentResourceNames(id))
        .containsExactly("org/camunda/bpm/engine/impl/test/TestHelperCoverageTest.conventionMethod.bpmn20.xml");
    } finally {
      TestHelper.deleteDeployment(engine, id);
    }
  }

  @Test
  public void shouldDeployByNamingConventionForClass() {
    String id = TestHelper.annotationDeploymentSetUp(engine, new String[0], TestHelperCoverageTest.class, false, "anything");
    try {
      assertThat(engine.getRepositoryService().getDeploymentResourceNames(id))
        .containsExactly("org/camunda/bpm/engine/impl/test/TestHelperCoverageTest.bpmn20.xml");
    } finally {
      TestHelper.deleteDeployment(engine, id);
    }
  }

  @Test
  public void shouldDeployWithExplicitResourcesOverload() {
    String id = TestHelper.annotationDeploymentSetUp(engine, new String[] {EXPLICIT}, TestHelperCoverageTest.class, "named");
    try {
      assertThat(engine.getRepositoryService().getDeploymentResourceNames(id)).containsExactly(EXPLICIT);
    } finally {
      TestHelper.deleteDeployment(engine, id);
    }
  }

  @Test
  public void shouldNotDeployWithoutResourcesAndMethodName() {
    assertThat(TestHelper.annotationDeploymentSetUp(engine, (String[]) null, TestHelperCoverageTest.class, "m")).isNull();
  }

  @Test
  public void shouldReturnNullWhenNoAnnotationAndNoMethod() {
    long before = deploymentCount();

    assertThat(TestHelper.annotationDeploymentSetUp(engine, TestHelperCoverageTest.class, "doesNotExist")).isNull();
    assertThat(TestHelper.annotationDeploymentSetUp(engine, TestHelperCoverageTest.class, "undecorated")).isNull();
    assertThat(deploymentCount()).isEqualTo(before);
  }

  @Test
  public void shouldUseGivenAnnotationWhenMethodIsMissing() throws Exception {
    Deployment annotation = TestHelperCoverageTest.class.getMethod("explicitOnMethod").getAnnotation(Deployment.class);

    String id = TestHelper.annotationDeploymentSetUp(engine, TestHelperCoverageTest.class, "renamedParameterizedCase", annotation);
    try {
      assertThat(id).isNotNull();
    } finally {
      TestHelper.deleteDeployment(engine, id);
    }
  }

  @Test
  public void shouldFindClassLevelAnnotationOnSuperclass() {
    String id = TestHelper.annotationDeploymentSetUp(engine, InheritsClassLevel.class, "somewhere");
    try {
      assertThat(id).isNotNull();
      assertThat(engine.getRepositoryService().createDeploymentQuery().deploymentId(id).singleResult().getName())
        .isEqualTo("TestHelperCoverageTest$ClassLevel.somewhere");
    } finally {
      TestHelper.deleteDeployment(engine, id);
    }
  }

  @Test
  public void shouldIgnoreNullDeploymentIdOnDelete() {
    long before = deploymentCount();

    TestHelper.deleteDeployment(engine, null);

    assertThat(deploymentCount()).isEqualTo(before);
  }

  @Test
  public void shouldResolveBpmnResourceByConventionOrFallBackToFirstSuffix() {
    assertThat(TestHelper.getBpmnProcessDefinitionResource(TestHelperCoverageTest.class, "conventionMethod"))
      .isEqualTo("org/camunda/bpm/engine/impl/test/TestHelperCoverageTest.conventionMethod.bpmn20.xml");
    assertThat(TestHelper.getBpmnProcessDefinitionResource(TestHelperCoverageTest.class, "unknown"))
      .isEqualTo("org/camunda/bpm/engine/impl/test/TestHelperCoverageTest.unknown.bpmn20.xml");
    assertThat(TestHelper.getBpmnProcessDefinitionResource(TestHelperCoverageTest.class, null))
      .isEqualTo("org/camunda/bpm/engine/impl/test/TestHelperCoverageTest.bpmn20.xml");
  }

  // -- required history level / database ------------------------------------------------------

  static class Levels {
    @RequiredHistoryLevel("full")
    public void needsFull() {
    }

    @RequiredHistoryLevel("none")
    public void needsNone() {
    }

    @RequiredHistoryLevel("bogus")
    public void needsBogus() {
    }

    public void plain() {
    }
  }

  @RequiredHistoryLevel("full")
  static class FullLevelClass {
    public void plain() {
    }
  }

  private ProcessEngine engineWithHistory(HistoryLevel current) {
    ProcessEngineConfigurationImpl config = mock(ProcessEngineConfigurationImpl.class);
    when(config.getHistoryLevels()).thenReturn(Arrays.asList(HistoryLevel.HISTORY_LEVEL_NONE, HistoryLevel.HISTORY_LEVEL_ACTIVITY,
        HistoryLevel.HISTORY_LEVEL_AUDIT, HistoryLevel.HISTORY_LEVEL_FULL));
    when(config.getHistoryLevel()).thenReturn(current);
    ProcessEngine mocked = mock(ProcessEngine.class);
    when(mocked.getProcessEngineConfiguration()).thenReturn(config);
    return mocked;
  }

  @Test
  public void shouldCompareHistoryLevelFromMethodAnnotation() {
    assertThat(TestHelper.annotationRequiredHistoryLevelCheck(engineWithHistory(HistoryLevel.HISTORY_LEVEL_AUDIT), null, Levels.class, "needsFull")).isFalse();
    assertThat(TestHelper.annotationRequiredHistoryLevelCheck(engineWithHistory(HistoryLevel.HISTORY_LEVEL_FULL), null, Levels.class, "needsFull")).isTrue();
    assertThat(TestHelper.annotationRequiredHistoryLevelCheck(engineWithHistory(HistoryLevel.HISTORY_LEVEL_NONE), null, Levels.class, "needsNone")).isTrue();
    assertThat(TestHelper.annotationRequiredHistoryLevelCheck(engineWithHistory(HistoryLevel.HISTORY_LEVEL_NONE), null, Levels.class, "plain")).isTrue();
  }

  @Test
  public void shouldCompareHistoryLevelFromClassAnnotation() {
    assertThat(TestHelper.annotationRequiredHistoryLevelCheck(engineWithHistory(HistoryLevel.HISTORY_LEVEL_ACTIVITY), FullLevelClass.class, "plain")).isFalse();
    assertThat(TestHelper.annotationRequiredHistoryLevelCheck(engineWithHistory(HistoryLevel.HISTORY_LEVEL_FULL), FullLevelClass.class, "plain")).isTrue();
  }

  @Test
  public void shouldPreferExplicitHistoryAnnotation() throws Exception {
    RequiredHistoryLevel full = Levels.class.getMethod("needsFull").getAnnotation(RequiredHistoryLevel.class);

    assertThat(TestHelper.annotationRequiredHistoryLevelCheck(engineWithHistory(HistoryLevel.HISTORY_LEVEL_NONE), full, Levels.class, "plain")).isFalse();
  }

  @Test
  public void shouldRejectUnknownHistoryLevelName() {
    ProcessEngine mocked = engineWithHistory(HistoryLevel.HISTORY_LEVEL_FULL);

    assertThatThrownBy(() -> TestHelper.annotationRequiredHistoryLevelCheck(mocked, Levels.class, "needsBogus"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Unknown history level: bogus");
  }

  static class Databases {
    @RequiredDatabase(includes = {"h2", "postgres"})
    public void includesH2() {
    }

    @RequiredDatabase(includes = "oracle")
    public void includesOracle() {
    }

    @RequiredDatabase(excludes = "h2")
    public void excludesH2() {
    }

    @RequiredDatabase
    public void unrestricted() {
    }

    public void plain() {
    }
  }

  @RequiredDatabase(excludes = "db2")
  static class ClassWithDatabase {
    public void plain() {
    }
  }

  private ProcessEngine engineOnDatabase(String type) {
    DbSqlSessionFactory factory = mock(DbSqlSessionFactory.class);
    when(factory.getDatabaseType()).thenReturn(type);
    ProcessEngineConfigurationImpl config = mock(ProcessEngineConfigurationImpl.class);
    when(config.getDbSqlSessionFactory()).thenReturn(factory);
    ProcessEngine mocked = mock(ProcessEngine.class);
    when(mocked.getProcessEngineConfiguration()).thenReturn(config);
    return mocked;
  }

  @Test
  public void shouldCheckRequiredDatabase() {
    ProcessEngine h2 = engineOnDatabase("h2");

    assertThat(TestHelper.annotationRequiredDatabaseCheck(h2, Databases.class, "includesH2")).isTrue();
    assertThat(TestHelper.annotationRequiredDatabaseCheck(h2, Databases.class, "includesOracle")).isFalse();
    assertThat(TestHelper.annotationRequiredDatabaseCheck(h2, Databases.class, "excludesH2")).isFalse();
    assertThat(TestHelper.annotationRequiredDatabaseCheck(engineOnDatabase("mysql"), Databases.class, "excludesH2")).isTrue();
    assertThat(TestHelper.annotationRequiredDatabaseCheck(h2, Databases.class, "unrestricted")).isTrue();
    assertThat(TestHelper.annotationRequiredDatabaseCheck(h2, Databases.class, "plain")).isTrue();
    assertThat(TestHelper.annotationRequiredDatabaseCheck(h2, ClassWithDatabase.class, "plain")).isTrue();
    assertThat(TestHelper.annotationRequiredDatabaseCheck(engineOnDatabase("db2"), ClassWithDatabase.class, "plain")).isFalse();
  }

  @Test
  public void shouldPreferExplicitDatabaseAnnotation() throws Exception {
    RequiredDatabase oracleOnly = Databases.class.getMethod("includesOracle").getAnnotation(RequiredDatabase.class);

    assertThat(TestHelper.annotationRequiredDatabaseCheck(engineOnDatabase("h2"), oracleOnly, Databases.class, "plain")).isFalse();
    assertThat(TestHelper.annotationRequiredDatabaseCheck(engineOnDatabase("oracle"), oracleOnly, Databases.class, "plain")).isTrue();
    assertThat(TestHelper.annotationRequiredDatabaseCheck(engineOnDatabase("h2"), null, Databases.class, "excludesH2")).isFalse();
  }

  // -- clean db / cache -----------------------------------------------------------------------

  @Test
  public void shouldReportDirtyDatabaseAndPurgeIt() {
    String deploymentId = engine.getRepositoryService().createDeployment()
      .addModelInstance("dirty.bpmn", Bpmn.createExecutableProcess("dirtyProcess").startEvent().userTask().endEvent().done())
      .deploy().getId();
    assertThat(deploymentId).isNotNull();

    assertThatThrownBy(() -> TestHelper.assertAndEnsureCleanDbAndCache(engine))
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("Database is not clean:");

    assertThat(TestHelper.assertAndEnsureCleanDbAndCache(engine, true)).isEmpty();
  }

  @Test
  public void shouldReturnMessageInsteadOfFailingWhenAsked() {
    engine.getRepositoryService().createDeployment()
      .addModelInstance("dirty2.bpmn", Bpmn.createExecutableProcess("dirtyProcess2").startEvent().userTask().endEvent().done())
      .deploy();

    String message = TestHelper.assertAndEnsureCleanDbAndCache(engine, false);

    assertThat(message).contains("Database is not clean:");
    assertThat(TestHelper.assertAndEnsureCleanDbAndCache(engine, false)).isEmpty();
  }

  @Test
  public void shouldReportDirtyDeploymentCache() {
    engine.getRepositoryService().createDeployment()
      .addModelInstance("cache.bpmn", Bpmn.createExecutableProcess("cacheProcess").startEvent().userTask().endEvent().done())
      .deploy();

    assertThatThrownBy(() -> TestHelper.assertAndEnsureCleanDeploymentCache(engine))
      .isInstanceOf(AssertionError.class)
      .hasMessageStartingWith("Deployment cache not clean:");

    TestHelper.assertAndEnsureCleanDbAndCache(engine, false);
    assertThat(TestHelper.assertAndEnsureCleanDeploymentCache(engine, false)).isNull();
  }

  @Test
  public void shouldReturnCacheSummaryWithoutFailing() {
    engine.getRepositoryService().createDeployment()
      .addModelInstance("cache2.bpmn", Bpmn.createExecutableProcess("cacheProcess2").startEvent().userTask().endEvent().done())
      .deploy();

    assertThat(TestHelper.assertAndEnsureCleanDeploymentCache(engine, false)).startsWith("Deployment cache not clean:");

    TestHelper.assertAndEnsureCleanDbAndCache(engine, false);
  }

  @Test
  public void shouldFailWithoutMessage() {
    assertThatThrownBy(() -> TestHelper.fail(null)).isInstanceOf(AssertionError.class).hasMessage(null);
    assertThatThrownBy(() -> TestHelper.fail("boom")).isInstanceOf(AssertionError.class).hasMessage("boom");
  }

  private void registerProcessApplication(ProcessEngineConfigurationImpl config, String deploymentId) {
    config.getCommandExecutorTxRequired().execute(commandContext -> {
      config.getProcessApplicationManager().registerProcessApplicationForDeployments(Collections.singleton(deploymentId),
          mock(org.camunda.bpm.application.ProcessApplicationReference.class));
      return null;
    });
  }

  @Test
  public void shouldDetectAndClearProcessApplicationRegistrations() {
    ProcessEngineConfigurationImpl config = ((org.camunda.bpm.engine.impl.ProcessEngineImpl) engine).getProcessEngineConfiguration();
    assertThat(TestHelper.assertAndEnsureNoProcessApplicationsRegistered(engine)).isNull();

    registerProcessApplication(config, "d1");

    assertThat(TestHelper.assertAndEnsureNoProcessApplicationsRegistered(engine)).isEqualTo("There are still process applications registered");
    assertThat(config.getProcessApplicationManager().hasRegistrations()).isFalse();
  }

  @Test
  public void shouldAppendProcessApplicationMessageToCleanCheck() {
    ProcessEngineConfigurationImpl config = ((org.camunda.bpm.engine.impl.ProcessEngineImpl) engine).getProcessEngineConfiguration();
    registerProcessApplication(config, "d2");

    assertThatThrownBy(() -> TestHelper.assertAndEnsureCleanDbAndCache(engine))
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("There are still process applications registered");
  }

  // -- job executor ---------------------------------------------------------------------------

  private ProcessEngineConfigurationImpl configWithJobs(JobExecutor jobExecutor, List<List<Job>> answers) {
    JobQuery query = mock(JobQuery.class);
    when(query.executable()).thenReturn(query);
    @SuppressWarnings("unchecked")
    List<Job>[] rest = answers.subList(1, answers.size()).toArray(new List[0]);
    when(query.list()).thenReturn(answers.get(0), rest);
    ManagementService management = mock(ManagementService.class);
    when(management.createJobQuery()).thenReturn(query);
    ProcessEngineConfigurationImpl config = mock(ProcessEngineConfigurationImpl.class);
    when(config.getJobExecutor()).thenReturn(jobExecutor);
    when(config.getManagementService()).thenReturn(management);
    return config;
  }

  @Test
  public void shouldReportJobAvailability() {
    Job job = mock(Job.class);

    assertThat(TestHelper.areJobsAvailable(configWithJobs(mock(JobExecutor.class), Collections.singletonList(Collections.singletonList(job))))).isTrue();
    assertThat(TestHelper.areJobsAvailable(configWithJobs(mock(JobExecutor.class), Collections.singletonList(Collections.emptyList())))).isFalse();
  }

  @Test
  public void shouldWaitUntilNoJobsAreLeft() {
    JobExecutor jobExecutor = mock(JobExecutor.class);
    Job job = mock(Job.class);

    TestHelper.waitForJobExecutorToProcessAllJobs(
        configWithJobs(jobExecutor, Arrays.asList(Collections.singletonList(job), Collections.<Job>emptyList())), 5000, 10);

    verify(jobExecutor).start();
    verify(jobExecutor).shutdown();
  }

  @Test
  public void shouldTimeOutWhenJobsNeverFinish() {
    JobExecutor jobExecutor = mock(JobExecutor.class);
    ProcessEngineConfigurationImpl config = configWithJobs(jobExecutor, Collections.singletonList(Collections.singletonList(mock(Job.class))));

    assertThatThrownBy(() -> TestHelper.waitForJobExecutorToProcessAllJobs(config, 200, 20))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessage("time limit of 200 was exceeded");
    verify(jobExecutor).shutdown();
    Thread.interrupted();
  }

  // -- id generator, engines, schema ---------------------------------------------------------

  @Test
  public void shouldResetOnlyDbIdGenerator() {
    DbIdGenerator generator = mock(DbIdGenerator.class);
    ProcessEngineConfigurationImpl config = mock(ProcessEngineConfigurationImpl.class);
    when(config.getIdGenerator()).thenReturn(generator);

    TestHelper.resetIdGenerator(config);

    verify(generator).reset();

    when(config.getIdGenerator()).thenReturn(mock(org.camunda.bpm.engine.impl.cfg.IdGenerator.class));
    TestHelper.resetIdGenerator(config);
    verify(generator).reset();
  }

  @Test
  public void shouldCacheAndCloseNamedProcessEngines() {
    ProcessEngine cached = mock(ProcessEngine.class);
    TestHelper.processEngines.put("coverage-test-cfg", cached);
    try {
      assertThat(TestHelper.getProcessEngine("coverage-test-cfg")).isSameAs(cached);

      TestHelper.closeProcessEngines();

      verify(cached).close();
      assertThat(TestHelper.processEngines).isEmpty();
    } finally {
      TestHelper.processEngines.remove("coverage-test-cfg");
    }
  }

  private static ProcessEngineConfigurationImpl configRunning(CommandContext context) {
    CommandExecutor executor = mock(CommandExecutor.class);
    when(executor.execute(any())).thenAnswer(invocation -> ((Command<?>) invocation.getArgument(0)).execute(context));
    ProcessEngineConfigurationImpl config = mock(ProcessEngineConfigurationImpl.class);
    when(config.getCommandExecutorTxRequired()).thenReturn(executor);
    return config;
  }

  @Test
  public void shouldCreateSchemaThroughPersistenceSession() {
    CommandContext context = mock(CommandContext.class);
    PersistenceSession session = mock(PersistenceSession.class);
    when(context.getSession(PersistenceSession.class)).thenReturn(session);

    TestHelper.createSchema(configRunning(context));

    verify(session).dbSchemaCreate();
  }

  @Test
  public void shouldDropSchemaThroughDbSqlSession() {
    CommandContext context = mock(CommandContext.class);
    DbSqlSession session = mock(DbSqlSession.class);
    when(context.getDbSqlSession()).thenReturn(session);

    TestHelper.dropSchema(configRunning(context));

    verify(session).dbSchemaDrop();
  }

  private static PropertyEntity property(String value) {
    PropertyEntity entity = new PropertyEntity("historyLevel", value);
    return entity;
  }

  @Test
  public void shouldUpdateHistoryLevelPropertyWhenDifferent() {
    CommandContext context = mock(CommandContext.class);
    DbEntityManager entityManager = mock(DbEntityManager.class);
    when(context.getDbEntityManager()).thenReturn(entityManager);
    PropertyEntity stored = property("0");
    when(entityManager.selectById(PropertyEntity.class, "historyLevel")).thenReturn(stored);
    ProcessEngineConfigurationImpl config = configRunning(context);
    when(config.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_FULL);

    TestHelper.createOrUpdateHistoryLevel(config);

    assertThat(stored.getValue()).isEqualTo(Integer.toString(HistoryLevel.HISTORY_LEVEL_FULL.getId()));
    verify(entityManager).merge(stored);
  }

  @Test
  public void shouldLeaveHistoryLevelPropertyWhenSame() {
    CommandContext context = mock(CommandContext.class);
    DbEntityManager entityManager = mock(DbEntityManager.class);
    when(context.getDbEntityManager()).thenReturn(entityManager);
    PropertyEntity stored = property(Integer.toString(HistoryLevel.HISTORY_LEVEL_FULL.getId()));
    when(entityManager.selectById(PropertyEntity.class, "historyLevel")).thenReturn(stored);
    ProcessEngineConfigurationImpl config = configRunning(context);
    when(config.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_FULL);

    TestHelper.createOrUpdateHistoryLevel(config);

    verify(entityManager, never()).merge(any(PropertyEntity.class));
  }

  @Test
  public void shouldDeleteHistoryLevelPropertyOnlyWhenPresent() {
    CommandContext context = mock(CommandContext.class);
    DbEntityManager entityManager = mock(DbEntityManager.class);
    when(context.getDbEntityManager()).thenReturn(entityManager);
    ProcessEngineConfigurationImpl config = configRunning(context);
    PropertyEntity stored = property("3");

    TestHelper.deleteHistoryLevel(config);
    verify(entityManager, never()).delete(any(PropertyEntity.class));

    when(entityManager.selectById(PropertyEntity.class, "historyLevel")).thenReturn(stored);
    TestHelper.deleteHistoryLevel(config);
    verify(entityManager).delete(stored);
  }

  @Test
  public void shouldRecreateHistoryLevelOnRealEngine() {
    ProcessEngineConfigurationImpl config = ((org.camunda.bpm.engine.impl.ProcessEngineImpl) engine).getProcessEngineConfiguration();
    TestHelper.deleteHistoryLevel(config);
    try {
      assertThat(historyLevelProperty(config)).isNull();
    } finally {
      TestHelper.createOrUpdateHistoryLevel(config);
    }
    assertThat(historyLevelProperty(config)).isEqualTo(Integer.toString(config.getHistoryLevel().getId()));
  }

  private String historyLevelProperty(ProcessEngineConfigurationImpl config) {
    return config.getCommandExecutorTxRequired().execute(commandContext -> {
      PropertyEntity entity = commandContext.getDbEntityManager().selectById(PropertyEntity.class, "historyLevel");
      return entity == null ? null : entity.getValue();
    });
  }

  @Test
  public void shouldDeleteInstallationIdOnlyWhenPresent() {
    CommandContext context = mock(CommandContext.class);
    DbEntityManager entityManager = mock(DbEntityManager.class);
    when(context.getDbEntityManager()).thenReturn(entityManager);
    ProcessEngineConfigurationImpl config = configRunning(context);
    PropertyEntity stored = new PropertyEntity("camunda.installation.id", "abc");

    TestHelper.deleteInstallationId(config);
    verify(entityManager, never()).delete(any(PropertyEntity.class));

    when(entityManager.selectById(PropertyEntity.class, "camunda.installation.id")).thenReturn(stored);
    TestHelper.deleteInstallationId(config);
    verify(entityManager).delete(stored);
  }

  @Test
  public void shouldClearUserOperationLogOnlyOnFullHistory() {
    HistoryService historyService = mock(HistoryService.class);
    org.camunda.bpm.engine.history.UserOperationLogQuery query = mock(org.camunda.bpm.engine.history.UserOperationLogQuery.class);
    UserOperationLogEntry entry = mock(UserOperationLogEntry.class);
    when(entry.getId()).thenReturn("log1");
    when(query.list()).thenReturn(Collections.singletonList(entry));
    when(historyService.createUserOperationLogQuery()).thenReturn(query);
    ProcessEngineConfigurationImpl config = mock(ProcessEngineConfigurationImpl.class);
    when(config.getHistoryService()).thenReturn(historyService);

    when(config.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_AUDIT);
    TestHelper.clearUserOperationLog(config);
    verify(historyService, never()).deleteUserOperationLogEntry("log1");

    when(config.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_FULL);
    TestHelper.clearUserOperationLog(config);
    verify(historyService).deleteUserOperationLogEntry("log1");
  }

  @Test
  public void shouldBuildDefaultManualActivationRule() {
    assertThat(TestHelper.defaultManualActivation()).isInstanceOf(CaseControlRuleImpl.class);
  }

  @Test
  public void shouldDelegateProcessEndedAssertion() {
    TestHelper.assertProcessEnded(engine, "no-such-process-instance");
  }
}
