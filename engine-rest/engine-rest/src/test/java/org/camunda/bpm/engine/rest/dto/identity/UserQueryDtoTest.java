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
package org.camunda.bpm.engine.rest.dto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.UserQuery;
import org.camunda.bpm.engine.rest.dto.SortingDto;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

/**
 * Unit coverage for {@link UserQueryDto}: each REST parameter must trigger exactly its fluent call
 * on the {@link UserQuery}, an absent parameter must trigger nothing, and sorting is validated.
 */
public class UserQueryDtoTest {

  private UserQuery query;
  private ProcessEngine engine;

  @Before
  public void setUp() {
    query = mock(UserQuery.class);
    engine = mock(ProcessEngine.class);
    IdentityService identityService = mock(IdentityService.class);
    when(engine.getIdentityService()).thenReturn(identityService);
    when(identityService.createUserQuery()).thenReturn(query);
  }

  private UserQueryDto fromParameters(String... keyValue) {
    MultivaluedMap<String, String> parameters = new MultivaluedHashMap<>();
    for (int i = 0; i < keyValue.length; i += 2) {
      parameters.add(keyValue[i], keyValue[i + 1]);
    }
    return new UserQueryDto(new ObjectMapper(), parameters);
  }

  private void assertBadRequest(Runnable call, String message) {
    try {
      call.run();
    } catch (InvalidRequestException e) {
      assertThat(e.getStatus()).isEqualTo(Status.BAD_REQUEST);
      assertThat(e.getMessage()).contains(message);
      return;
    }
    throw new AssertionError("expected InvalidRequestException: " + message);
  }

  @Test
  public void shouldApplyNoFilterWithoutParameters() {
    UserQuery result = new UserQueryDto().toQuery(engine);

    assertThat(result).isSameAs(query);
    verifyNoMoreInteractions(query);
  }

  @Test
  public void shouldReplayEveryParameterOntoTheQuery() {
    UserQueryDto dto = fromParameters(
        "id", "u1",
        "idIn", "u1,u2",
        "firstName", "Ann",
        "firstNameLike", "A%",
        "lastName", "Lee",
        "lastNameLike", "L%",
        "email", "a@x.io",
        "emailLike", "%@x.io",
        "memberOfGroup", "g1",
        "potentialStarter", "proc1",
        "memberOfTenant", "t1");

    dto.toQuery(engine);

    verify(query).userId("u1");
    verify(query).userIdIn("u1", "u2");
    verify(query).userFirstName("Ann");
    verify(query).userFirstNameLike("A%");
    verify(query).userLastName("Lee");
    verify(query).userLastNameLike("L%");
    verify(query).userEmail("a@x.io");
    verify(query).userEmailLike("%@x.io");
    verify(query).memberOfGroup("g1");
    verify(query).potentialStarter("proc1");
    verify(query).memberOfTenant("t1");
    verifyNoMoreInteractions(query);
  }

  @Test
  public void shouldApplyOnlyTheParametersThatWereGiven() {
    fromParameters("lastName", "Lee").toQuery(engine);

    verify(query).userLastName("Lee");
    verifyNoMoreInteractions(query);
  }

  @Test
  public void shouldTranslateEachSortByValueToItsOrdering() {
    assertSortedBy("userId", q -> q.orderByUserId());
    assertSortedBy("firstName", q -> q.orderByUserFirstName());
    assertSortedBy("lastName", q -> q.orderByUserLastName());
    assertSortedBy("email", q -> q.orderByUserEmail());
  }

  private void assertSortedBy(String sortBy, java.util.function.Consumer<UserQuery> expected) {
    UserQuery fresh = mock(UserQuery.class);
    when(engine.getIdentityService().createUserQuery()).thenReturn(fresh);

    fromParameters("sortBy", sortBy, "sortOrder", "desc").toQuery(engine);

    InOrder order = inOrder(fresh);
    expected.accept(order.verify(fresh));
    order.verify(fresh).desc();
  }

  @Test
  public void shouldSortAscending() {
    fromParameters("sortBy", "userId", "sortOrder", "asc").toQuery(engine);

    verify(query).orderByUserId();
    verify(query).asc();
  }

  @Test
  public void shouldApplyTheSortingListEntries() {
    UserQueryDto dto = new UserQueryDto();
    SortingDto byEmail = new SortingDto();
    byEmail.setSortBy("email");
    byEmail.setSortOrder("desc");
    dto.setSorting(Collections.singletonList(byEmail));

    dto.toQuery(engine);

    InOrder order = inOrder(query);
    order.verify(query).orderByUserEmail();
    order.verify(query).desc();
  }

  @Test
  public void shouldRefuseAnUnknownSortBy() {
    assertBadRequest(() -> new UserQueryDto().setSortBy("nickname"), "sortBy parameter has invalid value: nickname");
  }

  @Test
  public void shouldRefuseSortByWithoutSortOrder() {
    UserQueryDto dto = new UserQueryDto();
    dto.setSortBy("userId");

    assertBadRequest(() -> dto.toQuery(engine), "Only a single sorting parameter specified");
  }

  @Test
  public void shouldRefuseSortOrderWithoutSortBy() {
    UserQueryDto dto = new UserQueryDto();
    dto.setSortOrder("asc");

    assertBadRequest(() -> dto.toQuery(engine), "Only a single sorting parameter specified");
  }
}
