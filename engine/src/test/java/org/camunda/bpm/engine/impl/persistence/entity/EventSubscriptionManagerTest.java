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
package org.camunda.bpm.engine.impl.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.impl.EventSubscriptionQueryImpl;
import org.camunda.bpm.engine.impl.Page;
import org.camunda.bpm.engine.impl.db.ListQueryParameterObject;
import org.camunda.bpm.engine.impl.db.entitymanager.DbEntityManager;
import org.camunda.bpm.engine.impl.event.EventType;
import org.camunda.bpm.engine.impl.jobexecutor.ProcessEventJobHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests the query wrappers of {@link EventSubscriptionManager} and the merge of subscriptions
 * created in the current command (not yet visible to a query) into signal lookups.
 */
public class EventSubscriptionManagerTest {

  /** Exposes the collaborators AbstractManager normally pulls from the command context. */
  static class TestableManager extends EventSubscriptionManager {
    DbEntityManager dbEntityManager;
    JobManager jobManager;
    TenantManager tenantManager;
    AuthorizationManager authorizationManager;

    @Override
    protected DbEntityManager getDbEntityManager() {
      return dbEntityManager;
    }

    @Override
    protected JobManager getJobManager() {
      return jobManager;
    }

    @Override
    protected TenantManager getTenantManager() {
      return tenantManager;
    }

    @Override
    protected AuthorizationManager getAuthorizationManager() {
      return authorizationManager;
    }
  }

  protected TestableManager manager;
  protected DbEntityManager dbEntityManager;
  protected JobManager jobManager;
  protected TenantManager tenantManager;
  protected AuthorizationManager authorizationManager;

  @Before
  public void setUp() {
    manager = new TestableManager();
    dbEntityManager = mock(DbEntityManager.class);
    jobManager = mock(JobManager.class);
    tenantManager = mock(TenantManager.class);
    authorizationManager = mock(AuthorizationManager.class);
    manager.dbEntityManager = dbEntityManager;
    manager.jobManager = jobManager;
    manager.tenantManager = tenantManager;
    manager.authorizationManager = authorizationManager;
  }

  protected EventSubscriptionEntity subscription(EventType type, String id, String eventName, String executionId, String tenantId) {
    EventSubscriptionEntity entity = new EventSubscriptionEntity(type);
    entity.setId(id);
    entity.setEventName(eventName);
    entity.setExecutionId(executionId);
    entity.setTenantId(tenantId);
    return entity;
  }

  // insert / delete -------------------------------------------------------------------------

  @Test
  public void shouldInsertSubscriptionThroughDbEntityManager() {
    EventSubscriptionEntity message = subscription(EventType.MESSAGE, "m", "msg", "e1", null);

    manager.insert(message);

    verify(dbEntityManager).insert(message);
  }

  @Test
  public void shouldNotTrackNonSignalSubscriptionsAsCreatedInThisCommand() {
    manager.insert(subscription(EventType.MESSAGE, "m", "sig", "e1", null));
    when(dbEntityManager.selectList(any(String.class), any(Object.class))).thenReturn(Collections.emptyList());
    when(tenantManager.configureQuery((Object) "sig")).thenReturn(new ListQueryParameterObject());

    assertThat(manager.findSignalEventSubscriptionsByEventName("sig")).isEmpty();
  }

  @Test
  public void shouldDeleteSubscriptionAndItsPendingAsyncJobs() {
    EventSubscriptionEntity signal = subscription(EventType.SIGNAL, "s1", "sig", "e1", "t1");
    JobEntity job1 = mock(JobEntity.class);
    JobEntity job2 = mock(JobEntity.class);
    when(jobManager.findJobsByConfiguration(ProcessEventJobHandler.TYPE, "s1", "t1")).thenReturn(Arrays.asList(job1, job2));

    manager.deleteEventSubscription(signal);

    verify(dbEntityManager).delete(signal);
    verify(job1).delete();
    verify(job2).delete();
  }

  @Test
  public void shouldForgetDeletedSignalSubscriptionCreatedInThisCommand() {
    EventSubscriptionEntity signal = subscription(EventType.SIGNAL, "s1", "sig", "e1", null);
    manager.insert(signal);
    when(jobManager.findJobsByConfiguration(any(), any(), any())).thenReturn(Collections.emptyList());
    when(dbEntityManager.selectList(eq("selectSignalEventSubscriptionsByExecution"), eq("e1"))).thenReturn(Collections.emptyList());

    assertThat(manager.findSignalEventSubscriptionsByExecution("e1")).containsExactly(signal);
    manager.deleteEventSubscription(signal);

    assertThat(manager.findSignalEventSubscriptionsByExecution("e1")).isEmpty();
  }

