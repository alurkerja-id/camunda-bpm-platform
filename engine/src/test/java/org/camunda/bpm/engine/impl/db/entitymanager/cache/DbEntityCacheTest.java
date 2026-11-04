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
package org.camunda.bpm.engine.impl.db.entitymanager.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.db.DbEntity;
import org.camunda.bpm.engine.impl.db.HasDbReferences;
import org.junit.Before;
import org.junit.Test;

public class DbEntityCacheTest {

  public static class BaseEntity implements DbEntity {
    protected String id;
    protected String state = "initial";

    public BaseEntity(String id) {
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public void setId(String id) {
      this.id = id;
    }

    @Override
    public Object getPersistentState() {
      return state;
    }
  }

  public static class SubEntity extends BaseEntity {
    public SubEntity(String id) {
      super(id);
    }
  }

  public static class OtherEntity extends BaseEntity {
    public OtherEntity(String id) {
      super(id);
    }
  }

  public static class ReferencingEntity extends BaseEntity implements HasDbReferences {
    public ReferencingEntity(String id) {
      super(id);
    }

    @Override
    public Set<String> getReferencedEntityIds() {
      return Collections.singleton("ref");
    }

    @Override
    public Map<String, Class> getReferencedEntitiesIdAndClass() {
      return Collections.singletonMap("ref", BaseEntity.class);
    }
  }

  protected DbEntityCache cache;

  @Before
  public void setUp() {
    cache = new DbEntityCache();
  }

  protected DbEntityCache cacheWithSubKeyedAsBase() {
    DbEntityCacheKeyMapping mapping = new DbEntityCacheKeyMapping();
    mapping.registerEntityCacheKey(SubEntity.class, BaseEntity.class);
    return new DbEntityCache(mapping);
  }

  // get ///////////////////////////////////////////////////////

  @Test
  public void shouldReturnNullForUnknownTypeAndUnknownId() {
    assertThat(cache.get(BaseEntity.class, "1")).isNull();

    cache.putTransient(new BaseEntity("1"));
    assertThat(cache.get(BaseEntity.class, "2")).isNull();
  }

  @Test
  public void shouldGetEntityByTypeAndId() {
    BaseEntity entity = new BaseEntity("1");
    cache.putTransient(entity);

    assertThat(cache.get(BaseEntity.class, "1")).isSameAs(entity);
  }

  @Test
  public void shouldKeepEntitiesOfDifferentTypesWithSameIdApart() {
    BaseEntity base = new BaseEntity("1");
    OtherEntity other = new OtherEntity("1");
    cache.putTransient(base);
    cache.putTransient(other);

    assertThat(cache.get(BaseEntity.class, "1")).isSameAs(base);
    assertThat(cache.get(OtherEntity.class, "1")).isSameAs(other);
  }

  @Test
  public void shouldFailLookupWhenCachedEntityIsNotAssignableToRequestedType() {
    // both types share the cache key BaseEntity, but a BaseEntity is no SubEntity
    DbEntityCache keyed = cacheWithSubKeyedAsBase();
    keyed.putTransient(new BaseEntity("1"));

    assertThatThrownBy(() -> keyed.get(SubEntity.class, "1"))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("ENGINE-03001")
      .hasMessageContaining("Could not lookup entity of type")
      .hasMessageContaining("'1'");
  }

  @Test
  public void shouldGetSubtypeEntityWhenRequestedBySuperType() {
    DbEntityCache keyed = cacheWithSubKeyedAsBase();
    SubEntity sub = new SubEntity("1");
    keyed.putTransient(sub);

    assertThat(keyed.get(BaseEntity.class, "1")).isSameAs(sub);
    assertThat(keyed.get(SubEntity.class, "1")).isSameAs(sub);
  }

  // getEntitiesByType ////////////////////////////////////////////

  @Test
  public void shouldReturnEmptyListForUnknownType() {
    assertThat(cache.getEntitiesByType(BaseEntity.class)).isEmpty();
  }

  @Test
  public void shouldReturnAllEntitiesOfType() {
    BaseEntity first = new BaseEntity("1");
    BaseEntity second = new BaseEntity("2");
    cache.putTransient(first);
    cache.putTransient(second);
    cache.putTransient(new OtherEntity("3"));

    assertThat(cache.getEntitiesByType(BaseEntity.class)).containsExactlyInAnyOrder(first, second);
  }

