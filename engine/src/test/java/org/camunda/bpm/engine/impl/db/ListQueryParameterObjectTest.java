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

import java.util.Collections;
import java.util.List;
import org.camunda.bpm.engine.impl.QueryOrderingProperty;
import org.junit.Test;

public class ListQueryParameterObjectTest {

  @Test
  public void shouldDefaultToUnboundedPage() {
    ListQueryParameterObject query = new ListQueryParameterObject();

    assertThat(query.getFirstResult()).isZero();
    assertThat(query.getMaxResults()).isEqualTo(Integer.MAX_VALUE);
    assertThat(query.getFirstRow()).isEqualTo(1);
    assertThat(query.getLastRow()).isEqualTo(Integer.MAX_VALUE);
    assertThat(query.getParameter()).isNull();
    assertThat(query.getDatabaseType()).isNull();
    assertThat(query.getAuthCheck()).isNotNull();
    assertThat(query.getTenantCheck()).isNotNull();
    assertThat(query.getOrderingProperties()).isEmpty();
  }

  @Test
  public void shouldComputeRowBoundsFromConstructorArguments() {
    Object parameter = new Object();

    ListQueryParameterObject query = new ListQueryParameterObject(parameter, 10, 5);

    assertThat(query.getParameter()).isSameAs(parameter);
    assertThat(query.getFirstResult()).isEqualTo(10);
    assertThat(query.getMaxResults()).isEqualTo(5);
    assertThat(query.getFirstRow()).isEqualTo(11);
    assertThat(query.getLastRow()).isEqualTo(16);
  }

  @Test
  public void shouldRoundTripSetters() {
    ListQueryParameterObject query = new ListQueryParameterObject();
    AuthorizationCheck authCheck = new AuthorizationCheck();
    TenantCheck tenantCheck = new TenantCheck();
    List<QueryOrderingProperty> ordering = Collections.singletonList(new QueryOrderingProperty());

    query.setFirstResult(3);
    query.setMaxResults(7);
    query.setParameter("p");
    query.setDatabaseType("h2");
    query.setAuthCheck(authCheck);
    query.setTenantCheck(tenantCheck);
    query.setOrderingProperties(ordering);

    assertThat(query.getFirstResult()).isEqualTo(3);
    assertThat(query.getMaxResults()).isEqualTo(7);
    assertThat(query.getParameter()).isEqualTo("p");
    assertThat(query.getDatabaseType()).isEqualTo("h2");
    assertThat(query.getAuthCheck()).isSameAs(authCheck);
    assertThat(query.getTenantCheck()).isSameAs(tenantCheck);
    assertThat(query.getOrderingProperties()).isSameAs(ordering);
  }
}
