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
package org.camunda.bpm.engine.impl.db.entitymanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.camunda.bpm.engine.OptimisticLockingException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.Page;
import org.camunda.bpm.engine.impl.cfg.IdGenerator;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.DbEntity;
import org.camunda.bpm.engine.impl.db.DbEntityLifecycleAware;
import org.camunda.bpm.engine.impl.db.FlushResult;
import org.camunda.bpm.engine.impl.db.ListQueryParameterObject;
import org.camunda.bpm.engine.impl.db.PersistenceSession;
import org.camunda.bpm.engine.impl.db.entitymanager.cache.DbEntityCache;
import org.camunda.bpm.engine.impl.db.entitymanager.cache.DbEntityCacheKeyMapping;
import org.camunda.bpm.engine.impl.db.entitymanager.cache.DbEntityState;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbBulkOperation;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbEntityOperation;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbOperation;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbOperation.State;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbOperationManager;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbOperationType;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutorContext;
import org.camunda.bpm.engine.impl.persistence.entity.ByteArrayEntity;
import org.camunda.bpm.engine.impl.persistence.entity.HistoricVariableInstanceEntity;
import org.camunda.bpm.engine.impl.persistence.entity.PropertyEntity;
import org.camunda.bpm.engine.repository.ResourceTypes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public class DbEntityManagerTest {

  protected IdGenerator idGenerator;
  protected PersistenceSession session;
  protected ProcessEngineConfigurationImpl config;
  protected DbEntityManager manager;

  @Before
  public void setUp() {
    idGenerator = mock(IdGenerator.class);
    session = mock(PersistenceSession.class);
    config = mock(ProcessEngineConfigurationImpl.class);
    when(config.getDbEntityCacheKeyMapping()).thenReturn(DbEntityCacheKeyMapping.emptyMapping());
    Context.setProcessEngineConfiguration(config);
    manager = new DbEntityManager(idGenerator, session);
  }

  @After
  public void tearDown() {
    Context.removeProcessEngineConfiguration();
    Context.removeJobExecutorContext();
  }

  protected static PropertyEntity property(String name, String value) {
    return new PropertyEntity(name, value);
  }

  // construction / entity cache initialization ////////////////////

  @Test
  public void shouldRegisterAsEntityLoadListenerOfPersistenceSession() {
    verify(session).addEntityLoadListener(manager);
  }

  @Test
  public void shouldTolerateMissingPersistenceSession() {
    DbEntityManager withoutSession = new DbEntityManager(idGenerator, null);

    assertThat(withoutSession.getDbEntityCache()).isNotNull();
    assertThat(withoutSession.getDbOperationManager()).isNotNull();
  }

  @Test
  public void shouldCreateOwnCacheWithoutProcessEngineConfiguration() {
    Context.removeProcessEngineConfiguration();

    DbEntityManager standalone = new DbEntityManager(idGenerator, session);

    assertThat(standalone.getDbEntityCache()).isNotNull().isNotSameAs(manager.getDbEntityCache());
    Context.setProcessEngineConfiguration(config);
  }

  @Test
  public void shouldCreateNewCacheWhenCacheReuseIsDisabled() {
    when(config.isDbEntityCacheReuseEnabled()).thenReturn(false);
    JobExecutorContext jobExecutorContext = new JobExecutorContext();
    Context.setJobExecutorContext(jobExecutorContext);

    DbEntityManager created = new DbEntityManager(idGenerator, session);

    assertThat(created.getDbEntityCache()).isNotNull();
    assertThat(jobExecutorContext.getEntityCache()).isNull();
  }

  @Test
  public void shouldCreateAndShareCacheViaJobExecutorContextWhenReuseIsEnabled() {
    when(config.isDbEntityCacheReuseEnabled()).thenReturn(true);
    JobExecutorContext jobExecutorContext = new JobExecutorContext();
    Context.setJobExecutorContext(jobExecutorContext);

    DbEntityManager first = new DbEntityManager(idGenerator, session);
    DbEntityManager second = new DbEntityManager(idGenerator, session);

    assertThat(jobExecutorContext.getEntityCache()).isNotNull();
    assertThat(first.getDbEntityCache()).isSameAs(jobExecutorContext.getEntityCache());
    assertThat(second.getDbEntityCache()).isSameAs(first.getDbEntityCache());
  }

  @Test
  public void shouldNotShareCacheWhenReuseEnabledButNoJobExecutorContext() {
    when(config.isDbEntityCacheReuseEnabled()).thenReturn(true);

    DbEntityManager created = new DbEntityManager(idGenerator, session);

    assertThat(created.getDbEntityCache()).isNotSameAs(manager.getDbEntityCache());
  }

  // selects ///////////////////////////////////////////////////

  @Test
  public void shouldSelectListWithDefaultPaging() {
    List<Object> rows = Arrays.<Object>asList("a", "b");
    doReturn(rows).when(session).selectList(eq("stmt"), any());

    assertThat(manager.selectList("stmt")).containsExactly("a", "b");
    assertThat(manager.selectList("stmt", "param")).containsExactly("a", "b");
  }

  @Test
  public void shouldSelectListWithPage() {
    doReturn(Collections.emptyList()).when(session).selectList(eq("stmt"), any());

    assertThat(manager.selectList("stmt", "param", new Page(2, 5))).isEmpty();
    assertThat(manager.selectList("stmt", "param", (Page) null)).isEmpty();

    verify(session, times(2)).selectList(eq("stmt"), any(ListQueryParameterObject.class));
  }

  @Test
  public void shouldIgnorePageForListQueryParameterObjectOverload() {
    ListQueryParameterObject parameter = new ListQueryParameterObject("p", 3, 4);
    doReturn(Arrays.<Object>asList("x")).when(session).selectList("stmt", parameter);

    assertThat(manager.selectList("stmt", parameter, new Page(0, 1))).containsExactly("x");
  }

  @Test
  public void shouldReturnEmptyListWithoutQueryingWhenPagingIsMinusOne() {
    assertThat(manager.selectList("stmt", "param", -1, 10)).isEmpty();
    assertThat(manager.selectList("stmt", "param", 0, -1)).isEmpty();

    verify(session, never()).selectList(any(), any());
  }

  @Test
  public void shouldReturnCachedInstancesForLoadedEntities() {
    PropertyEntity cached = property("a", "cached");
    manager.getDbEntityCache().putPersistent(cached);
    PropertyEntity loadedDuplicate = property("a", "fresh");
    PropertyEntity loadedNew = property("b", "fresh");
    doReturn(new ArrayList<Object>(Arrays.asList(loadedDuplicate, loadedNew))).when(session).selectList(eq("stmt"), any());

    List result = manager.selectList("stmt", "param");

    assertThat(result).hasSize(2);
    assertThat(result.get(0)).isSameAs(cached);
    assertThat(result.get(1)).isSameAs(loadedNew);
  }

  @Test
  public void shouldNotFilterEmptyOrNonEntityResults() {
    doReturn(new ArrayList<Object>()).when(session).selectList(eq("empty"), any());
    doReturn(new ArrayList<Object>(Arrays.asList(null, "x"))).when(session).selectList(eq("nullFirst"), any());
    doReturn(new ArrayList<Object>(Arrays.asList("a", "b"))).when(session).selectList(eq("strings"), any());

    assertThat(manager.selectList("empty")).isEmpty();
    assertThat(manager.selectList("nullFirst")).containsExactly(null, "x");
    assertThat(manager.selectList("strings")).containsExactly("a", "b");
  }

  @Test
  public void shouldFilterSelectOneThroughCache() {
    PropertyEntity cached = property("a", "cached");
    manager.getDbEntityCache().putPersistent(cached);
    when(session.selectOne("stmt", "p")).thenReturn(property("a", "fresh"));

    assertThat(manager.selectOne("stmt", "p")).isSameAs(cached);
  }

  @Test
  public void shouldReturnUncachedEntityAndPlainValuesFromSelectOne() {
    PropertyEntity loaded = property("a", "fresh");
    when(session.selectOne("entity", "p")).thenReturn(loaded);
    when(session.selectOne("count", "p")).thenReturn(42L);
    when(session.selectOne("none", "p")).thenReturn(null);

    assertThat(manager.selectOne("entity", "p")).isSameAs(loaded);
    assertThat(manager.selectOne("count", "p")).isEqualTo(42L);
    assertThat(manager.selectOne("none", "p")).isNull();
  }

  @Test
  public void shouldSelectBoolean() {
    doReturn(Arrays.<Object>asList(0, 1)).when(session).selectList("yes", "p");
    doReturn(Arrays.<Object>asList(0)).when(session).selectList("no", "p");
    doReturn(null).when(session).selectList("null", "p");

    assertThat(manager.selectBoolean("yes", "p")).isTrue();
    assertThat(manager.selectBoolean("no", "p")).isFalse();
    assertThat(manager.selectBoolean("null", "p")).isFalse();
  }

  @Test
  public void shouldSelectByIdFromCacheBeforeSession() {
    PropertyEntity cached = property("a", "cached");
    manager.getDbEntityCache().putPersistent(cached);

    assertThat(manager.selectById(PropertyEntity.class, "a")).isSameAs(cached);
    verify(session, never()).selectById(any(), any());
  }

  @Test
  public void shouldSelectByIdFromSessionWhenNotCached() {
    PropertyEntity loaded = property("a", "loaded");
    when(session.selectById(PropertyEntity.class, "a")).thenReturn(loaded);

    assertThat(manager.selectById(PropertyEntity.class, "a")).isSameAs(loaded);
    assertThat(manager.selectById(PropertyEntity.class, "missing")).isNull();
  }

  @Test
  public void shouldExposeCachedEntityLookups() {
    PropertyEntity cached = property("a", "v");
    manager.getDbEntityCache().putTransient(cached);

    assertThat(manager.getCachedEntity(PropertyEntity.class, "a")).isSameAs(cached);
    assertThat(manager.getCachedEntitiesByType(PropertyEntity.class)).containsExactly(cached);
    assertThat(manager.contains(cached)).isTrue();
    assertThat(manager.contains(property("other", "v"))).isFalse();
  }

  // onEntityLoaded ////////////////////////////////////////////

  @Test
  public void shouldPutLoadedEntityIntoCacheAsPersistent() {
    PropertyEntity loaded = property("a", "v");

    manager.onEntityLoaded(loaded);

    assertThat(manager.getDbEntityCache().isPersistent(loaded)).isTrue();
  }

  @Test
  public void shouldNotReplaceAlreadyCachedEntityOnLoad() {
    PropertyEntity cached = property("a", "cached");
    manager.getDbEntityCache().putPersistent(cached);

    manager.onEntityLoaded(property("a", "other"));

    assertThat(manager.getCachedEntity(PropertyEntity.class, "a")).isSameAs(cached);
  }

  public static class LifecycleEntity extends PropertyEntity implements DbEntityLifecycleAware {
    public int postLoadCalls;

    public LifecycleEntity(String name) {
      super(name, "v");
    }

    @Override
    public void postLoad() {
      postLoadCalls++;
    }
  }

  @Test
  public void shouldInvokePostLoadOnlyWhenEntityIsNewlyCached() {
    LifecycleEntity entity = new LifecycleEntity("a");

    manager.onEntityLoaded(entity);
    manager.onEntityLoaded(entity);

    assertThat(entity.postLoadCalls).isEqualTo(1);
  }

  // lock / dirty / force update ///////////////////////////////

  @Test
  public void shouldDelegateLock() {
    manager.lock("stmt");
    manager.lock("stmt2", "param");

    verify(session).lock("stmt", null);
    verify(session).lock("stmt2", "param");
  }

  @Test
  public void shouldReportDirtyStates() {
    PropertyEntity unknown = property("u", "v");
    PropertyEntity clean = property("c", "v");
    PropertyEntity changed = property("d", "v");
    PropertyEntity merged = property("m", "v");
    manager.getDbEntityCache().putPersistent(clean);
    manager.getDbEntityCache().putPersistent(changed);
    manager.getDbEntityCache().putMerged(merged);
    changed.setValue("changed");

    assertThat(manager.isDirty(unknown)).isFalse();
    assertThat(manager.isDirty(clean)).isFalse();
    assertThat(manager.isDirty(changed)).isTrue();
    assertThat(manager.isDirty(merged)).isTrue();
  }

  @Test
  public void shouldForceUpdateOnlyPersistentEntities() {
    PropertyEntity persistent = property("p", "v");
    PropertyEntity transientEntity = property("t", "v");
    manager.getDbEntityCache().putPersistent(persistent);
    manager.getDbEntityCache().putTransient(transientEntity);

    assertThat(manager.getDbEntityCache().getCachedEntity(persistent).isDirty()).isFalse();

    manager.forceUpdate(persistent);
    manager.forceUpdate(transientEntity);
    manager.forceUpdate(property("unknown", "v"));

    assertThat(manager.isDirty(persistent)).isTrue();
    assertThat(manager.getDbEntityCache().getCachedEntity(transientEntity).getEntityState()).isEqualTo(DbEntityState.TRANSIENT);
    assertThat(manager.contains(property("unknown", "v"))).isFalse();
  }

  // insert / merge / delete ///////////////////////////////////

  @Test
  public void shouldGenerateIdOnInsertWhenMissing() {
    when(idGenerator.getNextId()).thenReturn("generated");
    ByteArrayEntity entity = new ByteArrayEntity("name", new byte[] { 1 });

    manager.insert(entity);

    assertThat(entity.getId()).isEqualTo("generated");
    assertThat(manager.getDbEntityCache().isTransient(entity)).isTrue();
  }

  @Test
  public void shouldKeepExistingIdOnInsert() {
    PropertyEntity entity = property("mine", "v");

    manager.insert(entity);

    assertThat(entity.getId()).isEqualTo("mine");
    verify(idGenerator, never()).getNextId();
  }

  @Test
  public void shouldRejectInsertWithReservedId() {
    PropertyEntity entity = property("*", "v");

    assertThatThrownBy(() -> manager.insert(entity))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("has an invalid id");
    assertThat(manager.contains(entity)).isFalse();
  }

  @Test
  public void shouldMergeEntityWithId() {
    PropertyEntity entity = property("a", "v");

    manager.merge(entity);

    assertThat(manager.getDbEntityCache().getCachedEntity(entity).getEntityState()).isEqualTo(DbEntityState.MERGED);
  }

  @Test
  public void shouldRejectMergeWithoutId() {
    assertThatThrownBy(() -> manager.merge(property(null, "v")))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("ENGINE-03007")
      .hasMessageContaining("without id");
  }

  @Test
  public void shouldDeleteAndUndoDeleteAndPruneDeletedEntities() {
    PropertyEntity kept = property("k", "v");
    PropertyEntity deleted = property("d", "v");
    manager.getDbEntityCache().putPersistent(kept);
    manager.getDbEntityCache().putPersistent(deleted);

    manager.delete(deleted);

    assertThat(manager.isDeleted(deleted)).isTrue();
    assertThat(manager.isDeleted(kept)).isFalse();
    assertThat(manager.pruneDeletedEntities(Arrays.asList(kept, deleted))).containsExactly(kept);

    manager.undoDelete(deleted);

    assertThat(manager.isDeleted(deleted)).isFalse();
    assertThat(manager.pruneDeletedEntities(Arrays.asList(kept, deleted))).containsExactly(kept, deleted);
  }

  // bulk operations ///////////////////////////////////////////

  @Test
  public void shouldScheduleBulkUpdateAndDeleteOperations() {
    manager.update(PropertyEntity.class, "updateStmt", "up");
    manager.delete(PropertyEntity.class, "deleteStmt", "dp");

    List<DbOperation> flush = manager.getDbOperationManager().calculateFlush();

    assertThat(flush).hasSize(2);
    for (DbOperation operation : flush) {
      DbBulkOperation bulk = (DbBulkOperation) operation;
      assertThat(bulk.getEntityType()).isEqualTo(PropertyEntity.class);
      if (bulk.getOperationType() == DbOperationType.UPDATE_BULK) {
        assertThat(bulk.getStatement()).isEqualTo("updateStmt");
        assertThat(bulk.getParameter()).isEqualTo("up");
      } else {
        assertThat(bulk.getOperationType()).isEqualTo(DbOperationType.DELETE_BULK);
        assertThat(bulk.getStatement()).isEqualTo("deleteStmt");
        assertThat(bulk.getParameter()).isEqualTo("dp");
      }
    }
    assertThat(flush).extracting(DbOperation::getOperationType)
      .containsExactlyInAnyOrder(DbOperationType.UPDATE_BULK, DbOperationType.DELETE_BULK);
  }

  @Test
  public void shouldReturnPreserveOrderOperationsInCallOrder() {
    DbOperation first = manager.deletePreserveOrder(PropertyEntity.class, "first", null);
    DbOperation second = manager.updatePreserveOrder(PropertyEntity.class, "second", null);

    assertThat(first.getOperationType()).isEqualTo(DbOperationType.DELETE_BULK);
    assertThat(second.getOperationType()).isEqualTo(DbOperationType.UPDATE_BULK);
    assertThat(manager.getDbOperationManager().calculateFlush()).containsExactly(first, second);
  }

  // flush: entity cache -> operations ////////////////////////////

  @Test
  public void shouldTranslateCacheStatesIntoOperationsOnFlush() {
    PropertyEntity inserted = property("insert", "v");
    PropertyEntity dirty = property("dirty", "v");
    PropertyEntity clean = property("clean", "v");
    PropertyEntity merged = property("merged", "v");
    PropertyEntity deletedPersistent = property("delPersistent", "v");
    PropertyEntity deletedTransient = property("delTransient", "v");
    DbEntityCache cache = manager.getDbEntityCache();
    cache.putTransient(inserted);
    cache.putPersistent(dirty);
    cache.putPersistent(clean);
    cache.putMerged(merged);
    cache.putPersistent(deletedPersistent);
    cache.putTransient(deletedTransient);
    dirty.setValue("changed");
    cache.setDeleted(deletedPersistent);
    cache.setDeleted(deletedTransient);
    when(session.executeDbOperations(anyList())).thenReturn(FlushResult.allApplied());

    manager.flush();

    org.mockito.ArgumentCaptor<List<DbOperation>> captor = org.mockito.ArgumentCaptor.forClass((Class) List.class);
    verify(session).executeDbOperations(captor.capture());
    List<DbOperation> operations = captor.getValue();
    assertThat(operations).hasSize(4);
    assertThat(operationsOf(operations, DbOperationType.INSERT)).containsExactly(inserted);
    assertThat(operationsOf(operations, DbOperationType.UPDATE)).containsExactlyInAnyOrder(dirty, merged);
    assertThat(operationsOf(operations, DbOperationType.DELETE)).containsExactly(deletedPersistent);

    assertThat(cache.isPersistent(inserted)).isTrue();
    assertThat(cache.isPersistent(merged)).isTrue();
    assertThat(cache.isPersistent(dirty)).isTrue();
    assertThat(cache.getCachedEntity(dirty).isDirty()).isFalse();
    assertThat(cache.contains(deletedPersistent)).isFalse();
    assertThat(cache.contains(deletedTransient)).isFalse();
    assertThat(cache.contains(clean)).isTrue();
  }

  protected static List<DbEntity> operationsOf(List<DbOperation> operations, DbOperationType type) {
    List<DbEntity> entities = new ArrayList<>();
    for (DbOperation operation : operations) {
      if (operation.getOperationType() == type) {
        entities.add(((DbEntityOperation) operation).getEntity());
      }
    }
    return entities;
  }

  @Test
  public void shouldNotCallSessionWhenNothingToFlush() {
    manager.flush();

    verify(session, never()).executeDbOperations(anyList());
  }

  @Test
  public void shouldFlushSingleEntityOnly() {
    PropertyEntity first = property("first", "v");
    PropertyEntity second = property("second", "v");
    manager.getDbEntityCache().putTransient(first);
    manager.getDbEntityCache().putTransient(second);
    when(session.executeDbOperations(anyList())).thenReturn(FlushResult.allApplied());

    manager.flushEntity(first);

    org.mockito.ArgumentCaptor<List<DbOperation>> captor = org.mockito.ArgumentCaptor.forClass((Class) List.class);
    verify(session).executeDbOperations(captor.capture());
    assertThat(operationsOf(captor.getValue(), DbOperationType.INSERT)).containsExactly(first);
    assertThat(manager.getDbEntityCache().isTransient(second)).isTrue();
  }

  @Test
  public void shouldTolerateFlushOfEntityThatIsNotCached() {
    manager.flushEntity(property("unknown", "v"));

    verify(session, never()).executeDbOperations(anyList());
  }

  @Test
  public void shouldFlushOperationsInBatches() {
    for (int i = 0; i < DbEntityManager.BATCH_SIZE + 5; i++) {
      manager.getDbEntityCache().putTransient(property("p" + i, "v"));
    }
    when(session.executeDbOperations(anyList())).thenReturn(FlushResult.allApplied());

    manager.flush();

    org.mockito.ArgumentCaptor<List<DbOperation>> captor = org.mockito.ArgumentCaptor.forClass((Class) List.class);
    verify(session, times(2)).executeDbOperations(captor.capture());
    assertThat(captor.getAllValues().get(0)).hasSize(DbEntityManager.BATCH_SIZE);
    assertThat(captor.getAllValues().get(1)).hasSize(5);
  }

  @Test
  public void shouldKeepFlushingRemainingOperations() {
    manager.getDbEntityCache().putTransient(property("a", "v"));
    manager.getDbEntityCache().putTransient(property("b", "v"));
    when(session.executeDbOperations(anyList())).thenAnswer(invocation -> {
      List<DbOperation> operations = (List<DbOperation>) invocation.getArguments()[0];
      if (operations.size() == 2) {
        return FlushResult.withFailuresAndRemaining(Collections.<DbOperation>emptyList(), operations.subList(1, 2));
      }
      return FlushResult.allApplied();
    });

    manager.flush();

    verify(session, times(2)).executeDbOperations(anyList());
  }

  @Test
  public void shouldFailWhenFlushMakesNoProgress() {
    manager.getDbEntityCache().putTransient(property("a", "v"));
    when(session.executeDbOperations(anyList())).thenAnswer(invocation ->
      FlushResult.withFailuresAndRemaining(Collections.<DbOperation>emptyList(), (List<DbOperation>) invocation.getArguments()[0]));

    assertThatThrownBy(() -> manager.flush())
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("Database flush did not process any operations");
  }

  @Test
  public void shouldWrapUnexpectedSessionFailure() {
    manager.getDbEntityCache().putTransient(property("a", "v"));
    when(session.executeDbOperations(anyList())).thenThrow(new IllegalStateException("boom"));

    Throwable thrown = catchThrowable(() -> manager.flush());

    assertThat(thrown).isInstanceOf(ProcessEngineException.class);
    assertThat(thrown.getCause()).isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("ENGINE-03083")
      .hasMessageContaining("boom");
    assertThat(thrown.getCause().getCause()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldThrowFlushExceptionForFailedErrorOperation() {
    manager.getDbEntityCache().putTransient(property("a", "v"));
    when(session.executeDbOperations(anyList())).thenAnswer(invocation -> {
      DbOperation failed = ((List<DbOperation>) invocation.getArguments()[0]).get(0);
      failed.setState(State.FAILED_ERROR);
      failed.setFailure(new IllegalArgumentException("db says no"));
      return FlushResult.withFailures(Collections.singletonList(failed));
    });

    Throwable thrown = catchThrowable(() -> manager.flush());

    assertThat(thrown).isInstanceOf(ProcessEngineException.class);
    // the persistence exception is wrapped; the detailed exception is its cause
    assertThat(thrown.getCause()).isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("ENGINE-03004")
      .hasMessageContaining("db says no");
    assertThat(thrown.getCause().getCause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldFailOnFailedOperationNotInFailureState() {
    manager.getDbEntityCache().putTransient(property("a", "v"));
    when(session.executeDbOperations(anyList())).thenAnswer(invocation -> {
      DbOperation failed = ((List<DbOperation>) invocation.getArguments()[0]).get(0);
      failed.setState(State.APPLIED);
      return FlushResult.withFailures(Collections.singletonList(failed));
    });

    assertThatThrownBy(() -> manager.flush())
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("not in an error state");
  }

  // flush: foreign keys ///////////////////////////////////////

  @Test
  public void shouldToggleForeignKeysAroundFlushAndResetFlag() {
    manager.update(PropertyEntity.class, "stmt", null);
    when(session.executeDbOperations(anyList())).thenReturn(FlushResult.allApplied());
    manager.setIgnoreForeignKeysForNextFlush(true);

    manager.flush();

    InOrder order = inOrder(session);
    order.verify(session).executeNonEmptyUpdateStmt("toggleForeignKey", false);
    order.verify(session).flushOperations();
    order.verify(session).executeDbOperations(anyList());
    order.verify(session).executeNonEmptyUpdateStmt("toggleForeignKey", true);
    order.verify(session).flushOperations();
    assertThat(manager.isIgnoreForeignKeysForNextFlush).isFalse();
  }

  @Test
  public void shouldReenableForeignKeysEvenWhenFlushFails() {
    manager.update(PropertyEntity.class, "stmt", null);
    when(session.executeDbOperations(anyList())).thenThrow(new IllegalStateException("boom"));
    manager.setIgnoreForeignKeysForNextFlush(true);

    assertThatThrownBy(() -> manager.flush()).isInstanceOf(ProcessEngineException.class);

    verify(session).executeNonEmptyUpdateStmt("toggleForeignKey", true);
    assertThat(manager.isIgnoreForeignKeysForNextFlush).isFalse();
  }

  @Test
  public void shouldNotToggleForeignKeysWhenThereIsNothingToFlush() {
    manager.setIgnoreForeignKeysForNextFlush(true);

    manager.flush();

    verify(session, never()).executeNonEmptyUpdateStmt(any(), any());
    assertThat(manager.isIgnoreForeignKeysForNextFlush).isTrue();
  }

  // optimistic locking ////////////////////////////////////////

  protected void failNextFlushWith(State state) {
    when(session.executeDbOperations(anyList())).thenAnswer(invocation -> {
      DbOperation failed = ((List<DbOperation>) invocation.getArguments()[0]).get(0);
      failed.setState(state);
      return FlushResult.withFailures(Collections.singletonList(failed));
    });
  }

  protected void cacheDirtyProperty(String name) {
    PropertyEntity entity = property(name, "v");
    manager.getDbEntityCache().putPersistent(entity);
    entity.setValue("changed");
  }

  @Test
  public void shouldThrowOptimisticLockingExceptionOnConcurrentModification() {
    cacheDirtyProperty("a");
    failNextFlushWith(State.FAILED_CONCURRENT_MODIFICATION);
    when(config.isSkipHistoryOptimisticLockingExceptions()).thenReturn(false);

    assertThatThrownBy(() -> manager.flush())
      .isInstanceOf(OptimisticLockingException.class)
      .hasMessageContaining("ENGINE-03005");
  }

  @Test
  public void shouldIgnoreConcurrentModificationWhenListenerSaysIgnore() {
    cacheDirtyProperty("a");
    failNextFlushWith(State.FAILED_CONCURRENT_MODIFICATION);
    List<DbOperation> seen = new ArrayList<>();
    manager.registerOptimisticLockingListener(listener(null, OptimisticLockingResult.IGNORE, seen));

    manager.flush();

    assertThat(seen).hasSize(1);
  }

  @Test
  public void shouldOnlyInvokeListenersMatchingTheEntityType() {
    cacheDirtyProperty("a");
    failNextFlushWith(State.FAILED_CONCURRENT_MODIFICATION);
    List<DbOperation> matching = new ArrayList<>();
    List<DbOperation> other = new ArrayList<>();
    manager.registerOptimisticLockingListener(listener(ByteArrayEntity.class, OptimisticLockingResult.IGNORE, other));
    manager.registerOptimisticLockingListener(listener(PropertyEntity.class, OptimisticLockingResult.IGNORE, matching));

    manager.flush();

    assertThat(other).isEmpty();
    assertThat(matching).hasSize(1);
  }

  @Test
  public void shouldThrowWhenLastMatchingListenerSaysThrow() {
    cacheDirtyProperty("a");
    failNextFlushWith(State.FAILED_CONCURRENT_MODIFICATION);
    manager.registerOptimisticLockingListener(listener(null, OptimisticLockingResult.IGNORE, new ArrayList<>()));
    manager.registerOptimisticLockingListener(listener(null, OptimisticLockingResult.THROW, new ArrayList<>()));

    assertThatThrownBy(() -> manager.flush()).isInstanceOf(OptimisticLockingException.class);
  }

  @Test
  public void shouldIgnoreHistoricEntityFailureWhenConfigured() {
    HistoricVariableInstanceEntity historic = new HistoricVariableInstanceEntity();
    historic.setId("h");
    manager.getDbEntityCache().putMerged(historic);
    failNextFlushWith(State.FAILED_CONCURRENT_MODIFICATION);
    when(config.isSkipHistoryOptimisticLockingExceptions()).thenReturn(true);

    manager.flush();
  }

  @Test
  public void shouldNotIgnoreHistoricEntityFailureWhenNotConfigured() {
    HistoricVariableInstanceEntity historic = new HistoricVariableInstanceEntity();
    historic.setId("h");
    manager.getDbEntityCache().putMerged(historic);
    failNextFlushWith(State.FAILED_CONCURRENT_MODIFICATION);
    when(config.isSkipHistoryOptimisticLockingExceptions()).thenReturn(false);

    assertThatThrownBy(() -> manager.flush()).isInstanceOf(OptimisticLockingException.class);
  }

  @Test
  public void shouldNotIgnoreFailureOfNonHistoricEntityEvenWhenConfigured() {
    cacheDirtyProperty("a");
    failNextFlushWith(State.FAILED_CONCURRENT_MODIFICATION);
    when(config.isSkipHistoryOptimisticLockingExceptions()).thenReturn(true);

    assertThatThrownBy(() -> manager.flush()).isInstanceOf(OptimisticLockingException.class);
  }

  @Test
  public void shouldTreatHistoryByteArrayAsHistoric() {
    ByteArrayEntity history = new ByteArrayEntity("name", new byte[] { 1 }, ResourceTypes.HISTORY);
    ByteArrayEntity repository = new ByteArrayEntity("name", new byte[] { 1 }, ResourceTypes.REPOSITORY);

    assertThat(manager.isHistoricByteArray(history)).isTrue();
    assertThat(manager.isHistoricByteArray(repository)).isFalse();
    assertThat(manager.isHistoricByteArray(property("a", "v"))).isFalse();
  }

  @Test
  public void shouldAlwaysThrowForConcurrentModificationWithRolledBackTransaction() {
    cacheDirtyProperty("a");
    failNextFlushWith(State.FAILED_CONCURRENT_MODIFICATION_EXCEPTION);
    List<DbOperation> seen = new ArrayList<>();
    manager.registerOptimisticLockingListener(listener(null, OptimisticLockingResult.IGNORE, seen));

    // even though the listener asks to ignore, the transaction is already aborted
    assertThatThrownBy(() -> manager.flush())
      .isInstanceOf(OptimisticLockingException.class)
      .hasMessageContaining("ENGINE-03005");
    assertThat(seen).hasSize(1);
  }

  @Test
  public void shouldThrowForRolledBackTransactionWithoutListeners() {
    cacheDirtyProperty("a");
    failNextFlushWith(State.FAILED_CONCURRENT_MODIFICATION_EXCEPTION);

    assertThatThrownBy(() -> manager.flush()).isInstanceOf(OptimisticLockingException.class);
  }

  protected static OptimisticLockingListener listener(final Class<? extends DbEntity> type,
      final OptimisticLockingResult result, final List<DbOperation> seen) {
    return new OptimisticLockingListener() {
      @Override
      public Class<? extends DbEntity> getEntityType() {
        return type;
      }

      @Override
      public OptimisticLockingResult failedOperation(DbOperation operation) {
        seen.add(operation);
        return result;
      }
    };
  }

  // misc //////////////////////////////////////////////////////

  @Test
  public void shouldCloseWithoutSideEffects() {
    manager.close();

    verify(session, never()).flushOperations();
  }

  @Test
  public void shouldDelegateTableNamesLookup() {
    when(session.getTableNamesPresent()).thenReturn(Arrays.asList("ACT_GE_PROPERTY"));

    assertThat(manager.getTableNamesPresentInDatabase()).containsExactly("ACT_GE_PROPERTY");
  }

  @Test
  public void shouldAllowReplacingCacheAndOperationManager() {
    DbEntityCache cache = new DbEntityCache();
    DbOperationManager operationManager = new DbOperationManager();

    manager.setDbEntityCache(cache);
    manager.setDbOperationManager(operationManager);

    assertThat(manager.getDbEntityCache()).isSameAs(cache);
    assertThat(manager.getDbOperationManager()).isSameAs(operationManager);
  }

  @Test
  public void shouldCreateQueryObjects() {
    assertThat(manager.createDeploymentQuery()).isNotNull();
    assertThat(manager.createProcessDefinitionQuery()).isNotNull();
    assertThat(manager.createCaseDefinitionQuery()).isNotNull();
    assertThat(manager.createProcessInstanceQuery()).isNotNull();
    assertThat(manager.createExecutionQuery()).isNotNull();
    assertThat(manager.createTaskQuery()).isNotNull();
    assertThat(manager.createJobQuery()).isNotNull();
    assertThat(manager.createHistoricProcessInstanceQuery()).isNotNull();
    assertThat(manager.createHistoricActivityInstanceQuery()).isNotNull();
    assertThat(manager.createHistoricTaskInstanceQuery()).isNotNull();
    assertThat(manager.createHistoricDetailQuery()).isNotNull();
    assertThat(manager.createHistoricVariableInstanceQuery()).isNotNull();
    assertThat(manager.createHistoricJobLogQuery()).isNotNull();
    assertThat(manager.createUserQuery()).isNotNull();
    assertThat(manager.createGroupQuery()).isNotNull();
  }
}
