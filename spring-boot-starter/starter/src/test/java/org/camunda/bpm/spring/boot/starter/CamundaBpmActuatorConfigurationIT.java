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
package org.camunda.bpm.spring.boot.starter;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.spring.boot.starter.test.nonpa.TestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(classes = { TestApplication.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CamundaBpmActuatorConfigurationIT extends AbstractCamundaAutoConfigurationIT{

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Test
  public void jobExecutorHealthIndicatorTest() throws Exception {
    final String body = getHealthBody();
    assertEquals("wrong body " + body, "UP", component(body, "jobExecutor").path("status").asText());
  }

  @Test
  public void processEngineHealthIndicatorTest() throws Exception {
    final String body = getHealthBody();
    final JsonNode processEngine = component(body, "processEngine");
    assertEquals("wrong body " + body, "UP", processEngine.path("status").asText());
    assertEquals("wrong body " + body, "testEngine", processEngine.path("details").path("name").asText());
  }

  /**
   * The health response used to be matched as a substring, which broke as soon as the actuator
   * started writing "details" before "status". Reading the component out of the parsed document
   * asserts the same thing without depending on how the fields happen to be ordered.
   */
  private JsonNode component(String body, String name) throws Exception {
    return new ObjectMapper().readTree(body).path("components").path(name);
  }

  private String getHealthBody() {
    ResponseEntity<String> entity = testRestTemplate.getForEntity("/actuator/health", String.class);
    final String body = entity.getBody();
    return body;
  }
}
