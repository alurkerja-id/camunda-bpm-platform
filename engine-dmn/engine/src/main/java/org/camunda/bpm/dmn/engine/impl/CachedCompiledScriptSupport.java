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
package org.camunda.bpm.dmn.engine.impl;

import javax.script.CompiledScript;
import javax.script.ScriptException;

/**
 * @author Daniel Meyer
 *
 */
public interface CachedCompiledScriptSupport {

  void cacheCompiledScript(CompiledScript compiledScript);

  CompiledScript getCachedCompiledScript();

  /**
   * Returns the cached script, compiling and caching it on first use. The lock lives here rather
   * than at the call site: callers used to synchronize on the support object they were handed as a
   * method parameter, which puts the lock in the hands of whoever passes it.
   */
  default CompiledScript getOrCacheCompiledScript(CompiledScriptSupplier supplier) throws ScriptException {
    CompiledScript compiledScript = getCachedCompiledScript();
    if (compiledScript == null) {
      synchronized (this) {
        compiledScript = getCachedCompiledScript();
        if (compiledScript == null) {
          compiledScript = supplier.compile();
          cacheCompiledScript(compiledScript);
        }
      }
    }
    return compiledScript;
  }

  /** Compiles a script; separate from {@link java.util.function.Supplier} because compiling throws. */
  @FunctionalInterface
  interface CompiledScriptSupplier {
    CompiledScript compile() throws ScriptException;
  }

}
