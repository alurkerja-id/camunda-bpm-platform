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
package org.camunda.bpm.engine.impl.bpmn.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.SimpleEmail;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.Expression;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MailActivityBehaviorTest {

  protected ProcessEngineConfigurationImpl configuration;
  protected ActivityExecution execution;
  protected MailActivityBehavior behavior;

  @Before
  public void setUp() {
    configuration = mock(ProcessEngineConfigurationImpl.class);
    when(configuration.getMailServerHost()).thenReturn("127.0.0.1");
    when(configuration.getMailServerPort()).thenReturn(1);
    when(configuration.getMailServerDefaultFrom()).thenReturn("engine@example.org");
    Context.setProcessEngineConfiguration(configuration);
    execution = mock(ActivityExecution.class);
    behavior = new MailActivityBehavior();
  }

  @After
  public void tearDown() {
    Context.removeProcessEngineConfiguration();
  }

  protected Expression literal(String value) {
    Expression expression = mock(Expression.class);
    when(expression.getValue(any(DelegateExecution.class))).thenReturn(value);
    return expression;
  }

  @Test
  public void shouldCreateHtmlEmailWithTextAlternative() {
    Email email = behavior.createEmail("plain", "<b>rich</b>");

    assertThat(email).isInstanceOf(HtmlEmail.class);
  }

  @Test
  public void shouldCreateHtmlEmailWithoutTextAlternative() {
    assertThat(behavior.createEmail(null, "<b>rich</b>")).isInstanceOf(HtmlEmail.class);
  }

  @Test
  public void shouldCreateTextOnlyEmail() {
    assertThat(behavior.createEmail("plain", null)).isInstanceOf(SimpleEmail.class);
  }

  @Test
  public void shouldRejectEmailWithoutAnyBody() {
    assertThatThrownBy(() -> behavior.createEmail(null, null))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-");
  }

  @Test
  public void shouldSplitAndTrimRecipients() {
    assertThat(behavior.splitAndTrim(" a@x.org , b@x.org,c@x.org ")).containsExactly("a@x.org", "b@x.org", "c@x.org");
    assertThat(behavior.splitAndTrim(null)).isNull();
  }

  @Test
  public void shouldAddRecipientsCcAndBcc() throws Exception {
    Email email = new SimpleEmail();

    behavior.addTo(email, "a@x.org, b@x.org");
    behavior.addCc(email, "c@x.org");
    behavior.addBcc(email, "d@x.org");

    assertThat(email.getToAddresses()).hasSize(2);
    assertThat(email.getCcAddresses()).hasSize(1);
    assertThat(email.getBccAddresses()).hasSize(1);
  }

  @Test
  public void shouldIgnoreMissingCcAndBcc() {
    Email email = new SimpleEmail();

    behavior.addCc(email, null);
    behavior.addBcc(email, null);

    assertThat(email.getCcAddresses()).isEmpty();
    assertThat(email.getBccAddresses()).isEmpty();
  }

  @Test
  public void shouldRejectMissingRecipients() {
    assertThatThrownBy(() -> behavior.addTo(new SimpleEmail(), null))
        .isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldWrapInvalidAddresses() {
    Email email = new SimpleEmail();

    assertThatThrownBy(() -> behavior.addTo(email, "not an address@@"))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() -> behavior.addCc(email, "not an address@@"))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() -> behavior.addBcc(email, "not an address@@"))
        .isInstanceOf(ProcessEngineException.class);
    assertThatThrownBy(() -> behavior.setFrom(email, "not an address@@"))
        .isInstanceOf(ProcessEngineException.class);
  }

  @Test
  public void shouldUseExplicitSenderOrConfiguredDefault() {
    Email explicit = new SimpleEmail();
    Email defaulted = new SimpleEmail();

    behavior.setFrom(explicit, "me@x.org");
    behavior.setFrom(defaulted, null);

    assertThat(explicit.getFromAddress().getAddress()).isEqualTo("me@x.org");
    assertThat(defaulted.getFromAddress().getAddress()).isEqualTo("engine@example.org");
  }

  @Test
  public void shouldDefaultSubjectToEmpty() {
    Email withSubject = new SimpleEmail();
    Email withoutSubject = new SimpleEmail();

    behavior.setSubject(withSubject, "hello");
    behavior.setSubject(withoutSubject, null);

    assertThat(withSubject.getSubject()).isEqualTo("hello");
    assertThat(withoutSubject.getSubject()).isEmpty();
  }

  @Test
  public void shouldApplyMailServerProperties() throws Exception {
    when(configuration.getMailServerUseTLS()).thenReturn(true);
    when(configuration.getMailServerUsername()).thenReturn("user");
    when(configuration.getMailServerPassword()).thenReturn("secret");
    Email email = new SimpleEmail();

    behavior.setMailServerProperties(email);

    assertThat(email.getHostName()).isEqualTo("127.0.0.1");
    assertThat(email.getSmtpPort()).isEqualTo("1");
    assertThat(email.isStartTLSEnabled()).isTrue();
    assertThat(email.getMailSession()).isNotNull();
  }

  @Test
  public void shouldRejectMissingSmtpHost() {
    when(configuration.getMailServerHost()).thenReturn(null);

    assertThatThrownBy(() -> behavior.setMailServerProperties(new SimpleEmail()))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("SMTP host");
  }

  @Test
  public void shouldApplyCharsetOnlyWhenFieldIsConfigured() {
    Email without = mock(Email.class);
    Email with = mock(Email.class);

    behavior.setCharset(without, "UTF-16");
    behavior.charset = literal("UTF-16");
    behavior.setCharset(with, "UTF-16");

    verify(without, never()).setCharset(any());
    verify(with).setCharset("UTF-16");
  }

  @Test
  public void shouldWrapFailureToSendTheEmail() {
    behavior.to = literal("a@x.org");
    behavior.text = literal("hi");

    assertThatThrownBy(() -> behavior.execute(execution))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("a@x.org");
  }

  @Test
  public void shouldReadFieldsAsNullWhenExpressionIsMissingOrEvaluatesToNull() {
    assertThat(behavior.getStringFromField(null, execution)).isNull();
    assertThat(behavior.getStringFromField(literal(null), execution)).isNull();
    assertThat(behavior.getStringFromField(literal("x"), execution)).isEqualTo("x");
  }
}
