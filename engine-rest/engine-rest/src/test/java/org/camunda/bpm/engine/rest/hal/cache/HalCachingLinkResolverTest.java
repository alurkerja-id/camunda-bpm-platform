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
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.hal.Hal;
import org.camunda.bpm.engine.rest.hal.HalResource;
import org.junit.After;
import org.junit.Test;

/**
 * Covers {@link HalCachingLinkResolver}: with no cache every id is resolved straight from the
 * engine; with a cache, hits are served from it, only the misses are resolved and stored, and the
 * combined result is put in a stable order when the resolver supplies a comparator.
 */
public class HalCachingLinkResolverTest {

  static class Res extends HalResource<Res> {
    final String id;
    Res(String id) { this.id = id; }
  }

  /** Records which ids actually reached the "engine". */
  static class RecordingResolver extends HalCachingLinkResolver {
    final List<String> resolvedFromEngine = new ArrayList<>();
    final Comparator<HalResource<?>> comparator;

    RecordingResolver(Comparator<HalResource<?>> comparator) { this.comparator = comparator; }

    @Override protected Class<?> getHalResourceClass() { return Res.class; }
    @Override protected Comparator<HalResource<?>> getResourceComparator() { return comparator; }
    @Override protected String getResourceId(HalResource<?> resource) { return ((Res) resource).id; }

    @Override
    protected List<HalResource<?>> resolveNotCachedLinks(String[] linkedIds, ProcessEngine processEngine) {
      List<HalResource<?>> resources = new ArrayList<>();
      for (String id : linkedIds) {
        resolvedFromEngine.add(id);
        resources.add(new Res(id));
      }
      return resources;
    }
  }

  private static final Comparator<HalResource<?>> BY_ID = (a, b) -> ((Res) a).id.compareTo(((Res) b).id);

  private final ProcessEngine engine = mock(ProcessEngine.class);

  @After
  public void clearRegisteredCaches() {
    Hal.getInstance().destroyHalRelationCaches();
  }

  private static List<String> ids(List<HalResource<?>> resources) {
    List<String> ids = new ArrayList<>();
    for (HalResource<?> r : resources) {
      ids.add(((Res) r).id);
    }
    return ids;
  }

  @Test
  public void shouldResolveEverythingFromTheEngineWhenNoCacheIsConfigured() {
    RecordingResolver resolver = new RecordingResolver(BY_ID);

    List<HalResource<?>> result = resolver.resolveLinks(new String[] {"b", "a"}, engine);

    // no cache -> no sorting either: the engine's order is returned untouched
    assertThat(ids(result)).containsExactly("b", "a");
    assertThat(resolver.resolvedFromEngine).containsExactly("b", "a");
  }

  @Test
  public void shouldResolveAndCacheMissesThenServeThemFromTheCache() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache();
    Hal.getInstance().registerHalRelationCache(Res.class, cache);
    RecordingResolver resolver = new RecordingResolver(BY_ID);

    assertThat(ids(resolver.resolveLinks(new String[] {"b", "a"}, engine))).containsExactly("a", "b");
    assertThat(resolver.resolvedFromEngine).containsExactly("b", "a");
    assertThat(cache.size()).isEqualTo(2);

    resolver.resolvedFromEngine.clear();
    assertThat(ids(resolver.resolveLinks(new String[] {"a", "b"}, engine))).containsExactly("a", "b");
    assertThat(resolver.resolvedFromEngine).isEmpty();
  }

  @Test
  public void shouldOnlyResolveTheIdsMissingFromTheCache() {
    DefaultHalResourceCache cache = new DefaultHalResourceCache();
    Res cached = new Res("a");
    cache.put("a", cached);
    Hal.getInstance().registerHalRelationCache(Res.class, cache);
    RecordingResolver resolver = new RecordingResolver(BY_ID);

    List<HalResource<?>> result = resolver.resolveLinks(new String[] {"c", "a", "b"}, engine);

    assertThat(resolver.resolvedFromEngine).containsExactly("c", "b");
    assertThat(ids(result)).containsExactly("a", "b", "c");
    assertThat(result.get(0)).isSameAs(cached);
  }

  @Test
  public void shouldKeepResolutionOrderWhenTheResolverHasNoComparator() {
    Hal.getInstance().registerHalRelationCache(Res.class, new DefaultHalResourceCache());
    RecordingResolver resolver = new RecordingResolver(null);

    // the base class returns no comparator by default, so sortResolvedResources must be a no-op
    assertThat(ids(resolver.resolveLinks(new String[] {"z", "y"}, engine))).containsExactly("z", "y");
  }

  @Test
  public void shouldOnlyGoToTheEngineForAnEmptyIdList() {
    Hal.getInstance().registerHalRelationCache(Res.class, new DefaultHalResourceCache());
    RecordingResolver resolver = new RecordingResolver(BY_ID);

    assertThat(resolver.resolveLinks(new String[0], engine)).isEmpty();
    assertThat(resolver.resolvedFromEngine).isEmpty();
  }

  @Test
  public void shouldLookTheCacheUpByTheResourceClass() {
    RecordingResolver resolver = new RecordingResolver(BY_ID);
    assertThat(resolver.getCache()).isNull();

    DefaultHalResourceCache cache = new DefaultHalResourceCache();
    Hal.getInstance().registerHalRelationCache(Res.class, cache);

    assertThat(resolver.getCache()).isSameAs(cache);
    assertThat(resolver.getResourceComparator()).isSameAs(BY_ID);
    assertThat(new RecordingResolver(null).getResourceComparator()).isNull();
  }
}
