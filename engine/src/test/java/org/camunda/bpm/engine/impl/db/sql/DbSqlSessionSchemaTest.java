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
package org.camunda.bpm.engine.impl.db.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;
import java.util.UUID;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Exercises the schema handling of {@link DbSqlSession} against a private in-memory H2 engine:
 * script execution, missing-table detection, table lookup and version checks.
 */
public class DbSqlSessionSchemaTest {

  private static ProcessEngine engine;
  private static ProcessEngineConfigurationImpl config;

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @BeforeClass
  public static void createEngine() {
    config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
    config.setJdbcUrl("jdbc:h2:mem:dbsqlsession-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    config.setHistory(ProcessEngineConfiguration.HISTORY_NONE);
    config.setJobExecutorActivate(false);
    engine = config.buildProcessEngine();
  }

  @AfterClass
  public static void closeEngine() {
    engine.close();
  }

  private static <T> T withSession(Function<DbSqlSession, T> fn) {
    return config.getCommandExecutorTxRequired().execute(
        commandContext -> fn.apply(commandContext.getSession(DbSqlSession.class)));
  }

  private static String prefixed(String table) {
    return DbSqlSessionSchemaTest.<String>withSession(s -> s.prependDatabaseTablePrefix(table));
  }

  private static boolean flag(Function<DbSqlSession, Boolean> fn) {
    return withSession(fn);
  }

  private File script(String content) throws Exception {
    File f = folder.newFile();
    Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    return f;
  }

  @Test
  public void shouldExecuteScriptWithCommentsAndMultiLineStatements() throws Exception {
    File f = script("-- a sql comment\n# a hash comment\n\n"
        + "create table SCHEMA_T1 (\n  ID_ varchar(10),\n  VAL_ integer\n);\n"
        + "insert into SCHEMA_T1 values ('a', 1);\n");

    withSession(s -> { s.executeSchemaResource(f.getAbsolutePath()); return null; });

    assertThat(flag(s -> s.isTablePresent("SCHEMA_T1"))).isTrue();
    withSession(s -> { s.executeSchemaResource(script2("drop table SCHEMA_T1;")); return null; });
    assertThat(flag(s -> s.isTablePresent("SCHEMA_T1"))).isFalse();
  }

  private String script2(String content) {
    try {
      return script(content).getAbsolutePath();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void shouldRunRemainingStatementsButReportFirstFailure() throws Exception {
    File f = script("create table SCHEMA_T2 (ID_ varchar(10));\n"
        + "drop table DOES_NOT_EXIST;\n"
        + "create table SCHEMA_T3 (ID_ varchar(10));\n");

    assertThatThrownBy(() -> withSession(s -> { s.executeSchemaResource(f.getAbsolutePath()); return null; }))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("DOES_NOT_EXIST");

    // the statement after the failing one was still executed
    assertThat(flag(s -> s.isTablePresent("SCHEMA_T3"))).isTrue();
    withSession(s -> { s.executeSchemaResource(script2("drop table SCHEMA_T2;\ndrop table SCHEMA_T3;")); return null; });
  }

  @Test
  public void shouldFailOnMissingScriptFile() {
    String missing = new File(folder.getRoot(), "nope.sql").getAbsolutePath();

    assertThatThrownBy(() -> withSession(s -> { s.executeSchemaResource(missing); return null; }))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("nope.sql");
  }

  @Test
  public void shouldTolerateMissingOptionalResourceButNotMandatoryOne() {
    withSession(s -> { s.executeSchemaResource("create", "x", "no/such/resource.sql", true); return null; });

    assertThatThrownBy(() -> withSession(s -> { s.executeSchemaResource("create", "x", "no/such/resource.sql", false); return null; }))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("no/such/resource.sql");
  }

  @Test
  public void shouldListEngineTablesAndDetectPresence() {
    List<String> tables = withSession(DbSqlSession::getTableNamesPresent);

    assertThat(tables).contains("ACT_RU_EXECUTION", "ACT_RU_TASK", "ACT_GE_PROPERTY");
    assertThat(flag(DbSqlSession::isEngineTablePresent)).isTrue();
    assertThat(flag(DbSqlSession::isIdentityTablePresent)).isTrue();
    assertThat(flag(s -> s.isTablePresent("ACT_NO_SUCH_TABLE"))).isFalse();
  }

  @Test
  public void shouldPassVersionCheckForFreshSchema() {
    withSession(s -> { s.dbSchemaCheckVersion(); return null; });
  }

  @Test
  public void shouldFailVersionCheckOnWrongVersion() {
    withSession(s -> {
      try {
        Connection c = s.getSqlSession().getConnection();
        setSchemaVersion(c, "0.0.0-wrong");
        try {
          assertThatThrownBy(s::dbSchemaCheckVersion)
              .isInstanceOf(ProcessEngineException.class)
              .hasMessageContaining("0.0.0-wrong");
        } finally {
          setSchemaVersion(c, ProcessEngine.VERSION);
        }
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
      return null;
    });
  }

  private static void setSchemaVersion(Connection c, String version) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("update ACT_GE_PROPERTY set VALUE_ = ? where NAME_ = 'schema.version'")) {
      ps.setString(1, version);
      assertThat(ps.executeUpdate()).isEqualTo(1);
    }
  }

  @Test
  public void shouldBuildResourceNameFromDatabaseType() {
    String name = withSession(s -> s.getResourceForDbOperation("create", "create", "engine"));

    assertThat(name).isEqualTo("org/camunda/bpm/engine/db/create/activiti.h2.create.engine.sql");
  }

  @Test
  public void shouldPrependTablePrefixOnlyWhenConfigured() {
    DbSqlSessionFactory factory = config.getDbSqlSessionFactory();
    String prefix = factory.getDatabaseTablePrefix();
    String schema = factory.getDatabaseSchema();
    try {
      factory.setDatabaseTablePrefix("PRE_");
      factory.setDatabaseSchema(null);
      assertThat(prefixed("ACT_X")).isEqualTo("PRE_ACT_X");

      factory.setDatabaseSchema("MYSCHEMA");
      factory.setDatabaseTablePrefix("MYSCHEMA.PRE_");
      assertThat(prefixed("ACT_X")).isEqualTo("PRE_ACT_X");

      factory.setDatabaseTablePrefix("OTHER.PRE_");
      assertThat(prefixed("ACT_X")).isEqualTo("OTHER.PRE_ACT_X");

      factory.setDatabaseTablePrefix(null);
      assertThat(prefixed("ACT_X")).isEqualTo("ACT_X");
    } finally {
      factory.setDatabaseTablePrefix(prefix);
      factory.setDatabaseSchema(schema);
    }
  }

  @Test
  public void shouldRecognizeMissingTableMessagesPerDatabase() {
    withSession(s -> {
      assertThat(s.isMissingTablesException(new RuntimeException("x", new RuntimeException("Table \"ACT_X\" not found")))).isTrue();
      assertThat(s.isMissingTablesException(new RuntimeException("x", new RuntimeException("Table 'a.b' doesn't exist")))).isTrue();
      assertThat(s.isMissingTablesException(new RuntimeException("x", new RuntimeException("relation \"act_x\" does not exist")))).isTrue();
      assertThat(s.isMissingTablesException(new RuntimeException("x", new RuntimeException("something else")))).isFalse();
      assertThat(s.isMissingTablesException(new RuntimeException("x", new RuntimeException((String) null)))).isFalse();
      assertThat(s.isMissingTablesException(new RuntimeException("no cause"))).isFalse();
      return null;
    });
  }
}
