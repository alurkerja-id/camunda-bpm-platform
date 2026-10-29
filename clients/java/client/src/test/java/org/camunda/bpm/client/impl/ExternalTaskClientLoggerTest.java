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
package org.camunda.bpm.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.client.exception.BadRequestException;
import org.camunda.bpm.client.exception.ConnectionLostException;
import org.camunda.bpm.client.exception.EngineException;
import org.camunda.bpm.client.exception.ExternalTaskClientException;
import org.camunda.bpm.client.exception.NotFoundException;
import org.camunda.bpm.client.exception.RestException;
import org.camunda.bpm.client.exception.UnknownHttpErrorException;
import org.junit.Test;

public class ExternalTaskClientLoggerTest {

  protected static EngineClientException engineFailure(Throwable cause) {
    return new EngineClientException("failure", cause) { };
  }

  protected static RestException rest(int status) {
    RestException exception = new RestException("rest message", "type", 1);
    exception.setHttpStatusCode(status);
    return exception;
  }

  @Test
  public void shouldMapRestStatusCodesToSpecificExceptions() {
    ExternalTaskClientLogger logger = ExternalTaskClientLogger.CLIENT_LOGGER;

    assertThat(logger.handledEngineClientException("act", engineFailure(rest(400)))).isInstanceOf(BadRequestException.class);
    assertThat(logger.handledEngineClientException("act", engineFailure(rest(404)))).isInstanceOf(NotFoundException.class);
    assertThat(logger.handledEngineClientException("act", engineFailure(rest(500)))).isInstanceOf(EngineException.class);
    ExternalTaskClientException unknown = logger.handledEngineClientException("act", engineFailure(rest(418)));
    assertThat(unknown).isInstanceOf(UnknownHttpErrorException.class);
    assertThat(unknown.getMessage()).contains("418").contains("rest message");
  }

  @Test
  public void shouldMapIoFailureToConnectionLost() {
    ExternalTaskClientException result = ExternalTaskClientLogger.CLIENT_LOGGER
        .handledEngineClientException("act", engineFailure(new IOException("no route")));

    assertThat(result).isInstanceOf(ConnectionLostException.class);
    assertThat(result.getMessage()).contains("no route");
  }

  @Test
  public void shouldFallBackToGenericExceptionForOtherCauses() {
    ExternalTaskClientException result = ExternalTaskClientLogger.CLIENT_LOGGER
        .handledEngineClientException("act", engineFailure(new IllegalStateException("x")));

    assertThat(result).isExactlyInstanceOf(ExternalTaskClientException.class);
    assertThat(result.getCause()).isInstanceOf(EngineClientException.class);
    assertThat(result.getMessage()).contains("act");
  }

  @Test
  public void shouldCreateMessageForEveryExceptionFactory() throws Exception {
    assertThat(assertEveryFactoryProducesMessage(ExternalTaskClientLogger.CLIENT_LOGGER, ExternalTaskClientLogger.class))
        .isGreaterThan(20);
  }

  @Test
  public void shouldCreateMessageForEverySpecializedLoggerFactory() throws Exception {
    assertEveryFactoryProducesMessage(ExternalTaskClientLogger.ENGINE_CLIENT_LOGGER, EngineClientLogger.class);
    assertEveryFactoryProducesMessage(ExternalTaskClientLogger.TOPIC_SUBSCRIPTION_MANAGER_LOGGER,
        org.camunda.bpm.client.topic.impl.TopicSubscriptionManagerLogger.class);
    assertEveryFactoryProducesMessage(ExternalTaskClientLogger.XML_FORMAT_LOGGER,
        org.camunda.bpm.client.variable.impl.format.xml.DomXmlLogger.class);
    assertEveryFactoryProducesMessage(ExternalTaskClientLogger.JSON_FORMAT_LOGGER,
        org.camunda.bpm.client.variable.impl.format.json.JacksonJsonLogger.class);
    assertEveryFactoryProducesMessage(ExternalTaskClientLogger.SERIALIZABLE_FORMAT_LOGGER,
        org.camunda.bpm.client.variable.impl.format.serializable.SerializableLogger.class);
  }

  /** invokes each exception factory of a logger with placeholder arguments and checks the produced message */
  public static int assertEveryFactoryProducesMessage(Object logger, Class<?> loggerClass) throws Exception {
    List<String> checked = new ArrayList<>();
    for (Method method : loggerClass.getDeclaredMethods()) {
      if (Modifier.isStatic(method.getModifiers()) || !Throwable.class.isAssignableFrom(method.getReturnType())
          || method.isSynthetic() || method.getName().equals("handledEngineClientException")) {
        continue;
      }
      method.setAccessible(true);
      Object[] args = new Object[method.getParameterCount()];
      Class<?>[] types = method.getParameterTypes();
      for (int i = 0; i < types.length; i++) {
        args[i] = placeholder(types[i]);
      }
      Object result;
      try {
        result = method.invoke(logger, args);
      } catch (InvocationTargetException e) {
        throw new AssertionError("factory " + method.getName() + " threw", e.getCause());
      }
      assertThat(result).as(method.getName()).isInstanceOf(Throwable.class);
      assertThat(((Throwable) result).getMessage()).as(method.getName()).isNotBlank();
      checked.add(method.getName());
    }
    return checked.size();
  }

  protected static Object placeholder(Class<?> type) throws Exception {
    if (type == String.class) {
      return "value";
    } else if (type == int.class || type == Integer.class) {
      return 1;
    } else if (type == long.class || type == Long.class) {
      return 1L;
    } else if (type == boolean.class || type == Boolean.class) {
      return true;
    } else if (type == Throwable.class || type == Exception.class) {
      return new RuntimeException("cause");
    } else if (type == IOException.class) {
      return new IOException("cause");
    } else if (type == Class.class) {
      return String.class;
    } else if (type == Object.class) {
      return "object";
    } else if (RestException.class.isAssignableFrom(type)) {
      return rest(500);
    } else if (type.isInterface()) {
      return java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
          (proxy, m, a) -> m.getReturnType() == String.class ? "name" : null);
    }
    return null;
  }

}
