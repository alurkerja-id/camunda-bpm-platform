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
package org.camunda.bpm.engine.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Verifies the merge semantics of {@link TaskQueryImpl#extend}: a criterion set on the
 * extending query wins, otherwise the base query's criterion is kept, otherwise it stays unset.
 */
public class TaskQueryImplExtendTest {

  private static final Date BASE_DATE = new Date(1_000_000L);
  private static final Date EXT_DATE = new Date(2_000_000L);

  /** A single criterion: how to set it on a query and how to read it back. */
  static final class Criterion {
    final String name;
    final BiConsumer<TaskQueryImpl, Object> setter;
    final Function<TaskQueryImpl, Object> getter;
    final Object baseValue;
    final Object extValue;

    Criterion(String name, BiConsumer<TaskQueryImpl, Object> setter, Function<TaskQueryImpl, Object> getter,
              Object baseValue, Object extValue) {
      this.name = name;
      this.setter = setter;
      this.getter = getter;
      this.baseValue = baseValue;
      this.extValue = extValue;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static Criterion str(String name, BiConsumer<TaskQueryImpl, String> setter, Function<TaskQueryImpl, Object> getter) {
    return new Criterion(name, (q, v) -> setter.accept(q, (String) v), getter, "base-" + name, "ext-" + name);
  }

  private static Criterion date(String name, BiConsumer<TaskQueryImpl, Date> setter, Function<TaskQueryImpl, Object> getter) {
    return new Criterion(name, (q, v) -> setter.accept(q, (Date) v), getter, BASE_DATE, EXT_DATE);
  }

  private static Criterion integer(String name, BiConsumer<TaskQueryImpl, Integer> setter, Function<TaskQueryImpl, Object> getter) {
    return new Criterion(name, (q, v) -> setter.accept(q, (Integer) v), getter, 1, 2);
  }

  private static Stream<Criterion> criteria() {
    return Stream.of(
        str("name", TaskQueryImpl::taskName, TaskQueryImpl::getName),
        str("nameLike", TaskQueryImpl::taskNameLike, TaskQueryImpl::getNameLike),
        str("nameNotEqual", TaskQueryImpl::taskNameNotEqual, TaskQueryImpl::getNameNotEqual),
        str("nameNotLike", TaskQueryImpl::taskNameNotLike, TaskQueryImpl::getNameNotLike),
        str("assignee", TaskQueryImpl::taskAssignee, TaskQueryImpl::getAssignee),
        str("assigneeLike", TaskQueryImpl::taskAssigneeLike, TaskQueryImpl::getAssigneeLike),
        str("involvedUser", TaskQueryImpl::taskInvolvedUser, TaskQueryImpl::getInvolvedUser),
        str("owner", TaskQueryImpl::taskOwner, TaskQueryImpl::getOwner),
        str("candidateUser", TaskQueryImpl::taskCandidateUser, TaskQueryImpl::getCandidateUser),
        str("candidateGroup", TaskQueryImpl::taskCandidateGroup, TaskQueryImpl::getCandidateGroup),
        str("candidateGroupLike", TaskQueryImpl::taskCandidateGroupLike, TaskQueryImpl::getCandidateGroupLike),
        str("processInstanceId", TaskQueryImpl::processInstanceId, TaskQueryImpl::getProcessInstanceId),
        str("executionId", TaskQueryImpl::executionId, TaskQueryImpl::getExecutionId),
        str("taskId", TaskQueryImpl::taskId, TaskQueryImpl::getTaskId),
        str("description", TaskQueryImpl::taskDescription, TaskQueryImpl::getDescription),
        str("descriptionLike", TaskQueryImpl::taskDescriptionLike, TaskQueryImpl::getDescriptionLike),
        str("definitionKey", TaskQueryImpl::taskDefinitionKey, TaskQueryImpl::getKey),
        str("definitionKeyLike", TaskQueryImpl::taskDefinitionKeyLike, TaskQueryImpl::getKeyLike),
        str("parentTaskId", TaskQueryImpl::taskParentTaskId, TaskQueryImpl::getParentTaskId),
        str("processDefinitionKey", TaskQueryImpl::processDefinitionKey, TaskQueryImpl::getProcessDefinitionKey),
        str("processDefinitionId", TaskQueryImpl::processDefinitionId, TaskQueryImpl::getProcessDefinitionId),
        str("processDefinitionName", TaskQueryImpl::processDefinitionName, TaskQueryImpl::getProcessDefinitionName),
        str("processDefinitionNameLike", TaskQueryImpl::processDefinitionNameLike, TaskQueryImpl::getProcessDefinitionNameLike),
        str("businessKey", TaskQueryImpl::processInstanceBusinessKey, TaskQueryImpl::getProcessInstanceBusinessKey),
        str("businessKeyLike", TaskQueryImpl::processInstanceBusinessKeyLike, TaskQueryImpl::getProcessInstanceBusinessKeyLike),
        str("caseInstanceId", TaskQueryImpl::caseInstanceId, TaskQueryImpl::getCaseInstanceId),
        str("caseInstanceBusinessKey", TaskQueryImpl::caseInstanceBusinessKey, TaskQueryImpl::getCaseInstanceBusinessKey),
        str("caseExecutionId", TaskQueryImpl::caseExecutionId, TaskQueryImpl::getCaseExecutionId),
        str("caseDefinitionId", TaskQueryImpl::caseDefinitionId, TaskQueryImpl::getCaseDefinitionId),
        str("caseDefinitionKey", TaskQueryImpl::caseDefinitionKey, TaskQueryImpl::getCaseDefinitionKey),
        str("caseDefinitionName", TaskQueryImpl::caseDefinitionName, TaskQueryImpl::getCaseDefinitionName),
        str("caseDefinitionNameLike", TaskQueryImpl::caseDefinitionNameLike, TaskQueryImpl::getCaseDefinitionNameLike),
        integer("priority", TaskQueryImpl::taskPriority, TaskQueryImpl::getPriority),
        integer("minPriority", TaskQueryImpl::taskMinPriority, TaskQueryImpl::getMinPriority),
        integer("maxPriority", TaskQueryImpl::taskMaxPriority, TaskQueryImpl::getMaxPriority),
        date("createTime", TaskQueryImpl::taskCreatedOn, TaskQueryImpl::getCreateTime),
        date("createTimeBefore", TaskQueryImpl::taskCreatedBefore, TaskQueryImpl::getCreateTimeBefore),
        date("createTimeAfter", TaskQueryImpl::taskCreatedAfter, TaskQueryImpl::getCreateTimeAfter),
        date("updatedAfter", TaskQueryImpl::taskUpdatedAfter, TaskQueryImpl::getUpdatedAfter),
        date("dueDate", TaskQueryImpl::dueDate, TaskQueryImpl::getDueDate),
        date("dueBefore", TaskQueryImpl::dueBefore, TaskQueryImpl::getDueBefore),
        date("dueAfter", TaskQueryImpl::dueAfter, TaskQueryImpl::getDueAfter),
        date("followUpDate", TaskQueryImpl::followUpDate, TaskQueryImpl::getFollowUpDate),
        date("followUpBefore", TaskQueryImpl::followUpBefore, TaskQueryImpl::getFollowUpBefore),
        date("followUpAfter", TaskQueryImpl::followUpAfter, TaskQueryImpl::getFollowUpAfter));
  }

  @Test
  public void extendingCriterionWinsOverBase() {
    criteria().forEach(c -> {
    TaskQueryImpl base = new TaskQueryImpl();
    TaskQueryImpl extending = new TaskQueryImpl();
    c.setter.accept(base, c.baseValue);
    c.setter.accept(extending, c.extValue);

    TaskQueryImpl result = (TaskQueryImpl) base.extend(extending);

    assertThat(c.getter.apply(result)).as(c.name).isEqualTo(c.extValue);    });
  }

  @Test
  public void baseCriterionKeptWhenExtendingHasNone() {
    criteria().forEach(c -> {
    TaskQueryImpl base = new TaskQueryImpl();
    c.setter.accept(base, c.baseValue);

    TaskQueryImpl result = (TaskQueryImpl) base.extend(new TaskQueryImpl());

    assertThat(c.getter.apply(result)).as(c.name).isEqualTo(c.baseValue);    });
  }

  @Test
  public void extendingCriterionAppliedWhenBaseHasNone() {
    criteria().forEach(c -> {
    TaskQueryImpl extending = new TaskQueryImpl();
    c.setter.accept(extending, c.extValue);

    TaskQueryImpl result = (TaskQueryImpl) new TaskQueryImpl().extend(extending);

    assertThat(c.getter.apply(result)).as(c.name).isEqualTo(c.extValue);    });
  }

  @Test
  public void criterionStaysUnsetWhenNeitherQueryHasIt() {
    criteria().forEach(c -> {
    TaskQueryImpl result = (TaskQueryImpl) new TaskQueryImpl().extend(new TaskQueryImpl());

    assertThat(c.getter.apply(result)).as(c.name).isNull();    });
  }

  @Test
  public void arrayCriteriaFollowSamePrecedence() {
    TaskQueryImpl base = new TaskQueryImpl();
    base.taskAssigneeIn("b1", "b2");
    base.taskAssigneeNotIn("nb1");
    base.taskDefinitionKeyIn("bk");
    base.taskDefinitionKeyNotIn("bnk");
    base.processDefinitionKeyIn("bpk");
    base.tenantIdIn("bt");

    TaskQueryImpl keptFromBase = (TaskQueryImpl) base.extend(new TaskQueryImpl());
    assertThat(keptFromBase.getAssigneeIn()).containsExactlyInAnyOrder("b1", "b2");
    assertThat(keptFromBase.getAssigneeNotIn()).containsExactlyInAnyOrder("nb1");
    assertThat(keptFromBase.getKeys()).containsExactlyInAnyOrder("bk");
    assertThat(keptFromBase.getKeyNotIn()).containsExactlyInAnyOrder("bnk");
    assertThat(keptFromBase.getProcessDefinitionKeys()).containsExactlyInAnyOrder("bpk");
    assertThat(keptFromBase.getTenantIds()).containsExactlyInAnyOrder("bt");

    TaskQueryImpl extending = new TaskQueryImpl();
    extending.taskAssigneeIn("e1");
    extending.taskAssigneeNotIn("ne1", "ne2");
    extending.taskDefinitionKeyIn("ek");
    extending.taskDefinitionKeyNotIn("enk");
    extending.processDefinitionKeyIn("epk");
    extending.tenantIdIn("et");

    TaskQueryImpl overridden = (TaskQueryImpl) base.extend(extending);
    assertThat(overridden.getAssigneeIn()).containsExactlyInAnyOrder("e1");
    assertThat(overridden.getAssigneeNotIn()).containsExactlyInAnyOrder("ne1", "ne2");
    assertThat(overridden.getKeys()).containsExactlyInAnyOrder("ek");
    assertThat(overridden.getKeyNotIn()).containsExactlyInAnyOrder("enk");
    assertThat(overridden.getProcessDefinitionKeys()).containsExactlyInAnyOrder("epk");
    assertThat(overridden.getTenantIds()).containsExactlyInAnyOrder("et");

    TaskQueryImpl fromExtendingOnly = (TaskQueryImpl) new TaskQueryImpl().extend(extending);
    assertThat(fromExtendingOnly.getAssigneeIn()).containsExactlyInAnyOrder("e1");
  }

  @Test
  public void booleanFlagsAreOrCombined() {
    TaskQueryImpl base = new TaskQueryImpl();
    base.taskAssigned();
    base.withCandidateGroups();
    base.withoutCandidateUsers();
    base.excludeSubtasks();
    base.withoutDueDate();

    TaskQueryImpl extending = new TaskQueryImpl();
    extending.taskUnassigned();
    extending.withCandidateUsers();
    extending.withoutCandidateGroups();
    extending.initializeFormKeys();
    extending.taskNameCaseInsensitive();
    extending.withoutTenantId();

    TaskQueryImpl result = (TaskQueryImpl) base.extend(extending);

    assertThat(result.isAssigned()).isTrue();
    assertThat(result.isUnassigned()).isTrue();
    assertThat(result.isWithCandidateGroups()).isTrue();
    assertThat(result.isWithCandidateUsers()).isTrue();
    assertThat(result.isWithoutCandidateGroups()).isTrue();
    assertThat(result.isWithoutCandidateUsers()).isTrue();
    assertThat(result.isExcludeSubtasks()).isTrue();
    assertThat(result.isWithoutDueDate()).isTrue();
    assertThat(result.isInitializeFormKeys()).isTrue();
    assertThat(result.isTaskNameCaseInsensitive()).isTrue();
    assertThat(result.isWithoutTenantId()).isTrue();

    TaskQueryImpl untouched = (TaskQueryImpl) new TaskQueryImpl().extend(new TaskQueryImpl());
    assertThat(untouched.isAssigned()).isFalse();
    assertThat(untouched.isExcludeSubtasks()).isFalse();
    assertThat(untouched.isWithoutTenantId()).isFalse();
  }

  @Test
  public void suspensionStateOfExtendingQueryWins() {
    TaskQueryImpl active = new TaskQueryImpl();
    active.active();
    TaskQueryImpl suspended = new TaskQueryImpl();
    suspended.suspended();
    TaskQueryImpl none = new TaskQueryImpl();

    assertThat(((TaskQueryImpl) suspended.extend(active)).getSuspensionState()).isEqualTo(org.camunda.bpm.engine.impl.persistence.entity.SuspensionState.ACTIVE);
    assertThat(((TaskQueryImpl) active.extend(suspended)).getSuspensionState()).isEqualTo(org.camunda.bpm.engine.impl.persistence.entity.SuspensionState.SUSPENDED);
    assertThat(((TaskQueryImpl) active.extend(none)).getSuspensionState()).isEqualTo(org.camunda.bpm.engine.impl.persistence.entity.SuspensionState.ACTIVE);
    assertThat(((TaskQueryImpl) suspended.extend(none)).getSuspensionState()).isEqualTo(org.camunda.bpm.engine.impl.persistence.entity.SuspensionState.SUSPENDED);
    assertThat(((TaskQueryImpl) none.extend(none)).getSuspensionState()).isNull();
  }
}
