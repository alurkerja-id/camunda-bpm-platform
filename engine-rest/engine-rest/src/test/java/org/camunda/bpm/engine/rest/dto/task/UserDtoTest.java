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
package org.camunda.bpm.engine.rest.dto.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Covers {@link UserDto}.
 *
 * <p>The display name is derived in the constructor and the rule has three outcomes: the id when
 * there is no name at all, both names joined when the last name is there, and the first name alone
 * otherwise. equals then compares three fields, each behind its own null guard.
 */
public class UserDtoTest {

  // the derived display name --------------------------------------------------------------------

  @Test
  public void shouldJoinBothNames() {
    assertThat(new UserDto("id", "Ana", "Putri").getDisplayName()).isEqualTo("Ana Putri");
  }

  @Test
  public void shouldUseTheFirstNameAloneWhenThereIsNoLastName() {
    assertThat(new UserDto("id", "Ana", null).getDisplayName()).isEqualTo("Ana");
  }

  /** A last name without a first name still goes through the join, so "null" appears literally. */
  @Test
  public void shouldJoinEvenWhenOnlyTheLastNameIsThere() {
    assertThat(new UserDto("id", null, "Putri").getDisplayName()).isEqualTo("null Putri");
  }

  @Test
  public void shouldFallBackToTheIdWhenNeitherNameIsThere() {
    assertThat(new UserDto("id", null, null).getDisplayName()).isEqualTo("id");
    assertThat(new UserDto(null, null, null).getDisplayName()).isNull();
  }

  // the plain fields ---------------------------------------------------------------------------

  @Test
  public void shouldKeepWhatItWasBuiltWith() {
    UserDto user = new UserDto("id", "Ana", "Putri");

    assertThat(user.getId()).isEqualTo("id");
    assertThat(user.getFirstName()).isEqualTo("Ana");
    assertThat(user.getLastName()).isEqualTo("Putri");
  }

  // equality ------------------------------------------------------------------------------------

  @Test
  public void shouldEqualItselfAndAnIdenticalUser() {
    UserDto user = new UserDto("id", "Ana", "Putri");

    assertThat(user.equals(user)).isTrue();
    assertThat(user).isEqualTo(new UserDto("id", "Ana", "Putri"));
  }

  @Test
  public void shouldNotEqualNullOrAnotherType() {
    UserDto user = new UserDto("id", "Ana", "Putri");

    assertThat(user.equals(null)).isFalse();
    assertThat(user.equals("id")).isFalse();
  }

  @Test
  public void shouldDifferOnAnyOfTheThreeFields() {
    UserDto user = new UserDto("id", "Ana", "Putri");

    assertThat(user.equals(new UserDto("other", "Ana", "Putri"))).isFalse();
    assertThat(user.equals(new UserDto("id", "Budi", "Putri"))).isFalse();
    assertThat(user.equals(new UserDto("id", "Ana", "Sari"))).isFalse();
  }

  /** Each field is guarded for null on both sides, so null has to be driven both ways. */
  @Test
  public void shouldCompareFieldsThatAreNull() {
    UserDto full = new UserDto("id", "Ana", "Putri");
    UserDto noNames = new UserDto("id", null, null);
    UserDto noId = new UserDto(null, "Ana", "Putri");

    assertThat(noNames).isEqualTo(new UserDto("id", null, null));
    assertThat(noNames.equals(full)).isFalse();
    assertThat(full.equals(noNames)).isFalse();
    assertThat(noId.equals(full)).isFalse();
    assertThat(full.equals(noId)).isFalse();
  }

  /** The display name is deliberately left out of equals - two users can differ only in it. */
  @Test
  public void shouldIgnoreTheDisplayNameWhenComparing() {
    UserDto fromNames = new UserDto("id", null, null);

    assertThat(fromNames.getDisplayName()).isEqualTo("id");
    assertThat(fromNames).isEqualTo(new UserDto("id", null, null));
  }

  @Test
  public void shouldHashEqualUsersAlike() {
    assertThat(new UserDto("id", "Ana", "Putri").hashCode())
        .isEqualTo(new UserDto("id", "Ana", "Putri").hashCode());
    assertThat(new UserDto(null, null, null).hashCode()).isZero();
  }
}