  /**
   * Finding: the filter for types whose cache key differs from the type compares
   * {@code cachedEntity.getClass()} (always {@link CachedDbEntity}) instead of the class of the
   * wrapped entity, so it never matches and the result is always empty. Locks the current behavior.
   */
  @Test
  public void shouldReturnNothingWhenTypeDiffersFromCacheKey() {
    DbEntityCache keyed = cacheWithSubKeyedAsBase();
    keyed.putTransient(new SubEntity("1"));

    assertThat(keyed.getEntitiesByType(SubEntity.class)).isEmpty();
    assertThat(keyed.getEntitiesByType(BaseEntity.class)).hasSize(1);
  }

  // getCachedEntity ///////////////////////////////////////////

  @Test
  public void shouldGetCachedEntityByTypeAndId() {
    BaseEntity entity = new BaseEntity("1");
    cache.putTransient(entity);

    CachedDbEntity cached = cache.getCachedEntity(BaseEntity.class, "1");
    assertThat(cached.getEntity()).isSameAs(entity);
    assertThat(cached.getEntityState()).isEqualTo(DbEntityState.TRANSIENT);
    assertThat(cache.getCachedEntity(BaseEntity.class, "x")).isNull();
    assertThat(cache.getCachedEntity(OtherEntity.class, "1")).isNull();
  }

  @Test
  public void shouldGetCachedEntityByEntity() {
    BaseEntity entity = new BaseEntity("1");
    cache.putTransient(entity);

    assertThat(cache.getCachedEntity(entity).getEntity()).isSameAs(entity);
    assertThat(cache.getCachedEntity(new BaseEntity("2"))).isNull();
  }

  // put ///////////////////////////////////////////////////////

  @Test
  public void shouldPutPersistentWithCopyAndReferences() {
    cache.putPersistent(new ReferencingEntity("1"));

    CachedDbEntity cached = cache.getCachedEntity(ReferencingEntity.class, "1");
    assertThat(cached.getEntityState()).isEqualTo(DbEntityState.PERSISTENT);
    assertThat(cached.isDirty()).isFalse();
    assertThat(cached.getFlushRelevantEntityReferences()).containsExactly("ref");
  }

  @Test
  public void shouldPutPersistentThatTracksDirtyState() {
    BaseEntity entity = new BaseEntity("1");
    cache.putPersistent(entity);
    entity.state = "changed";

    assertThat(cache.getCachedEntity(entity).isDirty()).isTrue();
  }

  @Test
  public void shouldPutMergedWithReferencesAndNoCopy() {
    cache.putMerged(new BaseEntity("1"));

    CachedDbEntity cached = cache.getCachedEntity(BaseEntity.class, "1");
    assertThat(cached.getEntityState()).isEqualTo(DbEntityState.MERGED);
    assertThat(cached.getFlushRelevantEntityReferences()).isEmpty();
    assertThat(cached.copy).isNull();
  }

  @Test
  public void shouldFailPuttingTransientOverTransient() {
    cache.putTransient(new BaseEntity("1"));

    assertThatThrownBy(() -> cache.putTransient(new BaseEntity("1")))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("ENGINE-03002")
      .hasMessageContaining("TRANSIENT")
      .hasMessageContaining("'1'");
  }

  @Test
  public void shouldFailPuttingTransientOverNonTransientExistingEntity() {
    cache.putPersistent(new BaseEntity("1"));

    assertThatThrownBy(() -> cache.putTransient(new BaseEntity("1")))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("ENGINE-03003")
      .hasMessageContaining("PERSISTENT");
  }

  @Test
  public void shouldReplacePersistentWithPersistent() {
    BaseEntity replacement = new BaseEntity("1");
    cache.putPersistent(new BaseEntity("1"));
    cache.putPersistent(replacement);

    assertThat(cache.get(BaseEntity.class, "1")).isSameAs(replacement);
    assertThat(cache.isPersistent(replacement)).isTrue();
  }