  @Test
  public void shouldFlushEntityAfterDeleteAndFlush() {
    EventSubscriptionEntity message = subscription(EventType.MESSAGE, "m", "msg", "e1", null);
    when(jobManager.findJobsByConfiguration(any(), any(), any())).thenReturn(Collections.emptyList());

    manager.deleteAndFlushEventSubscription(message);

    verify(dbEntityManager).delete(message);
    verify(dbEntityManager).flushEntity(message);
  }

  // single and query lookups ----------------------------------------------------------------

  @Test
  public void shouldFindSubscriptionById() {
    EventSubscriptionEntity entity = subscription(EventType.MESSAGE, "m", "msg", "e1", null);
    when(dbEntityManager.selectOne("selectEventSubscription", "m")).thenReturn(entity);

    assertThat(manager.findEventSubscriptionById("m")).isSameAs(entity);
  }

  @Test
  public void shouldConfigureQueryBeforeCounting() {
    EventSubscriptionQueryImpl query = new EventSubscriptionQueryImpl();
    when(dbEntityManager.selectOne("selectEventSubscriptionCountByQueryCriteria", query)).thenReturn(7L);

    assertThat(manager.findEventSubscriptionCountByQueryCriteria(query)).isEqualTo(7L);
    verify(authorizationManager).configureEventSubscriptionQuery(query);
    verify(tenantManager).configureQuery(query);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldConfigureQueryBeforeListingWithPage() {
    EventSubscriptionQueryImpl query = new EventSubscriptionQueryImpl();
    Page page = new Page(0, 10);
    List result = Collections.singletonList(subscription(EventType.MESSAGE, "m", "msg", "e1", null));
    when(dbEntityManager.selectList("selectEventSubscriptionByQueryCriteria", query, page)).thenReturn(result);

    assertThat(manager.findEventSubscriptionsByQueryCriteria(query, page)).isSameAs(result);
    verify(authorizationManager).configureEventSubscriptionQuery(query);
    verify(tenantManager).configureQuery(query);
  }

  // signal lookups merge subscriptions created in this command -------------------------------

  @Test
  public void shouldMergeCreatedSignalSubscriptionsByEventName() {
    EventSubscriptionEntity persisted = subscription(EventType.SIGNAL, "p", "sig", "e1", null);
    EventSubscriptionEntity created = subscription(EventType.SIGNAL, "c", "sig", "e2", null);
    EventSubscriptionEntity other = subscription(EventType.SIGNAL, "o", "other", "e3", null);
    manager.insert(created);
    manager.insert(other);
    ListQueryParameterObject param = new ListQueryParameterObject();
    when(tenantManager.configureQuery((Object) "sig")).thenReturn(param);
    when(dbEntityManager.selectList("selectSignalEventSubscriptionsByEventName", param))
        .thenReturn(Collections.singletonList(persisted));

    assertThat(manager.findSignalEventSubscriptionsByEventName("sig")).containsExactlyInAnyOrder(persisted, created);
  }

  @Test
  public void shouldNotDuplicateSubscriptionVisibleInQueryAndCreatedInThisCommand() {
    EventSubscriptionEntity created = subscription(EventType.SIGNAL, "c", "sig", "e2", null);
    manager.insert(created);
    ListQueryParameterObject param = new ListQueryParameterObject();
    when(tenantManager.configureQuery((Object) "sig")).thenReturn(param);
    when(dbEntityManager.selectList("selectSignalEventSubscriptionsByEventName", param))
        .thenReturn(Collections.singletonList(created));

    assertThat(manager.findSignalEventSubscriptionsByEventName("sig")).containsExactly(created);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldMergeCreatedSignalSubscriptionsByNameAndTenant() {
    EventSubscriptionEntity persisted = subscription(EventType.SIGNAL, "p", "sig", "e1", "t1");
    EventSubscriptionEntity sameTenant = subscription(EventType.SIGNAL, "c1", "sig", "e2", "t1");
    EventSubscriptionEntity otherTenant = subscription(EventType.SIGNAL, "c2", "sig", "e3", "t2");
    EventSubscriptionEntity noTenant = subscription(EventType.SIGNAL, "c3", "sig", "e4", null);
    EventSubscriptionEntity otherName = subscription(EventType.SIGNAL, "c4", "other", "e5", "t1");
    for (EventSubscriptionEntity e : Arrays.asList(sameTenant, otherTenant, noTenant, otherName)) {
      manager.insert(e);
    }
    when(dbEntityManager.selectList(eq("selectSignalEventSubscriptionsByEventNameAndTenantId"), any(Map.class)))
        .thenReturn(Collections.singletonList(persisted));

    assertThat(manager.findSignalEventSubscriptionsByEventNameAndTenantId("sig", "t1"))
        .containsExactlyInAnyOrder(persisted, sameTenant);
    ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
    verify(dbEntityManager).selectList(eq("selectSignalEventSubscriptionsByEventNameAndTenantId"), params.capture());
    assertThat(params.getValue()).containsEntry("eventName", "sig").containsEntry("tenantId", "t1");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldMatchOnlyTenantlessCreatedSubscriptionsWhenTenantIdIsNull() {
    EventSubscriptionEntity withTenant = subscription(EventType.SIGNAL, "c1", "sig", "e2", "t1");
    EventSubscriptionEntity noTenant = subscription(EventType.SIGNAL, "c2", "sig", "e3", null);
    manager.insert(withTenant);
    manager.insert(noTenant);
    when(dbEntityManager.selectList(eq("selectSignalEventSubscriptionsByEventNameAndTenantId"), any(Map.class)))
        .thenReturn(Collections.emptyList());

    assertThat(manager.findSignalEventSubscriptionsByEventNameAndTenantId("sig", null)).containsExactly(noTenant);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldIncludeTenantlessCreatedSubscriptionsWhenAskedToIncludeWithoutTenantId() {
    EventSubscriptionEntity persisted = subscription(EventType.SIGNAL, "p", "sig", "e1", "t1");
    EventSubscriptionEntity sameTenant = subscription(EventType.SIGNAL, "c1", "sig", "e2", "t1");
    EventSubscriptionEntity noTenant = subscription(EventType.SIGNAL, "c2", "sig", "e3", null);
    EventSubscriptionEntity otherTenant = subscription(EventType.SIGNAL, "c3", "sig", "e4", "t2");
    EventSubscriptionEntity otherName = subscription(EventType.SIGNAL, "c4", "other", "e5", null);
    for (EventSubscriptionEntity e : Arrays.asList(sameTenant, noTenant, otherTenant, otherName)) {
      manager.insert(e);
    }
    when(dbEntityManager.selectList(eq("selectSignalEventSubscriptionsByEventNameAndTenantIdIncludeWithoutTenantId"), any(Map.class)))
        .thenReturn(Collections.singletonList(persisted));

    assertThat(manager.findSignalEventSubscriptionsByEventNameAndTenantIdIncludeWithoutTenantId("sig", "t1"))
        .containsExactlyInAnyOrder(persisted, sameTenant, noTenant);
  }

  @Test
  public void shouldMergeCreatedSignalSubscriptionsByExecution() {
    EventSubscriptionEntity persisted = subscription(EventType.SIGNAL, "p", "sig", "e1", null);
    EventSubscriptionEntity created = subscription(EventType.SIGNAL, "c", "sig2", "e1", null);
    EventSubscriptionEntity otherExecution = subscription(EventType.SIGNAL, "o", "sig", "e9", null);
    manager.insert(created);
    manager.insert(otherExecution);
    when(dbEntityManager.selectList("selectSignalEventSubscriptionsByExecution", "e1"))
        .thenReturn(Collections.singletonList(persisted));

    assertThat(manager.findSignalEventSubscriptionsByExecution("e1")).containsExactlyInAnyOrder(persisted, created);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldMergeCreatedSignalSubscriptionsByNameAndExecution() {
    EventSubscriptionEntity persisted = subscription(EventType.SIGNAL, "p", "sig", "e1", null);
    EventSubscriptionEntity created = subscription(EventType.SIGNAL, "c", "sig", "e1", null);
    EventSubscriptionEntity otherName = subscription(EventType.SIGNAL, "o1", "other", "e1", null);
    EventSubscriptionEntity otherExecution = subscription(EventType.SIGNAL, "o2", "sig", "e2", null);
    manager.insert(created);
    manager.insert(otherName);
    manager.insert(otherExecution);
    when(dbEntityManager.selectList(eq("selectSignalEventSubscriptionsByNameAndExecution"), any(Map.class)))
        .thenReturn(Collections.singletonList(persisted));

    assertThat(manager.findSignalEventSubscriptionsByNameAndExecution("sig", "e1"))
        .containsExactlyInAnyOrder(persisted, created);
  }

  // parameterised selects -------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  public void shouldPassParametersOfExecutionAndTypeLookup() {
    manager.findEventSubscriptionsByExecutionAndType("e1", "message", true);

    ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
    verify(dbEntityManager).selectList(eq("selectEventSubscriptionsByExecutionAndType"), params.capture());
    assertThat(params.getValue()).containsEntry("executionId", "e1").containsEntry("eventType", "message")
        .containsEntry("lockResult", true);
  }

  @Test
  public void shouldSelectByExecutionAndByProcessInstance() {
    manager.findEventSubscriptionsByExecution("e1");
    manager.findEventSubscriptionsByProcessInstanceId("pi1");

    verify(dbEntityManager).selectList("selectEventSubscriptionsByExecution", "e1");
    verify(dbEntityManager).selectList("selectEventSubscriptionsByProcessInstanceId", "pi1");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldPassParametersOfExecutionTypeAndActivityLookup() {
    manager.findEventSubscriptions("e1", "message", "act1");

    ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
    verify(dbEntityManager).selectList(eq("selectEventSubscriptionsByExecutionTypeAndActivity"), params.capture());
    assertThat(params.getValue()).containsEntry("executionId", "e1").containsEntry("eventType", "message")
        .containsEntry("activityId", "act1");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldPassParametersOfConfigurationLookups() {
    manager.findEventSubscriptionsByConfiguration("message", "cfg");
    manager.findStartEventSubscriptionsByConfigurationLike("cfg%");

    ArgumentCaptor<Map> exact = ArgumentCaptor.forClass(Map.class);
    verify(dbEntityManager).selectList(eq("selectEventSubscriptionsByConfiguration"), exact.capture());
    assertThat(exact.getValue()).containsEntry("eventType", "message").containsEntry("configuration", "cfg");
    ArgumentCaptor<Map> like = ArgumentCaptor.forClass(Map.class);
    verify(dbEntityManager).selectList(eq("selectStartEventSubscriptionsByConfigurationLike"), like.capture());
    assertThat(like.getValue()).containsEntry("configuration", "cfg%");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldPassParametersOfNameAndTenantLookup() {
    manager.findEventSubscriptionsByNameAndTenantId("message", "msg", "t1");

    ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
    verify(dbEntityManager).selectList(eq("selectEventSubscriptionsByNameAndTenantId"), params.capture());
    assertThat(params.getValue()).containsEntry("eventType", "message").containsEntry("eventName", "msg")
        .containsEntry("tenantId", "t1");
  }

  // cache-aware lookup ----------------------------------------------------------------------

  @Test
  public void shouldFilterCachedExecutionSubscriptionsByTypeAndName() {
    ExecutionEntity execution = mock(ExecutionEntity.class);
    EventSubscriptionEntity match = subscription(EventType.MESSAGE, "m1", "msg", "e1", null);
    EventSubscriptionEntity otherName = subscription(EventType.MESSAGE, "m2", "other", "e1", null);
    EventSubscriptionEntity otherType = subscription(EventType.SIGNAL, "s1", "msg", "e1", null);
    when(execution.getEventSubscriptions()).thenReturn(Arrays.asList(match, otherName, otherType));
    when(dbEntityManager.getCachedEntity(ExecutionEntity.class, "e1")).thenReturn(execution);

    List<EventSubscriptionEntity> result = manager.findEventSubscriptionsByNameAndExecution(EventType.MESSAGE.name(), "msg", "e1", false);

    assertThat(result).containsExactly(match);
    verify(dbEntityManager, never()).selectList(any(String.class), any(Object.class));
  }

  @Test
  public void shouldMatchSubscriptionsWithoutEventNameWhenNameIsNull() {
    ExecutionEntity execution = mock(ExecutionEntity.class);
    EventSubscriptionEntity unnamed = subscription(EventType.MESSAGE, "m1", null, "e1", null);
    EventSubscriptionEntity named = subscription(EventType.MESSAGE, "m2", "msg", "e1", null);
    when(execution.getEventSubscriptions()).thenReturn(Arrays.asList(unnamed, named));
    when(dbEntityManager.getCachedEntity(ExecutionEntity.class, "e1")).thenReturn(execution);

    assertThat(manager.findEventSubscriptionsByNameAndExecution(EventType.MESSAGE.name(), null, "e1", false))
        .containsExactly(unnamed);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldSelectFromDatabaseWhenExecutionIsNotCached() {
    when(dbEntityManager.getCachedEntity(ExecutionEntity.class, "e1")).thenReturn(null);

    manager.findEventSubscriptionsByNameAndExecution("message", "msg", "e1", false);

    ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
    verify(dbEntityManager).selectList(eq("selectEventSubscriptionsByNameAndExecution"), params.capture());
    assertThat(params.getValue()).containsEntry("eventType", "message").containsEntry("eventName", "msg")
        .containsEntry("executionId", "e1").containsEntry("lockResult", false);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldSelectFromDatabaseWhenResultMustBeLockedEvenIfCached() {
    when(dbEntityManager.getCachedEntity(ExecutionEntity.class, "e1")).thenReturn(mock(ExecutionEntity.class));

    manager.findEventSubscriptionsByNameAndExecution("message", "msg", "e1", true);

    ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
    verify(dbEntityManager).selectList(eq("selectEventSubscriptionsByNameAndExecution"), params.capture());
    assertThat(params.getValue()).containsEntry("lockResult", true);
  }

  @Test
  public void shouldRequireEventTypeWhenMatchingSubscription() {
    EventSubscriptionEntity entity = subscription(EventType.MESSAGE, "m1", "msg", "e1", null);

    assertThatThrownBy(() -> manager.matchesSubscription(entity, null, "msg"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("event type");
  }

  // message / conditional start events ------------------------------------------------------

  @Test
  public void shouldFindMessageStartEventSubscriptionsByNameThroughTenantAwareQuery() {
    ListQueryParameterObject param = new ListQueryParameterObject();
    EventSubscriptionEntity entity = subscription(EventType.MESSAGE, "m1", "msg", null, null);
    when(tenantManager.configureQuery((Object) "msg")).thenReturn(param);
    when(dbEntityManager.selectList("selectMessageStartEventSubscriptionByName", param))
        .thenReturn(Collections.singletonList(entity));

    assertThat(manager.findMessageStartEventSubscriptionByName("msg")).containsExactly(entity);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldFindMessageStartEventSubscriptionByNameAndTenant() {
    EventSubscriptionEntity entity = subscription(EventType.MESSAGE, "m1", "msg", null, "t1");
    when(dbEntityManager.selectOne(eq("selectMessageStartEventSubscriptionByNameAndTenantId"), any(Map.class))).thenReturn(entity);

    assertThat(manager.findMessageStartEventSubscriptionByNameAndTenantId("msg", "t1")).isSameAs(entity);
    ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
    verify(dbEntityManager).selectOne(eq("selectMessageStartEventSubscriptionByNameAndTenantId"), params.capture());
    assertThat(params.getValue()).containsEntry("messageName", "msg").containsEntry("tenantId", "t1");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldConfigureTenantQueryForConditionalStartSubscriptionsOfTenant() {
    manager.findConditionalStartEventSubscriptionByTenantId("t1");

    ArgumentCaptor<Map> params = ArgumentCaptor.forClass(Map.class);
    verify(tenantManager).configureQuery(params.capture());
    assertThat(params.getValue()).containsEntry("tenantId", "t1");
    verify(dbEntityManager).selectList(eq("selectConditionalStartEventSubscriptionByTenantId"), any(Map.class));
  }

  @Test
  public void shouldConfigureAuthorizationAndTenantForConditionalStartSubscriptions() {
    manager.findConditionalStartEventSubscription();

    ArgumentCaptor<ListQueryParameterObject> param = ArgumentCaptor.forClass(ListQueryParameterObject.class);
    verify(dbEntityManager).selectList(eq("selectConditionalStartEventSubscription"), param.capture());
    verify(authorizationManager).configureConditionalEventSubscriptionQuery(param.getValue());
    verify(tenantManager).configureQuery(param.getValue());
  }
}
