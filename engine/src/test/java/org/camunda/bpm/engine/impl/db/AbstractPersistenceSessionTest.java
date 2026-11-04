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
package org.camunda.bpm.engine.impl.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbBulkOperation;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbEntityOperation;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbOperationType;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.persistence.entity.PropertyEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

/**
 * Tests the template logic of {@link AbstractPersistenceSession}: operation dispatch, which schema
 * parts are created, dropped, pruned and updated for a given engine configuration, and the
 * entity load listeners. The concrete schema operations are mocked and verified in order.
 */
public class AbstractPersistenceSessionTest {

  protected AbstractPersistenceSession session;
  protected ProcessEngineConfigurationImpl config;

  @Before
  public void setUp() {
    session = mock(AbstractPersistenceSession.class,
        withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
    config = mock(ProcessEngineConfigurationImpl.class);
    when(config.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_FULL);
    Context.setProcessEngineConfiguration(config);
  }

  @After
  public void tearDown() {
    Context.removeProcessEngineConfiguration();
  }

  protected void configure(boolean history, boolean identity, boolean cmmn, boolean dmn) {
    when(config.isDbHistoryUsed()).thenReturn(history);
    when(config.isDbIdentityUsed()).thenReturn(identity);
    when(config.isCmmnEnabled()).thenReturn(cmmn);
    when(config.isDmnEnabled()).thenReturn(dmn);
  }

  protected void tablesPresent(boolean engine, boolean history, boolean identity, boolean cmmn,
      boolean cmmnHistory, boolean dmn, boolean dmnHistory) {
    doReturn(engine).when(session).isEngineTablePresent();
    doReturn(history).when(session).isHistoryTablePresent();
    doReturn(identity).when(session).isIdentityTablePresent();
    doReturn(cmmn).when(session).isCmmnTablePresent();
    doReturn(cmmnHistory).when(session).isCmmnHistoryTablePresent();
    doReturn(dmn).when(session).isDmnTablePresent();
    doReturn(dmnHistory).when(session).isDmnHistoryTablePresent();
  }

  protected DbEntityOperation entityOperation(DbOperationType type) {
    DbEntityOperation operation = new DbEntityOperation();
    operation.setOperationType(type);
    return operation;
  }

  protected DbBulkOperation bulkOperation(DbOperationType type) {
    return new DbBulkOperation(type, PropertyEntity.class, "stmt", null);
  }

  // dispatch --------------------------------------------------------------------------------

  @Test
  public void shouldDispatchEveryOperationTypeToItsHandler() {
    DbEntityOperation insert = entityOperation(DbOperationType.INSERT);
    DbEntityOperation delete = entityOperation(DbOperationType.DELETE);
    DbEntityOperation update = entityOperation(DbOperationType.UPDATE);
    DbBulkOperation deleteBulk = bulkOperation(DbOperationType.DELETE_BULK);
    DbBulkOperation updateBulk = bulkOperation(DbOperationType.UPDATE_BULK);

    session.executeDbOperation(insert);
    session.executeDbOperation(delete);
    session.executeDbOperation(update);
    session.executeDbOperation(deleteBulk);
    session.executeDbOperation(updateBulk);

    verify(session).insertEntity(insert);
    verify(session).deleteEntity(delete);
    verify(session).updateEntity(update);
    verify(session).deleteBulk(deleteBulk);
    verify(session).updateBulk(updateBulk);
  }

  // dbSchemaCreate --------------------------------------------------------------------------

  @Test
  public void shouldCreateAllSchemaPartsOnEmptyDatabase() {
    configure(true, true, true, true);
    tablesPresent(false, false, false, false, false, false, false);

    session.dbSchemaCreate();

    InOrder order = inOrder(session);
    order.verify(session).dbSchemaCreateEngine();
    order.verify(session).dbSchemaCreateHistory();
    order.verify(session).dbSchemaCreateIdentity();
    order.verify(session).dbSchemaCreateCmmn();
    order.verify(session).dbSchemaCreateCmmnHistory();
    order.verify(session).dbSchemaCreateDmn();
    order.verify(session).dbSchemaCreateDmnHistory();
  }

  @Test
  public void shouldCreateOnlyEngineTablesWhenEverythingElseIsDisabled() {
    configure(false, false, false, false);
    when(config.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_NONE);
    tablesPresent(false, false, false, false, false, false, false);

    session.dbSchemaCreate();

    verify(session).dbSchemaCreateEngine();
    verify(session, never()).dbSchemaCreateHistory();
    verify(session, never()).dbSchemaCreateIdentity();
    verify(session, never()).dbSchemaCreateCmmn();
    verify(session, never()).dbSchemaCreateCmmnHistory();
    verify(session, never()).dbSchemaCreateDmn();
    verify(session, never()).dbSchemaCreateDmnHistory();
  }

  @Test
  public void shouldCreateDmnWithoutDmnHistoryWhenHistoryIsOff() {
    configure(false, false, false, true);
    when(config.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_NONE);
    tablesPresent(false, false, false, false, false, false, false);

    session.dbSchemaCreate();

    verify(session).dbSchemaCreateDmn();
    verify(session, never()).dbSchemaCreateDmnHistory();
  }

  @Test
  public void shouldCreateCmmnWithoutCmmnHistoryWhenHistoryIsOff() {
    configure(false, false, true, false);
    when(config.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_NONE);
    tablesPresent(false, false, false, false, false, false, false);

    session.dbSchemaCreate();

    verify(session).dbSchemaCreateCmmn();
    verify(session, never()).dbSchemaCreateCmmnHistory();
  }

  @Test
  public void shouldSkipEngineCreationWhenTablesPresentWithMatchingVersion() {
    configure(false, false, false, false);
    when(config.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_NONE);
    tablesPresent(true, false, false, false, false, false, false);
    doReturn(ProcessEngine.VERSION).when(session).getDbVersion();

    session.dbSchemaCreate();

    verify(session, never()).dbSchemaCreateEngine();
  }

  @Test
  public void shouldFailWhenDatabaseVersionDiffersFromLibraryVersion() {
    configure(false, false, false, false);
    when(config.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_NONE);
    tablesPresent(true, false, false, false, false, false, false);
    doReturn("0.0.1").when(session).getDbVersion();

    assertThatThrownBy(() -> session.dbSchemaCreate())
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("0.0.1")
        .hasMessageContaining(ProcessEngine.VERSION);
    verify(session, never()).dbSchemaCreateEngine();
  }

  @Test
  public void shouldFailBeforeCreatingAnythingWhenHistoryLevelNeedsHistoryTables() {
    configure(false, false, false, false);
    when(config.getHistoryLevel()).thenReturn(HistoryLevel.HISTORY_LEVEL_FULL);

    assertThatThrownBy(() -> session.dbSchemaCreate())
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("dbHistoryUsed");
    verify(session, never()).dbSchemaCreateEngine();
  }

  // dbSchemaDrop ----------------------------------------------------------------------------

  @Test
  public void shouldDropAllSchemaPartsInReverseDependencyOrder() {
    configure(true, true, true, true);

    session.dbSchemaDrop();

    InOrder order = inOrder(session);
    order.verify(session).dbSchemaDropDmn();
    order.verify(session).dbSchemaDropDmnHistory();
    order.verify(session).dbSchemaDropCmmn();
    order.verify(session).dbSchemaDropEngine();
    order.verify(session).dbSchemaDropCmmnHistory();
    order.verify(session).dbSchemaDropHistory();
    order.verify(session).dbSchemaDropIdentity();
  }

  @Test
  public void shouldDropOnlyEngineTablesWhenEverythingElseIsDisabled() {
    configure(false, false, false, false);

    session.dbSchemaDrop();

    verify(session).dbSchemaDropEngine();
    verify(session, never()).dbSchemaDropDmn();
    verify(session, never()).dbSchemaDropDmnHistory();
    verify(session, never()).dbSchemaDropCmmn();
    verify(session, never()).dbSchemaDropCmmnHistory();
    verify(session, never()).dbSchemaDropHistory();
    verify(session, never()).dbSchemaDropIdentity();
  }

  @Test
  public void shouldDropDmnWithoutDmnHistoryWhenHistoryIsOff() {
    configure(false, false, false, true);

    session.dbSchemaDrop();

    verify(session).dbSchemaDropDmn();
    verify(session, never()).dbSchemaDropDmnHistory();
  }

  // dbSchemaPrune ---------------------------------------------------------------------------

  @Test
  public void shouldPruneEveryPresentPartThatIsDisabled() {
    configure(false, false, false, false);
    tablesPresent(true, true, true, true, true, true, true);

    session.dbSchemaPrune();

    verify(session).dbSchemaDropHistory();
    verify(session).dbSchemaDropIdentity();
    verify(session).dbSchemaDropCmmn();
    verify(session).dbSchemaDropCmmnHistory();
    verify(session).dbSchemaDropDmn();
    verify(session).dbSchemaDropDmnHistory();
  }

  @Test
  public void shouldNotPruneAnythingThatIsStillEnabled() {
    configure(true, true, true, true);
    tablesPresent(true, true, true, true, true, true, true);

    session.dbSchemaPrune();

    verify(session, never()).dbSchemaDropHistory();
    verify(session, never()).dbSchemaDropIdentity();
    verify(session, never()).dbSchemaDropCmmn();
    verify(session, never()).dbSchemaDropCmmnHistory();
    verify(session, never()).dbSchemaDropDmn();
    verify(session, never()).dbSchemaDropDmnHistory();
  }

  @Test
  public void shouldPruneCmmnAndDmnHistoryWhenOnlyHistoryIsDisabled() {
    configure(false, true, true, true);
    tablesPresent(true, false, false, false, true, false, true);

    session.dbSchemaPrune();

    verify(session).dbSchemaDropCmmnHistory();
    verify(session).dbSchemaDropDmnHistory();
    verify(session, never()).dbSchemaDropCmmn();
    verify(session, never()).dbSchemaDropDmn();
  }

  @Test
  public void shouldNotPruneAbsentParts() {
    configure(false, false, false, false);
    tablesPresent(true, false, false, false, false, false, false);

    session.dbSchemaPrune();

    verify(session, never()).dbSchemaDropHistory();
    verify(session, never()).dbSchemaDropIdentity();
    verify(session, never()).dbSchemaDropCmmn();
    verify(session, never()).dbSchemaDropCmmnHistory();
    verify(session, never()).dbSchemaDropDmn();
    verify(session, never()).dbSchemaDropDmnHistory();
  }

  // dbSchemaUpdate --------------------------------------------------------------------------

  @Test
  public void shouldCreateEveryMissingEnabledPartOnUpdate() {
    configure(true, true, true, true);
    tablesPresent(false, false, false, false, false, false, false);

    session.dbSchemaUpdate();

    InOrder order = inOrder(session);
    order.verify(session).dbSchemaCreateEngine();
    order.verify(session).dbSchemaCreateHistory();
    order.verify(session).dbSchemaCreateIdentity();
    order.verify(session).dbSchemaCreateCmmn();
    order.verify(session).dbSchemaCreateCmmnHistory();
    order.verify(session).dbSchemaCreateDmn();
    order.verify(session).dbSchemaCreateDmnHistory();
  }

  @Test
  public void shouldNotRecreatePartsThatAreAlreadyPresentOnUpdate() {
    configure(true, true, true, true);
    tablesPresent(true, true, true, true, true, true, true);

    session.dbSchemaUpdate();

    verify(session, never()).dbSchemaCreateEngine();
    verify(session, never()).dbSchemaCreateHistory();
    verify(session, never()).dbSchemaCreateIdentity();
    verify(session, never()).dbSchemaCreateCmmn();
    verify(session, never()).dbSchemaCreateCmmnHistory();
    verify(session, never()).dbSchemaCreateDmn();
    verify(session, never()).dbSchemaCreateDmnHistory();
  }

  @Test
  public void shouldNotCreateDisabledPartsOnUpdate() {
    configure(false, false, false, false);
    tablesPresent(true, false, false, false, false, false, false);

    session.dbSchemaUpdate();

    verify(session, never()).dbSchemaCreateHistory();
    verify(session, never()).dbSchemaCreateIdentity();
    verify(session, never()).dbSchemaCreateCmmn();
    verify(session, never()).dbSchemaCreateCmmnHistory();
    verify(session, never()).dbSchemaCreateDmn();
    verify(session, never()).dbSchemaCreateDmnHistory();
  }

  @Test
  public void shouldNeedBothCmmnAndHistoryForCmmnHistoryOnUpdate() {
    configure(false, false, true, false);
    tablesPresent(true, true, true, true, false, true, true);

    session.dbSchemaUpdate();

    verify(session, never()).dbSchemaCreateCmmnHistory();
  }

  @Test
  public void shouldNeedBothDmnAndHistoryForDmnHistoryOnUpdate() {
    configure(true, false, false, true);
    tablesPresent(true, true, true, true, true, true, false);

    session.dbSchemaUpdate();

    verify(session).dbSchemaCreateDmnHistory();
    verify(session, never()).dbSchemaCreateDmn();
  }

  // listeners and defaults ------------------------------------------------------------------

  @Test
  public void shouldReportNoTableNamesByDefault() {
    assertThat(session.getTableNamesPresent()).isEmpty();
  }

  @Test
  public void shouldNotifyAllListenersWhenDbEntityIsLoaded() {
    EntityLoadListener first = mock(EntityLoadListener.class);
    EntityLoadListener second = mock(EntityLoadListener.class);
    session.addEntityLoadListener(first);
    session.addEntityLoadListener(second);
    PropertyEntity entity = new PropertyEntity();

    session.fireEntityLoaded(entity);

    verify(first).onEntityLoaded(entity);
    verify(second).onEntityLoaded(entity);
  }

  @Test
  public void shouldIgnoreLoadedObjectsThatAreNotDbEntities() {
    EntityLoadListener listener = mock(EntityLoadListener.class);
    session.addEntityLoadListener(listener);

    session.fireEntityLoaded("not an entity");
    session.fireEntityLoaded(null);

    verifyNoMoreInteractions(listener);
  }
}
