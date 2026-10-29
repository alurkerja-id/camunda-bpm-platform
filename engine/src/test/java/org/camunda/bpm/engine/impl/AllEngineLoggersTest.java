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
package org.camunda.bpm.engine.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;
import javax.naming.NamingException;

import org.camunda.bpm.engine.ProcessEngineException;
import org.jboss.vfs.VirtualFile;
import org.junit.Test;

public class AllEngineLoggersTest {

  @Test
  public void shouldInvokeEveryLoggerMethod() throws Exception {
    int loggers = 0;
    int invoked = 0;
    int built = 0;

    for (Field field : ProcessEngineLogger.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) || !ProcessEngineLogger.class.isAssignableFrom(field.getType())) {
        continue;
      }
      field.setAccessible(true);
      Object logger = field.get(null);
      loggers++;

      for (Method method : logger.getClass().getDeclaredMethods()) {
        if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
          continue;
        }
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
          args[i] = argumentFor(types[i]);
        }
        try {
          Object result = method.invoke(logger, args);
          if (result instanceof ProcessEngineException) {
            built++;
          }
        } catch (InvocationTargetException e) {
          // log methods that dereference their argument or throw by design
          if (e.getCause() instanceof ProcessEngineException) {
            built++;
          }
        }
        invoked++;
      }
    }

    assertThat(loggers).isGreaterThan(20);
    assertThat(invoked).isGreaterThan(300);
    assertThat(built).isGreaterThan(100);
  }

  protected Object argumentFor(Class<?> type) throws Exception {
    if (type == String.class) {
      return "value";
    } else if (type == int.class || type == Integer.class) {
      return 1;
    } else if (type == long.class || type == Long.class) {
      return 1L;
    } else if (type == boolean.class || type == Boolean.class) {
      return true;
    } else if (type == IOException.class) {
      return new IOException("io");
    } else if (type == MalformedURLException.class) {
      return new MalformedURLException("url");
    } else if (type == URISyntaxException.class) {
      return new URISyntaxException("in", "reason");
    } else if (type == NamingException.class) {
      return new NamingException("naming");
    } else if (type == RejectedExecutionException.class) {
      return new RejectedExecutionException("rejected");
    } else if (type == Exception.class) {
      return new Exception("e");
    } else if (type == Throwable.class) {
      return new Throwable("t");
    } else if (type == URL.class) {
      return new URL("http://localhost/x");
    } else if (type == ObjectName.class) {
      return new ObjectName("camunda:name=test");
    } else if (type == TimeUnit.class) {
      return TimeUnit.SECONDS;
    } else if (type == Collection.class) {
      return new ArrayList<Object>();
    } else if (type == VirtualFile.class) {
      return mock(VirtualFile.class);
    } else if (type == java.lang.reflect.Type.class || type == Class.class) {
      return String.class;
    } else if (type == Object.class) {
      return "object";
    } else if (type == double.class || type == Double.class) {
      return 1.0d;
    } else if (type == java.util.List.class) {
      return new ArrayList<Object>();
    } else if (type == java.util.Map.class) {
      return new java.util.HashMap<Object, Object>();
    } else if (type == java.util.Set.class) {
      return new java.util.HashSet<Object>();
    } else if (type == java.util.Date.class) {
      return new java.util.Date();
    } else if (type.isArray()) {
      return java.lang.reflect.Array.newInstance(type.getComponentType(), 0);
    } else if (Exception.class.isAssignableFrom(type)) {
      try {
        return type.getConstructor(String.class).newInstance("e");
      } catch (ReflectiveOperationException e) {
        return null;
      }
    } else if (type.isInterface() || !Modifier.isFinal(type.getModifiers())) {
      try {
        return mock(type);
      } catch (RuntimeException e) {
        return null;
      }
    }
    return null;
  }

}
