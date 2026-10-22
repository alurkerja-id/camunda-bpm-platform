package org.camunda.bpm.engine.spring.test.components.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Method;
import java.util.Collections;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.camunda.bpm.engine.spring.components.registry.ActivitiStateHandlerRegistration;
import org.camunda.bpm.engine.spring.components.registry.ActivitiStateHandlerRegistry;
import org.junit.Before;
import org.junit.Test;

/**
 * Covers registration and lookup in {@link ActivitiStateHandlerRegistry}.
 */
public class ActivitiStateHandlerRegistryTest {

  protected ActivitiStateHandlerRegistry registry;

  @Before
  public void setUp() {
    registry = new ActivitiStateHandlerRegistry();
  }

  protected ActivitiStateHandlerRegistration registration(String process, String state, String bean) throws Exception {
    Method method = Object.class.getMethod("toString");
    return new ActivitiStateHandlerRegistration(Collections.<Integer, String>emptyMap(), method,
        new Object(), state, bean, -1, -1, process);
  }

  @Test
  public void shouldFindNothingInEmptyRegistry() {
    assertThat(registry.findRegistrationsForProcessAndState("p", "s")).isEmpty();
    assertThat(registry.findRegistrationForProcessAndState("p", "s")).isNull();
  }

  @Test
  public void shouldFindRegistrationByProcessAndState() throws Exception {
    ActivitiStateHandlerRegistration r = registration("proc", "state", "bean");
    registry.registerActivitiStateHandler(r);

    assertThat(registry.findRegistrationForProcessAndState("proc", "state")).isSameAs(r);
    assertThat(registry.findRegistrationsForProcessAndState("proc", "state")).containsExactly(r);
  }

  @Test
  public void shouldMatchCaseInsensitively() throws Exception {
    ActivitiStateHandlerRegistration r = registration("Proc", "State", "bean");
    registry.registerActivitiStateHandler(r);

    assertThat(registry.findRegistrationForProcessAndState("PROC", "STATE")).isSameAs(r);
  }

  @Test
  public void shouldFallBackToStateOnlyWhenProcessDoesNotMatch() throws Exception {
    ActivitiStateHandlerRegistration r = registration(null, "state", "bean");
    registry.registerActivitiStateHandler(r);

    assertThat(registry.findRegistrationsForProcessAndState("other", "state")).containsExactly(r);
    assertThat(registry.findRegistrationForProcessAndState("other", "state")).isSameAs(r);
  }

  @Test
  public void shouldPreferExactProcessMatchOverStateOnlyRegistration() throws Exception {
    ActivitiStateHandlerRegistration generic = registration(null, "state", "generic");
    ActivitiStateHandlerRegistration specific = registration("proc", "state", "specific");
    registry.registerActivitiStateHandler(generic);
    registry.registerActivitiStateHandler(specific);

    assertThat(registry.findRegistrationForProcessAndState("proc", "state")).isSameAs(specific);
  }

  @Test
  public void shouldReplaceRegistrationWithSameKey() throws Exception {
    ActivitiStateHandlerRegistration first = registration("proc", "state", "first");
    ActivitiStateHandlerRegistration second = registration("proc", "state", "second");
    registry.registerActivitiStateHandler(first);
    registry.registerActivitiStateHandler(second);

    assertThat(registry.findRegistrationsForProcessAndState("proc", "state")).containsExactly(second);
  }

  @Test
  public void shouldNotFindUnknownState() throws Exception {
    registry.registerActivitiStateHandler(registration("proc", "state", "bean"));

    assertThat(registry.findRegistrationForProcessAndState("proc", "unknown")).isNull();
  }

  @Test
  public void shouldRequireProcessEngineOnInitialization() {
    assertThatThrownBy(registry::afterPropertiesSet)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processEngine");

    registry.setProcessEngine(mock(ProcessEngine.class));
    registry.setBeanName("registry");
    registry.setBeanFactory(null);
  }

  @Test
  public void shouldInitializeWithProcessEngine() throws Exception {
    registry.setProcessEngine(mock(ProcessEngine.class));

    registry.afterPropertiesSet();
  }

  @Test
  public void executeIsNoOpAndSignalLeavesActivity() throws Exception {
    ActivityExecution execution = mock(ActivityExecution.class);

    registry.execute(execution);
    verifyNoInteractions(execution);

    assertThatThrownBy(() -> registry.signal(execution, "sig", null))
        .isInstanceOf(RuntimeException.class);
  }
}
