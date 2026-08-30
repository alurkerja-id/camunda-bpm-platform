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

import java.util.function.Supplier;

import org.camunda.bpm.dmn.engine.impl.spi.el.ElExpression;

/**
 * @author Daniel Meyer
 *
 */
public interface CachedExpressionSupport {

  void setCachedExpression(ElExpression expression);

  ElExpression getCachedExpression();

  /**
   * Returns the cached expression, creating and caching it on first use. The lock lives here rather
   * than at the call site: callers used to synchronize on the support object they were handed as a
   * method parameter, which puts the lock in the hands of whoever passes it.
   */
  default ElExpression getOrCacheExpression(Supplier<ElExpression> supplier) {
    ElExpression expression = getCachedExpression();
    if (expression == null) {
      synchronized (this) {
        expression = getCachedExpression();
        if (expression == null) {
          expression = supplier.get();
          setCachedExpression(expression);
        }
      }
    }
    return expression;
  }

}
