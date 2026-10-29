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
package org.camunda.bpm.engine.cdi.jsf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.Conversation;
import javax.enterprise.inject.Instance;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;

import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.cdi.BusinessProcess;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.repository.ProcessDefinitionQuery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class TaskFormUnitTest {

  /** exposes the protected static setter of FacesContext */
  private abstract static class FacesContextHolder extends FacesContext {
    static void install(FacesContext context) {
      setCurrentInstance(context);
    }
  }

  @Mock private BusinessProcess businessProcess;
  @Mock private RepositoryService repositoryService;
  @Mock private Instance<Conversation> conversationInstance;
  @Mock private Conversation conversation;
  @Mock private FacesContext facesContext;
  @Mock private ExternalContext externalContext;
  @InjectMocks private TaskForm taskForm;

  private final Map<String, String> requestParameters = new HashMap<>();

  @Before
  public void setUp() {
    FacesContextHolder.install(facesContext);
    when(facesContext.getExternalContext()).thenReturn(externalContext);
    when(externalContext.getRequestParameterMap()).thenReturn(requestParameters);
    when(conversationInstance.get()).thenReturn(conversation);
  }

  @After
  public void tearDown() {
    FacesContextHolder.install(null);
  }

  @Test
  @SuppressWarnings("deprecation")
  public void startTaskStoresUrlAndStartsTaskInConversation() {
    taskForm.startTask("t1", "/done");

    assertThat(taskForm.getUrl()).isEqualTo("/done");
    verify(businessProcess).startTask("t1", true);
  }

  @Test
  @SuppressWarnings("deprecation")
  public void startTaskIgnoresMissingParametersOnPostback() {
    when(facesContext.isPostback()).thenReturn(true);

    taskForm.startTask(null, "/done");
    taskForm.startTask("t1", null);

    verify(businessProcess, never()).startTask("t1", true);
    assertThat(taskForm.getUrl()).isNull();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void startTaskLogsAndReturnsOnMissingParametersWithoutPostback() {
    when(facesContext.isPostback()).thenReturn(false);

    taskForm.startTask(null, null);

    verify(businessProcess, never()).startTask(null, true);
    assertThat(taskForm.getUrl()).isNull();
  }

  @Test
  public void startTaskFormReadsRequestParameters() {
    requestParameters.put("taskId", "t2");
    requestParameters.put("callbackUrl", "/back");

    taskForm.startTaskForm();

    assertThat(taskForm.getUrl()).isEqualTo("/back");
    verify(businessProcess).startTask("t2", true);
  }

  @Test
  public void startTaskFormWithoutParametersDoesNothing() {
    when(facesContext.isPostback()).thenReturn(true);
    taskForm.startTaskForm();
    when(facesContext.isPostback()).thenReturn(false);
    requestParameters.put("taskId", "t2");
    taskForm.startTaskForm();

    verify(businessProcess, never()).startTask("t2", true);
  }

  @Test
  public void completeTaskCompletesAndRedirects() throws IOException {
    taskForm.setUrl("/after");

    taskForm.completeTask();

    verify(businessProcess).completeTask(true);
    verify(externalContext).redirect("/after");
  }

  @Test
  @SuppressWarnings("deprecation")
  public void startProcessInstanceByIdFormBeginsTransientConversation() {
    when(conversation.isTransient()).thenReturn(true);

    taskForm.startProcessInstanceByIdForm("def:1", "/cb");

    assertThat(taskForm.getUrl()).isEqualTo("/cb");
    verify(conversation).begin();
  }

  @Test
  @SuppressWarnings("deprecation")
  public void startProcessInstanceByKeyFormKeepsRunningConversation() {
    when(conversation.isTransient()).thenReturn(false);

    taskForm.startProcessInstanceByKeyForm("key", "/cb");

    assertThat(taskForm.getUrl()).isEqualTo("/cb");
    verify(conversation, never()).begin();
  }

  @Test
  public void startProcessInstanceByIdFormFromRequest() {
    requestParameters.put("callbackUrl", "/cb");
    requestParameters.put("processDefinitionId", "def:1");
    when(conversation.isTransient()).thenReturn(true);

    taskForm.startProcessInstanceByIdForm();

    assertThat(taskForm.getUrl()).isEqualTo("/cb");
    verify(conversation).begin();
  }

  @Test
  public void startProcessInstanceByKeyFormFromRequest() {
    requestParameters.put("callbackUrl", "/cb2");
    requestParameters.put("processDefinitionKey", "key");
    when(conversation.isTransient()).thenReturn(true);

    taskForm.startProcessInstanceByKeyForm();

    assertThat(taskForm.getUrl()).isEqualTo("/cb2");
    verify(conversation).begin();
  }

  @Test
  public void startProcessInstanceFormsIgnorePostback() {
    when(facesContext.isPostback()).thenReturn(true);
    requestParameters.put("callbackUrl", "/ignored");

    taskForm.startProcessInstanceByIdForm();
    taskForm.startProcessInstanceByKeyForm();

    assertThat(taskForm.getUrl()).isNull();
    verify(conversation, never()).begin();
  }

  @Test
  public void completeProcessInstanceFormStartsByIdThenClearsIt() throws IOException {
    requestParameters.put("callbackUrl", "/cb");
    requestParameters.put("processDefinitionId", "def:1");
    taskForm.startProcessInstanceByIdForm();

    taskForm.completeProcessInstanceForm();

    verify(businessProcess).startProcessById("def:1");
    verify(conversation).end();
    verify(externalContext).redirect("/cb");
    // id was cleared, so a second completion falls through to the key
    taskForm.completeProcessInstanceForm();
    verify(businessProcess).startProcessByKey(null);
  }

  @Test
  public void completeProcessInstanceFormStartsByKey() throws IOException {
    requestParameters.put("callbackUrl", "/cb");
    requestParameters.put("processDefinitionKey", "key");
    taskForm.startProcessInstanceByKeyForm();

    taskForm.completeProcessInstanceForm();

    verify(businessProcess).startProcessByKey("key");
    verify(businessProcess, never()).startProcessById("key");
  }

  @Test
  public void getProcessDefinitionQueriesById() {
    ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
    ProcessDefinition definition = mock(ProcessDefinition.class);
    when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
    when(query.processDefinitionId("def:1")).thenReturn(query);
    when(query.singleResult()).thenReturn(definition);
    requestParameters.put("processDefinitionId", "def:1");
    taskForm.startProcessInstanceByIdForm();

    assertThat(taskForm.getProcessDefinition()).isSameAs(definition);
  }

  @Test
  public void getProcessDefinitionQueriesLatestVersionByKey() {
    ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
    ProcessDefinition definition = mock(ProcessDefinition.class);
    when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
    when(query.processDefinitionKey("key")).thenReturn(query);
    when(query.latestVersion()).thenReturn(query);
    when(query.singleResult()).thenReturn(definition);
    requestParameters.put("processDefinitionKey", "key");
    taskForm.startProcessInstanceByKeyForm();

    assertThat(taskForm.getProcessDefinition()).isSameAs(definition);
  }
}
