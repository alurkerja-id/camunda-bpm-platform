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
package org.camunda.bpm.engine.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.bpm.engine.impl.ProcessEngineLogger;

/**
 * Invokes every public instance method of a {@link ProcessEngineLogger} subclass with
 * synthesized arguments. Methods returning a {@link Throwable} must produce a message that
 * carries the engine error code; all other methods must complete without throwing.
 */
public final class LoggerInvocationHelper {

  private LoggerInvocationHelper() {
  }

  public static int invokeAll(ProcessEngineLogger logger) throws Exception {
    int invoked = 0;
    List<String> problems = new ArrayList<>();
    for (Method method : logger.getClass().getDeclaredMethods()) {
      if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()) || method.isSynthetic()) {
        continue;
      }
      Object[] args = new Object[method.getParameterCount()];
      for (int i = 0; i < args.length; i++) {
        args[i] = argumentFor(method.getParameterTypes()[i]);
      }
      try {
        Object result = method.invoke(logger, args);
        if (result instanceof Throwable) {
          String message = ((Throwable) result).getMessage();
          if (!hasEngineCode(message)) {
            problems.add(method.getName() + " -> message without engine code: " + message);
          }
        }
        invoked++;
      } catch (InvocationTargetException e) {
        // loggers that raise instead of return must still carry the engine error code
        String message = e.getCause() == null ? null : e.getCause().getMessage();
        if (!hasEngineCode(message)) {
          problems.add(method.getName() + " -> " + e.getCause());
        }
        invoked++;
      }
    }
    assertThat(problems).isEmpty();
    return invoked;
  }

  private static boolean hasEngineCode(String message) {
    // persistence flush errors deliberately hide the code behind a generic user-facing message
    return message != null && (message.contains("ENGINE-") || message.contains("persistence layer"));
  }

  private static Object argumentFor(Class<?> type) {
    if (type == String.class || type == CharSequence.class) {
      return "x";
    } else if (type == int.class || type == Integer.class) {
      return 1;
    } else if (type == long.class || type == Long.class) {
      return 1L;
    } else if (type == boolean.class || type == Boolean.class) {
      return false;
    } else if (type == double.class || type == Double.class) {
      return 1.0d;
    } else if (type == short.class || type == byte.class || type == char.class || type == float.class) {
      return Array0.of(type);
    } else if (type == Class.class) {
      return String.class;
    } else if (type == Object.class) {
      return "x";
    } else if (type.isAssignableFrom(ArrayList.class)) {
      return new ArrayList<>();
    } else if (type.isAssignableFrom(HashSet.class)) {
      return new HashSet<>();
    } else if (type == Collection.class) {
      return new ArrayList<>();
    } else if (type == Set.class) {
      return new HashSet<>();
    } else if (type.isAssignableFrom(HashMap.class)) {
      return new HashMap<>();
    } else if (type == Map.class) {
      return new HashMap<>();
    } else if (type.isAssignableFrom(RuntimeException.class)) {
      return new RuntimeException("x");
    } else if (type.isAssignableFrom(Exception.class)) {
      return new Exception("x");
    } else if (type.isEnum()) {
      return type.getEnumConstants()[0];
    } else if (type.isArray()) {
      return java.lang.reflect.Array.newInstance(type.getComponentType(), 0);
    } else {
      return mock(type);
    }
  }

  private static final class Array0 {
    static Object of(Class<?> type) {
      return java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(type, 1), 0);
    }
  }
}
