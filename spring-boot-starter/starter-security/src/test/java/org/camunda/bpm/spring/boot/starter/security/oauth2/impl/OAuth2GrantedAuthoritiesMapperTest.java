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
package org.camunda.bpm.spring.boot.starter.security.oauth2.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.spring.boot.starter.security.oauth2.OAuth2Properties;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

/**
 * Covers {@link OAuth2GrantedAuthoritiesMapper}, which turns the group claim of an OAuth2 token
 * into Camunda authorities.
 *
 * <p>The claim can arrive in three shapes and each is handled differently: a collection is taken
 * as-is, a string is split on the configured delimiter, and anything else is refused with a log
 * line and no authority. Authorities that are not OAuth2 ones are passed over entirely.
 */
public class OAuth2GrantedAuthoritiesMapperTest {

  protected OAuth2Properties properties;
  protected OAuth2GrantedAuthoritiesMapper mapper;

  @Before
  public void setUp() {
    properties = new OAuth2Properties();
    properties.getIdentityProvider().setGroupNameAttribute("groups");
    mapper = new OAuth2GrantedAuthoritiesMapper(properties);
  }

  protected OAuth2UserAuthority authorityWith(Object groupClaim) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("sub", "ana");
    if (groupClaim != null) {
      attributes.put("groups", groupClaim);
    }
    return new OAuth2UserAuthority(attributes);
  }

  @Test
  public void shouldMapAGroupClaimThatIsACollection() {
    Collection<? extends GrantedAuthority> mapped =
        mapper.mapAuthorities(Collections.singletonList(authorityWith(Arrays.asList("admin",
            "sales"))));

    assertThat(mapped).extracting("authority").containsExactlyInAnyOrder("admin", "sales");
  }

  @Test
  public void shouldSplitAGroupClaimThatIsAString() {
    Collection<? extends GrantedAuthority> mapped =
        mapper.mapAuthorities(Collections.singletonList(authorityWith("admin,sales")));

    assertThat(mapped).extracting("authority").containsExactlyInAnyOrder("admin", "sales");
  }

  @Test
  public void shouldSplitOnTheConfiguredDelimiter() {
    properties.getIdentityProvider().setGroupNameDelimiter(";");

    Collection<? extends GrantedAuthority> mapped =
        mapper.mapAuthorities(Collections.singletonList(authorityWith("admin;sales")));

    assertThat(mapped).extracting("authority").containsExactlyInAnyOrder("admin", "sales");
  }

  @Test
  public void shouldMapNothingWhenTheClaimIsMissing() {
    assertThat(mapper.mapAuthorities(Collections.singletonList(authorityWith(null)))).isEmpty();
  }

  /** An unsupported claim type is logged and skipped rather than guessed at. */
  @Test
  public void shouldMapNothingWhenTheClaimIsOfAnUnsupportedType() {
    assertThat(mapper.mapAuthorities(Collections.singletonList(authorityWith(42)))).isEmpty();
  }

  @Test
  public void shouldPassOverAuthoritiesThatAreNotOAuth2Ones() {
    Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(
        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

    assertThat(mapped).isEmpty();
  }

  @Test
  public void shouldMapNothingForAnEmptyInput() {
    assertThat(mapper.mapAuthorities(Collections.<GrantedAuthority>emptyList())).isEmpty();
  }

  @Test
  public void shouldMergeGroupsComingFromSeveralAuthorities() {
    Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(Arrays.asList(
        authorityWith(Collections.singletonList("admin")),
        authorityWith("sales,admin")));

    assertThat(mapped).extracting("authority").containsExactlyInAnyOrder("admin", "sales");
  }
}
