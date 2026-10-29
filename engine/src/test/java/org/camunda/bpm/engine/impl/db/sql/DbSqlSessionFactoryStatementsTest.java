package org.camunda.bpm.engine.impl.db.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import org.junit.Test;

/**
 * Verifies that the database specific statement maps exposed by
 * {@link DbSqlSessionFactory} are populated (same content as before the
 * hardening) and cannot be modified by callers.
 */
public class DbSqlSessionFactoryStatementsTest {

  @Test
  @SuppressWarnings("unchecked")
  public void shouldKeepContentOfPublicStatementMaps() throws Exception {
    int checked = 0;
    for (Field field : DbSqlSessionFactory.class.getDeclaredFields()) {
      if (!Modifier.isPublic(field.getModifiers()) || !Modifier.isStatic(field.getModifiers())
          || !Map.class.isAssignableFrom(field.getType())) {
        continue;
      }
      Map<Object, Object> map = (Map<Object, Object>) field.get(null);
      checked++;
      assertThat(map.keySet()).as(field.getName()).isNotEmpty();
    }
    assertThat(checked).isEqualTo(41);
    assertThat(DbSqlSessionFactory.databaseSpecificLimitAfterStatements.get(DbSqlSessionFactory.H2))
        .isEqualTo("LIMIT #{maxResults} OFFSET #{firstResult}");
    assertThat(DbSqlSessionFactory.databaseSpecificTrueConstant.get(DbSqlSessionFactory.H2)).isEqualTo("true");
    assertThat(DbSqlSessionFactory.dbSpecificConstants.get(DbSqlSessionFactory.H2).get("constant_for_update"))
        .isEqualTo("for update");
    assertThat(DbSqlSessionFactory.dbSpecificConstants.keySet())
        .containsExactlyInAnyOrder(DbSqlSessionFactory.getSupportedDatabases());
  }

  @Test
  public void shouldRejectModification() {
    assertThatThrownBy(() -> DbSqlSessionFactory.databaseSpecificTrueConstant.put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> DbSqlSessionFactory.dbSpecificConstants.get(DbSqlSessionFactory.H2).put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void shouldReturnCopyOfSupportedDatabases() {
    String[] copy = DbSqlSessionFactory.getSupportedDatabases();
    copy[0] = "changed";
    assertThat(DbSqlSessionFactory.getSupportedDatabases()[0]).isNotEqualTo("changed");
    assertThat(DbSqlSessionFactory.getSupportedDatabases()).hasSize(6);
  }
}