  @Test
  public void shouldIgnorePersistentPutOverDeletedEntities() {
    BaseEntity persistentDeleted = new BaseEntity("1");
    cache.putPersistent(persistentDeleted);
    cache.setDeleted(persistentDeleted);
    cache.putPersistent(new BaseEntity("1"));

    BaseEntity mergedDeleted = new BaseEntity("2");
    cache.setDeleted(mergedDeleted); // not cached -> DELETED_MERGED
    cache.putPersistent(new BaseEntity("2"));

    assertThat(cache.getCachedEntity(BaseEntity.class, "1").getEntityState()).isEqualTo(DbEntityState.DELETED_PERSISTENT);
    assertThat(cache.get(BaseEntity.class, "1")).isSameAs(persistentDeleted);
    assertThat(cache.getCachedEntity(BaseEntity.class, "2").getEntityState()).isEqualTo(DbEntityState.DELETED_MERGED);
    assertThat(cache.get(BaseEntity.class, "2")).isSameAs(mergedDeleted);
  }

  @Test
  public void shouldFailPuttingPersistentOverTransientOrMerged() {
    cache.putTransient(new BaseEntity("1"));
    cache.putMerged(new BaseEntity("2"));

    assertThatThrownBy(() -> cache.putPersistent(new BaseEntity("1")))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("ENGINE-03002")
      .hasMessageContaining("PERSISTENT");
    assertThatThrownBy(() -> cache.putPersistent(new BaseEntity("2")))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("ENGINE-03002");
  }

  @Test
  public void shouldReplacePersistentAndMergedWithMerged() {
    cache.putPersistent(new BaseEntity("1"));
    BaseEntity mergedOverPersistent = new BaseEntity("1");
    cache.putMerged(mergedOverPersistent);

    BaseEntity mergedOverMerged = new BaseEntity("2");
    cache.putMerged(new BaseEntity("2"));
    cache.putMerged(mergedOverMerged);

    assertThat(cache.get(BaseEntity.class, "1")).isSameAs(mergedOverPersistent);
    assertThat(cache.getCachedEntity(BaseEntity.class, "1").getEntityState()).isEqualTo(DbEntityState.MERGED);
    assertThat(cache.get(BaseEntity.class, "2")).isSameAs(mergedOverMerged);
  }

  @Test
  public void shouldIgnoreMergedPutOverDeletedEntities() {
    BaseEntity deletedPersistent = new BaseEntity("1");
    cache.putPersistent(deletedPersistent);
    cache.setDeleted(deletedPersistent);
    cache.putMerged(new BaseEntity("1"));

    BaseEntity deletedMerged = new BaseEntity("2");
    cache.setDeleted(deletedMerged);
    cache.putMerged(new BaseEntity("2"));

    assertThat(cache.get(BaseEntity.class, "1")).isSameAs(deletedPersistent);
    assertThat(cache.get(BaseEntity.class, "2")).isSameAs(deletedMerged);
  }

  @Test
  public void shouldFailPuttingMergedOverTransient() {
    cache.putTransient(new BaseEntity("1"));

    assertThatThrownBy(() -> cache.putMerged(new BaseEntity("1")))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("ENGINE-03002")
      .hasMessageContaining("MERGED");
  }

  @Test
  public void shouldAlwaysAddDeleteStatesOverExistingEntity() {
    cache.putPersistent(new BaseEntity("1"));
    BaseEntity replacement = new BaseEntity("1");
    CachedDbEntity deleted = new CachedDbEntity();
    deleted.setEntity(replacement);
    deleted.setEntityState(DbEntityState.DELETED_TRANSIENT);

    cache.putInternal(deleted);

    assertThat(cache.getCachedEntity(BaseEntity.class, "1")).isSameAs(deleted);
  }

  // remove ////////////////////////////////////////////////////

  @Test
  public void shouldRemoveEntity() {
    BaseEntity entity = new BaseEntity("1");
    cache.putTransient(entity);

    assertThat(cache.remove(entity)).isTrue();
    assertThat(cache.contains(entity)).isFalse();
    assertThat(cache.remove(entity)).isFalse();
  }

  @Test
  public void shouldNotRemoveEntityOfUnknownType() {
    assertThat(cache.remove(new BaseEntity("1"))).isFalse();
  }

  @Test
  public void shouldRemoveCachedEntity() {
    BaseEntity entity = new BaseEntity("1");
    cache.putTransient(entity);

    cache.remove(cache.getCachedEntity(entity));

    assertThat(cache.contains(entity)).isFalse();
  }

  // state checks //////////////////////////////////////////////

