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
package org.camunda.bpm.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.camunda.bpm.client.exception.ExternalTaskClientException;
import org.camunda.bpm.client.task.OrderingConfig.Direction;
import org.camunda.bpm.client.task.OrderingConfig.SortingField;
import org.junit.Test;

public class OrderingConfigTest {

  @Test
  public void shouldConvertConfiguredFieldToSortingDto() {
    OrderingConfig config = OrderingConfig.empty();
    config.configureField(SortingField.CREATE_TIME);
    config.configureDirectionOnLastField(Direction.DESC);

    config.validateOrderingProperties();

    assertThat(config.toSortingDtos()).isNotEmpty();
    assertThat(config.toSortingDtos().get(config.toSortingDtos().size() - 1).getSortBy()).isEqualTo("createTime");
    assertThat(config.toSortingDtos().get(config.toSortingDtos().size() - 1).getSortOrder()).isEqualTo("desc");
  }

  @Test
  public void shouldReturnNoSortingForEmptyConfig() {
    assertThat(OrderingConfig.empty().toSortingDtos()).isEmpty();
    OrderingConfig.empty().validateOrderingProperties();
  }

  @Test
  public void shouldRejectDirectionWithoutField() {
    OrderingConfig config = OrderingConfig.empty();

    assertThatThrownBy(() -> config.configureDirectionOnLastField(Direction.ASC))
        .isInstanceOf(ExternalTaskClientException.class);
  }

  @Test
  public void shouldRejectFieldWithoutDirection() {
    OrderingConfig config = OrderingConfig.empty();
    config.configureField(SortingField.CREATE_TIME);

    assertThatThrownBy(config::validateOrderingProperties).isInstanceOf(ExternalTaskClientException.class);
  }

  @Test
  public void shouldRejectSecondDirectionOnSameField() {
    OrderingConfig config = OrderingConfig.empty();
    config.configureField(SortingField.CREATE_TIME);
    config.configureDirectionOnLastField(Direction.ASC);

    assertThatThrownBy(() -> config.configureDirectionOnLastField(Direction.DESC))
        .isInstanceOf(ExternalTaskClientException.class);
  }

  @Test
  public void shouldExposeEnumNames() {
    assertThat(SortingField.CREATE_TIME.getName()).isEqualTo("createTime");
    assertThat(Direction.ASC.asString()).isEqualTo("asc");
    assertThat(Direction.DESC.asString()).isEqualTo("desc");
  }

  @Test
  public void shouldBuildSortingDto() {
    SortingDto dto = SortingDto.of("createTime", "asc");

    assertThat(dto.getSortBy()).isEqualTo("createTime");
    assertThat(dto.getSortOrder()).isEqualTo("asc");
    dto.setSortBy("x");
    dto.setSortOrder("desc");
    assertThat(dto.getSortBy()).isEqualTo("x");
    assertThat(dto.getSortOrder()).isEqualTo("desc");
  }

}
