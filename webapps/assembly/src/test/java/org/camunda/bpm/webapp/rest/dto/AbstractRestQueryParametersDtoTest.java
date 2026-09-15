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
package org.camunda.bpm.webapp.rest.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.camunda.bpm.engine.rest.dto.CamundaQueryParam;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.variable.value.NumberValue;
import org.junit.Test;

/**
 * Covers {@link AbstractRestQueryParametersDto} through a small concrete subclass.
 *
 * <p>Three rules carry the branches: sortBy and sortOrder must be given together or not at all,
 * getOrderBy fills in "asc" when only sortBy is there, and resolveVariableValue turns numbers into
 * a typed value and tries to read a string as a date before giving up on it.
 */
public class AbstractRestQueryParametersDtoTest {

  /** Accepts only "name" as a sort field, and maps it to a column expression. */
  public static class TestQueryDto extends AbstractRestQueryParametersDto<Object> {

    private static final long serialVersionUID = 1L;

    protected String name;

    public TestQueryDto() {
    }

    public TestQueryDto(ObjectMapper objectMapper, MultivaluedMap<String, String> parameters) {
      super(objectMapper, parameters);
    }

    @CamundaQueryParam("name")
    public void setName(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    @Override
    protected String getOrderByValue(String sortBy) {
      return "RES.NAME_";
    }

    @Override
    protected boolean isValidSortByValue(String value) {
      return "name".equals(value);
    }
  }

  protected MultivaluedMap<String, String> parameters(String... keyThenValue) {
    MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
    for (int i = 0; i < keyThenValue.length; i += 2) {
      map.putSingle(keyThenValue[i], keyThenValue[i + 1]);
    }
    return map;
  }

  // sorting parameters ---------------------------------------------------------------------------

  @Test
  public void shouldAcceptAValidSortField() {
    TestQueryDto dto = new TestQueryDto();

    dto.setSortBy("name");

    assertThat(dto.getOrderBy()).isEqualTo("RES.NAME_ asc");
  }

  @Test
  public void shouldRefuseAnUnknownSortField() {
    TestQueryDto dto = new TestQueryDto();

    assertThatThrownBy(() -> dto.setSortBy("unknown"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("sortBy parameter has invalid value");
  }

  @Test
  public void shouldAcceptOnlyAscOrDescAsSortOrder() {
    TestQueryDto dto = new TestQueryDto();

    dto.setSortOrder("asc");
    dto.setSortOrder("desc");

    assertThatThrownBy(() -> dto.setSortOrder("sideways"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("sortOrder parameter has invalid value");
    assertThatThrownBy(() -> dto.setSortOrder(null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  public void shouldRequireSortByAndSortOrderTogether() {
    assertThat(new TestQueryDto(null, parameters("sortBy", "name", "sortOrder", "desc")))
        .isNotNull();
    assertThat(new TestQueryDto(null, parameters())).isNotNull();

    assertThatThrownBy(() -> new TestQueryDto(null, parameters("sortBy", "name")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Only a single sorting parameter specified");
    assertThatThrownBy(() -> new TestQueryDto(null, parameters("sortOrder", "asc")))
        .isInstanceOf(InvalidRequestException.class);
  }

  // the order by clause ----------------------------------------------------------------------------

  @Test
  public void shouldFallBackToTheDefaultOrderWithoutASortField() {
    assertThat(new TestQueryDto().getOrderBy()).isEqualTo("RES.ID_ asc");
    assertThat(new TestQueryDto().getInternalOrderBy()).isEqualTo("RES.ID_ asc");
  }

  @Test
  public void shouldDefaultToAscendingWhenOnlyTheFieldIsKnown() {
    TestQueryDto dto = new TestQueryDto();
    dto.setSortBy("name");

    assertThat(dto.getOrderBy()).isEqualTo("RES.NAME_ asc");
  }

  @Test
  public void shouldUseTheGivenDirection() {
    TestQueryDto dto = new TestQueryDto(null, parameters("sortBy", "name", "sortOrder", "desc"));

    assertThat(dto.getOrderBy()).isEqualTo("RES.NAME_ desc");
  }

  // populating from query parameters ------------------------------------------------------------------

  @Test
  public void shouldSetAnAnnotatedField() {
    TestQueryDto dto = new TestQueryDto(null, parameters("name", "ana"));

    assertThat(dto.getName()).isEqualTo("ana");
  }

  @Test
  public void shouldIgnoreAParameterNoSetterClaims() {
    TestQueryDto dto = new TestQueryDto(null, parameters("unknownParameter", "value"));

    assertThat(dto.getName()).isNull();
  }

  @Test
  public void shouldWrapAnErrorFromTheSetterAsABadRequest() {
    assertThatThrownBy(() -> new TestQueryDto(null, parameters("sortBy", "unknown")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot set query parameter 'sortBy'");
  }

  // variable values ---------------------------------------------------------------------------------

  @Test
  public void shouldTurnANumberIntoATypedValue() {
    TestQueryDto dto = new TestQueryDto();

    Object resolved = dto.resolveVariableValue(42);

    assertThat(resolved).isInstanceOf(NumberValue.class);
    assertThat(((NumberValue) resolved).getValue()).isEqualTo(42);
  }

  @Test
  public void shouldReadAStringAsADateWhenAMapperIsThere() {
    TestQueryDto dto = new TestQueryDto();
    dto.setObjectMapper(new ObjectMapper());

    Object resolved = dto.resolveVariableValue("2026-08-29T00:00:00.000+0000");

    assertThat(resolved).isInstanceOf(Date.class);
  }

  @Test
  public void shouldLeaveAStringAloneWhenItIsNotADate() {
    TestQueryDto dto = new TestQueryDto();
    dto.setObjectMapper(new ObjectMapper());

    assertThat(dto.resolveVariableValue("plain text")).isEqualTo("plain text");
  }

  @Test
  public void shouldLeaveTheValueAloneWithoutAMapperOrWhenNull() {
    TestQueryDto dto = new TestQueryDto();

    assertThat(dto.resolveVariableValue("2026-08-29T00:00:00.000+0000"))
        .isEqualTo("2026-08-29T00:00:00.000+0000");
    assertThat(dto.resolveVariableValue(null)).isNull();
  }
}
