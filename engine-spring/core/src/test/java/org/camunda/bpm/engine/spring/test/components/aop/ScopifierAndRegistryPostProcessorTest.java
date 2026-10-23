package org.camunda.bpm.engine.spring.test.components.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.spring.components.ActivitiContextUtils;
import org.camunda.bpm.engine.spring.components.aop.util.Scopifier;
import org.camunda.bpm.engine.spring.components.config.xml.StateHandlerAnnotationBeanFactoryPostProcessor;
import org.camunda.bpm.engine.spring.components.registry.ActivitiStateHandlerRegistry;
import org.junit.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Covers {@link Scopifier} and {@link StateHandlerAnnotationBeanFactoryPostProcessor}.
 */
public class ScopifierAndRegistryPostProcessorTest {

  protected RootBeanDefinition definition(String scope) {
    RootBeanDefinition definition = new RootBeanDefinition(Object.class);
    definition.setScope(scope);
    return definition;
  }

  @Test
  public void shouldCreateScopedProxyAndRegisterItUnderBeanName() {
    DefaultListableBeanFactory registry = new DefaultListableBeanFactory();

    BeanDefinitionHolder holder = Scopifier.createScopedProxy("bean", definition("process"), registry, false);

    assertThat(holder.getBeanName()).isEqualTo("bean");
    assertThat(registry.containsBeanDefinition("bean")).isTrue();
    assertThat(registry.containsBeanDefinition("scopedTarget.bean")).isTrue();
    assertThat(registry.getBeanDefinition("scopedTarget.bean").getScope()).isEqualTo("process");
  }

  @Test
  public void shouldProxyNestedBeanOfSameScopeInUnscopedParent() {
    DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
    Scopifier scopifier = new Scopifier(registry, "process", false, false);
    RootBeanDefinition parent = new RootBeanDefinition(Object.class);
    parent.getPropertyValues().add("nested", definition("process"));

    scopifier.visitBeanDefinition(parent);

    assertThat(parent.getPropertyValues().getPropertyValue("nested").getValue()).isInstanceOf(BeanDefinitionHolder.class);
    assertThat(registry.getBeanDefinitionNames()).isNotEmpty();
  }

  @Test
  public void shouldNotProxyNestedBeanWhenParentIsAlreadyScopedOrScopeDiffers() {
    DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
    RootBeanDefinition scopedParent = new RootBeanDefinition(Object.class);
    RootBeanDefinition nested = definition("process");
    scopedParent.getPropertyValues().add("nested", nested);
    new Scopifier(registry, "process", false, true).visitBeanDefinition(scopedParent);

    RootBeanDefinition otherScopeParent = new RootBeanDefinition(Object.class);
    BeanDefinition singleton = definition("singleton");
    otherScopeParent.getPropertyValues().add("nested", singleton);
    new Scopifier(registry, "process", false, false).visitBeanDefinition(otherScopeParent);

    assertThat(scopedParent.getPropertyValues().getPropertyValue("nested").getValue()).isSameAs(nested);
    assertThat(otherScopeParent.getPropertyValues().getPropertyValue("nested").getValue()).isSameAs(singleton);
    assertThat(registry.getBeanDefinitionNames()).isEmpty();
  }

  @Test
  public void shouldProxyNestedBeanHolderOfSameScope() {
    DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
    RootBeanDefinition parent = new RootBeanDefinition(Object.class);
    parent.getPropertyValues().add("nested", new BeanDefinitionHolder(definition("process"), "inner"));

    new Scopifier(registry, "process", true, false).visitBeanDefinition(parent);

    assertThat(registry.containsBeanDefinition("inner")).isTrue();
    assertThat(registry.containsBeanDefinition("scopedTarget.inner")).isTrue();
  }

  @Test
  public void shouldRegisterDefaultRegistryWithProcessEngine() {
    DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
    ProcessEngine engine = mock(ProcessEngine.class);
    StateHandlerAnnotationBeanFactoryPostProcessor processor = new StateHandlerAnnotationBeanFactoryPostProcessor();
    processor.setProcessEngine(engine);

    processor.postProcessBeanFactory(factory);

    BeanDefinition registered = factory.getBeanDefinition(ActivitiContextUtils.ACTIVITI_REGISTRY_BEAN_NAME);
    assertThat(registered.getBeanClassName()).isEqualTo(ActivitiStateHandlerRegistry.class.getName());
    assertThat(registered.getPropertyValues().getPropertyValue("processEngine").getValue()).isSameAs(engine);
  }

  @Test
  public void shouldKeepUserRegisteredRegistry() {
    DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
    RootBeanDefinition user = new RootBeanDefinition(ActivitiStateHandlerRegistry.class);
    factory.registerBeanDefinition(ActivitiContextUtils.ACTIVITI_REGISTRY_BEAN_NAME, user);

    new StateHandlerAnnotationBeanFactoryPostProcessor().postProcessBeanFactory(factory);

    assertThat(factory.getBeanDefinition(ActivitiContextUtils.ACTIVITI_REGISTRY_BEAN_NAME)).isSameAs(user);
  }

  @Test
  public void shouldRejectReservedNameWithWrongType() {
    DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
    factory.registerBeanDefinition(ActivitiContextUtils.ACTIVITI_REGISTRY_BEAN_NAME, new RootBeanDefinition(Object.class));

    assertThatThrownBy(() -> new StateHandlerAnnotationBeanFactoryPostProcessor().postProcessBeanFactory(factory))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("is reserved");
  }

  @Test
  public void shouldTolerateBeanFactoryWithoutRegistry() {
    ConfigurableListableBeanFactory factory = mock(ConfigurableListableBeanFactory.class);

    new StateHandlerAnnotationBeanFactoryPostProcessor().postProcessBeanFactory(factory);
  }
}