  @Test
  public void shouldReportContains() {
    BaseEntity entity = new BaseEntity("1");
    assertThat(cache.contains(entity)).isFalse();

    cache.putTransient(entity);
    assertThat(cache.contains(entity)).isTrue();
  }

  @Test
  public void shouldReportPersistent() {
    BaseEntity entity = new BaseEntity("1");
    assertThat(cache.isPersistent(entity)).isFalse();

    cache.putMerged(entity);
    assertThat(cache.isPersistent(entity)).isFalse();

    BaseEntity persistentEntity = new BaseEntity("2");
    cache.putPersistent(persistentEntity);
    assertThat(cache.isPersistent(persistentEntity)).isTrue();
  }

  @Test
  public void shouldReportTransient() {
    BaseEntity entity = new BaseEntity("1");
    assertThat(cache.isTransient(entity)).isFalse();

    cache.putPersistent(entity);
    assertThat(cache.isTransient(entity)).isFalse();

    BaseEntity other = new BaseEntity("2");
    cache.putTransient(other);
    assertThat(cache.isTransient(other)).isTrue();
  }

  @Test
  public void shouldReportDeletedForAllDeleteStates() {
    BaseEntity unknown = new BaseEntity("0");
    BaseEntity transientEntity = new BaseEntity("1");
    BaseEntity persistentEntity = new BaseEntity("2");
    BaseEntity mergedEntity = new BaseEntity("3");
    BaseEntity notCachedEntity = new BaseEntity("4");
    BaseEntity live = new BaseEntity("5");
    cache.putTransient(transientEntity);
    cache.putPersistent(persistentEntity);
    cache.putMerged(mergedEntity);
    cache.putPersistent(live);

    assertThat(cache.isDeleted(unknown)).isFalse();
    assertThat(cache.isDeleted(live)).isFalse();

    cache.setDeleted(transientEntity);
    cache.setDeleted(persistentEntity);
    cache.setDeleted(mergedEntity);
    cache.setDeleted(notCachedEntity);

    assertThat(cache.getCachedEntity(transientEntity).getEntityState()).isEqualTo(DbEntityState.DELETED_TRANSIENT);
    assertThat(cache.getCachedEntity(persistentEntity).getEntityState()).isEqualTo(DbEntityState.DELETED_PERSISTENT);
    assertThat(cache.getCachedEntity(mergedEntity).getEntityState()).isEqualTo(DbEntityState.DELETED_MERGED);
    assertThat(cache.getCachedEntity(notCachedEntity).getEntityState()).isEqualTo(DbEntityState.DELETED_MERGED);
    assertThat(cache.isDeleted(transientEntity)).isTrue();
    assertThat(cache.isDeleted(persistentEntity)).isTrue();
    assertThat(cache.isDeleted(mergedEntity)).isTrue();
    assertThat(cache.isDeleted(notCachedEntity)).isTrue();
    assertThat(cache.isDeleted(live)).isFalse();
  }

  @Test
  public void shouldKeepDeletedStateWhenDeletingAgain() {
    BaseEntity entity = new BaseEntity("1");
    cache.putPersistent(entity);
    cache.setDeleted(entity);

    cache.setDeleted(entity);

    assertThat(cache.getCachedEntity(entity).getEntityState()).isEqualTo(DbEntityState.DELETED_PERSISTENT);
  }

  @Test
  public void shouldUndoDelete() {
    BaseEntity transientEntity = new BaseEntity("1");
    BaseEntity persistentEntity = new BaseEntity("2");
    cache.putTransient(transientEntity);
    cache.putPersistent(persistentEntity);
    cache.setDeleted(transientEntity);
    cache.setDeleted(persistentEntity);

    cache.undoDelete(transientEntity);
    cache.undoDelete(persistentEntity);

    assertThat(cache.getCachedEntity(transientEntity).getEntityState()).isEqualTo(DbEntityState.TRANSIENT);
    assertThat(cache.getCachedEntity(persistentEntity).getEntityState()).isEqualTo(DbEntityState.MERGED);
  }

  @Test
  public void shouldListAllCachedEntitiesAcrossTypes() {
    assertThat(cache.getCachedEntities()).isEmpty();

    cache.putTransient(new BaseEntity("1"));
    cache.putTransient(new BaseEntity("2"));
    cache.putTransient(new OtherEntity("1"));

    assertThat(cache.getCachedEntities()).hasSize(3);
  }
}
