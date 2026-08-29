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
package org.camunda.bpm.engine.rest.hal.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.After;
import org.junit.Test;

/**
 * Covers {@link DefaultHalResourceCache}.
 *
 * <p>Two rules decide what the cache keeps: an entry older than secondsToLive is gone on read, and
 * putting past the capacity evicts - expired entries first, then the oldest ones. Time is moved
 * through {@link ClockUtil} so the age rules can be driven without waiting.
 */
public class DefaultHalResourceCacheTest {

  @After
  public void resetClock() {
    ClockUtil.reset();
  }

  // configuration -------------------------------------------------------------------------------

  @Test
  public void shouldStartWithTheDocumentedDefaults() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache();

    assertThat(cache.getCapacity()).isEqualTo(100);
    assertThat(cache.getSecondsToLive()).isEqualTo(100);
    assertThat(cache.size()).isZero();
  }

  @Test
  public void shouldAcceptCapacityAndLifetime() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache(5, 60);

    assertThat(cache.getCapacity()).isEqualTo(5);
    assertThat(cache.getSecondsToLive()).isEqualTo(60);

    cache.setCapacity(7);
    cache.setSecondsToLive(30);

    assertThat(cache.getCapacity()).isEqualTo(7);
    assertThat(cache.getSecondsToLive()).isEqualTo(30);
  }

  // storing and reading ---------------------------------------------------------------------------

  @Test
  public void shouldStoreAndReadBackAResource() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache(10, 60);

    cache.put("id", "resource");

    assertThat(cache.get("id")).isEqualTo("resource");
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  public void shouldAnswerNullForAnUnknownId() {
    assertThat(new DefaultHalResourceCache(10, 60).get("absent")).isNull();
  }

  @Test
  public void shouldRemoveAndClear() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache(10, 60);
    cache.put("first", "a");
    cache.put("second", "b");

    cache.remove("first");
    assertThat(cache.get("first")).isNull();
    assertThat(cache.size()).isEqualTo(1);

    cache.destroy();
    assertThat(cache.size()).isZero();
  }

  // expiry ------------------------------------------------------------------------------------------

  @Test
  public void shouldDropAnEntryThatOutlivedItsLifetime() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache(10, 60);
    ClockUtil.setCurrentTime(new Date(0L));
    cache.put("id", "resource");

    ClockUtil.setCurrentTime(new Date(61_000L));

    assertThat(cache.get("id")).isNull();
    assertThat(cache.size()).isZero();
  }

  @Test
  public void shouldKeepAnEntryThatIsStillYoungEnough() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache(10, 60);
    ClockUtil.setCurrentTime(new Date(0L));
    cache.put("id", "resource");

    ClockUtil.setCurrentTime(new Date(59_000L));

    assertThat(cache.get("id")).isEqualTo("resource");
  }

  // capacity ------------------------------------------------------------------------------------------

  @Test
  public void shouldStayWithinCapacityByDroppingTheOldest() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache(2, 600);

    ClockUtil.setCurrentTime(new Date(1_000L));
    cache.put("oldest", "a");
    ClockUtil.setCurrentTime(new Date(2_000L));
    cache.put("middle", "b");
    ClockUtil.setCurrentTime(new Date(3_000L));
    cache.put("newest", "c");

    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.get("oldest")).isNull();
    assertThat(cache.get("middle")).isEqualTo("b");
    assertThat(cache.get("newest")).isEqualTo("c");
  }

  /** Eviction looks at expired entries first, and stops as soon as it is back within capacity. */
  @Test
  public void shouldEvictExpiredEntriesBeforeTheOldestLivingOne() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache(2, 60);

    ClockUtil.setCurrentTime(new Date(0L));
    cache.put("stale", "a");

    ClockUtil.setCurrentTime(new Date(120_000L));
    cache.put("fresh", "b");
    cache.put("fresher", "c");

    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.get("stale")).isNull();
    assertThat(cache.get("fresh")).isEqualTo("b");
    assertThat(cache.get("fresher")).isEqualTo("c");
  }

  @Test
  public void shouldNotEvictWhileBelowCapacity() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache(5, 60);

    for (int i = 0; i < 5; i++) {
      cache.put("id" + i, "resource" + i);
    }

    assertThat(cache.size()).isEqualTo(5);
  }

  @Test
  public void shouldReplaceAnEntryStoredUnderTheSameId() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache(10, 60);

    cache.put("id", "first");
    cache.put("id", "second");

    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.get("id")).isEqualTo("second");
  }
}
