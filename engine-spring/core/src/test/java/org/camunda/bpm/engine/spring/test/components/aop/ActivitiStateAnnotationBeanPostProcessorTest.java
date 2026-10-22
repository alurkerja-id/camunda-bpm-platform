package org.camunda.bpm.engine.spring.test.components.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.spring.annotations.ProcessEngineComponent;
import org.camunda.bpm.engine.spring.annotations.ProcessId;
import org.camunda.bpm.engine.spring.annotations.ProcessVariable;
import org.camunda.bpm.engine.spring.annotations.ProcessVariables;
import org.camunda.bpm.engine.spring.annotations.State;
import org.camunda.bpm.engine.spring.components.aop.ActivitiStateAnnotationBeanPostProcessor;
import org.camunda.bpm.engine.spring.components.registry.ActivitiStateHandlerRegistration;
import org.camunda.bpm.engine.spring.components.registry.ActivitiStateHandlerRegistry;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.Ordered;

import java.util.Map;

/**
 * Covers {@link ActivitiStateAnnotationBeanPostProcessor}.
 */
public class ActivitiStateAnnotationBeanPostProcessorTest {

  protected ActivitiStateHandlerRegistry registry;
  protected ActivitiStateAnnotationBeanPostProcessor processor;

  @Before
  public void setUp() {
    registry = new ActivitiStateHandlerRegistry();
    registry.setProcessEngine(mock(ProcessEngine.class));
    processor = new ActivitiStateAnnotationBeanPostProcessor();
    processor.setRegistry(registry);
  }

  @ProcessEngineComponent(processKey = "defaultProcess")
  public static class Handler {

    @State("byValue")
    public void byValue() {
    }

    @State(state = "byState", value = "ignored")
    public void byState() {
    }

    @State(state = "withProcess", process = "customProcess")
    public void withProcess() {
    }

    @State("params")
    public void params(@ProcessVariable("a") String a, String untagged, @ProcessVariable("b") int b,
                       @ProcessVariables Map<String, Object> vars, @ProcessId String id) {
    }

    @State("paramsReordered")
    public void paramsReordered(@ProcessId String id, @ProcessVariables Map<String, Object> vars) {
    }

    public void notAHandler() {
    }
  }

  @ProcessEngineComponent
  public static class DefaultKeyHandler {
    @State("only")
    public void only() {
    }
  }

  public static class NotAComponent {
    public void noop() {
    }
  }

  @Test
  public void shouldReturnBeanUnchangedBeforeInitialization() {
    Object bean = new Object();

    assertThat(processor.postProcessBeforeInitialization(bean, "b")).isSameAs(bean);
  }

  @Test
  public void shouldDefaultToLowestPrecedence() {
    assertThat(processor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
  }

  @Test
  public void shouldValidateCollaboratorsOnInitialization() {
    assertThatThrownBy(processor::afterPropertiesSet).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("beanFactory");

    processor.setBeanFactory(new DefaultListableBeanFactory());
    processor.afterPropertiesSet();

    processor.setBeanClassLoader(null);
    assertThatThrownBy(processor::afterPropertiesSet).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("beanClassLoader");

    processor.setBeanClassLoader(getClass().getClassLoader());
    processor.afterPropertiesSet();
  }

  @Test
  public void shouldRegisterStateByValueUsingComponentProcessKey() {
    Handler bean = new Handler();
    assertThat(processor.postProcessAfterInitialization(bean, "handlerBean")).isSameAs(bean);

    ActivitiStateHandlerRegistration r = registry.findRegistrationForProcessAndState("defaultProcess", "byValue");
    assertThat(r).isNotNull();
    assertThat(r.getStateName()).isEqualTo("byValue");
    assertThat(r.getProcessName()).isEqualTo("defaultProcess");
    assertThat(r.getBeanName()).isEqualTo("handlerBean");
    assertThat(r.getHandler()).isSameAs(bean);
    assertThat(r.getHandlerMethod().getName()).isEqualTo("byValue");
    assertThat(r.requiresProcessId()).isFalse();
    assertThat(r.requiresProcessVariablesMap()).isFalse();
  }

  @Test
  public void shouldPreferExplicitStateOverValue() {
    processor.postProcessAfterInitialization(new Handler(), "handlerBean");

    assertThat(registry.findRegistrationForProcessAndState("defaultProcess", "byState")).isNotNull();
    assertThat(registry.findRegistrationForProcessAndState("defaultProcess", "ignored")).isNull();
  }

  @Test
  public void shouldPreferProcessOfStateAnnotationOverComponentKey() {
    processor.postProcessAfterInitialization(new Handler(), "handlerBean");

    ActivitiStateHandlerRegistration r = registry.findRegistrationForProcessAndState("customProcess", "withProcess");
    assertThat(r).isNotNull();
    assertThat(r.getProcessName()).isEqualTo("customProcess");
  }

  @Test
  public void shouldCollectParameterMetadata() {
    processor.postProcessAfterInitialization(new Handler(), "handlerBean");

    ActivitiStateHandlerRegistration r = registry.findRegistrationForProcessAndState("defaultProcess", "params");
    assertThat(r.getProcessVariablesExpected()).hasSize(2).containsEntry(1, "a").containsEntry(3, "b");
    assertThat(r.getProcessVariablesIndex()).isEqualTo(4);
    assertThat(r.getProcessIdIndex()).isEqualTo(5);
    assertThat(r.requiresProcessVariablesMap()).isTrue();
    assertThat(r.requiresProcessId()).isTrue();
    assertThat(r.toString()).contains("stateName=params").contains("beanName=handlerBean");
  }

  @Test
  public void shouldCollectOnlyIdAndMapIndexes() {
    processor.postProcessAfterInitialization(new Handler(), "handlerBean");

    ActivitiStateHandlerRegistration r = registry.findRegistrationForProcessAndState("defaultProcess", "paramsReordered");
    assertThat(r.getProcessVariablesExpected()).isEmpty();
    assertThat(r.getProcessIdIndex()).isEqualTo(1);
    assertThat(r.getProcessVariablesIndex()).isEqualTo(2);
  }

  @Test
  public void shouldIgnoreMethodsWithoutStateAnnotation() {
    processor.postProcessAfterInitialization(new Handler(), "handlerBean");

    assertThat(registry.findRegistrationsForProcessAndState("defaultProcess", "notAHandler")).isEmpty();
  }

  @Test
  public void shouldRegisterWithEmptyProcessNameWhenComponentHasNoKey() {
    processor.postProcessAfterInitialization(new DefaultKeyHandler(), "b");

    ActivitiStateHandlerRegistration r = registry.findRegistrationForProcessAndState("", "only");
    assertThat(r).isNotNull();
    assertThat(r.getProcessName()).isEmpty();
  }

  @Test
  public void shouldRegisterNothingForBeansWithoutStateMethods() {
    NotAComponent bean = new NotAComponent();

    assertThat(processor.postProcessAfterInitialization(bean, "plain")).isSameAs(bean);
    assertThat(registry.findRegistrationsForProcessAndState("", "noop")).isEmpty();
  }
}
