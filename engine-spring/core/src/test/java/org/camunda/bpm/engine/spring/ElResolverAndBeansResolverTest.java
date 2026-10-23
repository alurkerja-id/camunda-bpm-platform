package org.camunda.bpm.engine.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.camunda.bpm.impl.juel.jakarta.el.CompositeELResolver;
import org.camunda.bpm.impl.juel.jakarta.el.ELContext;
import org.camunda.bpm.impl.juel.jakarta.el.ELResolver;

import org.camunda.bpm.engine.ProcessEngineException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;

/**
 * Covers {@link ApplicationContextElResolver}, {@link SpringBeansResolverFactory} and {@link SpringExpressionManager}.
 */
public class ElResolverAndBeansResolverTest {

  protected ApplicationContext applicationContext;
  protected ELContext elContext;

  @Before
  public void setUp() {
    applicationContext = mock(ApplicationContext.class);
    elContext = new ELContext() {
      @Override
      public ELResolver getELResolver() {
        return null;
      }

      @Override
      public org.camunda.bpm.impl.juel.jakarta.el.FunctionMapper getFunctionMapper() {
        return null;
      }

      @Override
      public org.camunda.bpm.impl.juel.jakarta.el.VariableMapper getVariableMapper() {
        return null;
      }
    };
    when(applicationContext.containsBean("known")).thenReturn(true);
    when(applicationContext.getBean("known")).thenReturn("bean");
  }

  @Test
  public void elResolverResolvesKnownBeanAndMarksPropertyResolved() {
    ApplicationContextElResolver resolver = new ApplicationContextElResolver(applicationContext);

    assertThat(resolver.getValue(elContext, null, "known")).isEqualTo("bean");
    assertThat(elContext.isPropertyResolved()).isTrue();
  }

  @Test
  public void elResolverIgnoresUnknownBeansAndNonNullBase() {
    ApplicationContextElResolver resolver = new ApplicationContextElResolver(applicationContext);

    assertThat(resolver.getValue(elContext, null, "unknown")).isNull();
    assertThat(resolver.getValue(elContext, new Object(), "known")).isNull();
    assertThat(elContext.isPropertyResolved()).isFalse();
  }

  @Test
  public void elResolverIsReadOnlyAndRejectsWritingBeans() {
    ApplicationContextElResolver resolver = new ApplicationContextElResolver(applicationContext);

    assertThat(resolver.isReadOnly(elContext, null, "known")).isTrue();
    assertThatThrownBy(() -> resolver.setValue(elContext, null, "known", "x"))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Cannot set value of 'known'");
    resolver.setValue(elContext, null, "unknown", "x");
    resolver.setValue(elContext, new Object(), "known", "x");
  }

  @Test
  public void elResolverReportsGenericTypeInformation() {
    ApplicationContextElResolver resolver = new ApplicationContextElResolver(applicationContext);

    assertThat(resolver.getCommonPropertyType(elContext, null)).isEqualTo(Object.class);
    assertThat(resolver.getType(elContext, null, "known")).isEqualTo(Object.class);
    assertThat(resolver.getFeatureDescriptors(elContext, null)).isNull();
  }

  @Test
  public void beansResolverExposesBeanNamesAsKeys() {
    when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {"a", "b"});
    SpringBeansResolverFactory resolver = new SpringBeansResolverFactory(applicationContext);

    assertThat(resolver.keySet()).containsExactlyInAnyOrder("a", "b");
    assertThat(resolver.containsKey("a")).isTrue();
    assertThat(resolver.containsKey("c")).isFalse();
    assertThat(resolver.containsKey(42)).isFalse();
    assertThat(resolver.createResolver(null)).isSameAs(resolver);
  }

  @Test
  public void beansResolverLooksUpBeansByStringKeyOnly() {
    when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {"known"});
    SpringBeansResolverFactory resolver = new SpringBeansResolverFactory(applicationContext);

    assertThat(resolver.get("known")).isEqualTo("bean");
    assertThat(resolver.get(42)).isNull();
  }

  @Test
  public void beansResolverSwallowsOnlyScopeNotActiveFailures() {
    when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[] {"scoped", "broken"});
    when(applicationContext.getBean("scoped")).thenThrow(new org.springframework.beans.factory.support.ScopeNotActiveException("scoped", "request", new IllegalStateException("no request")));
    BeanCreationException failure = new BeanCreationException("broken", "cannot create");
    when(applicationContext.getBean("broken")).thenThrow(failure);
    SpringBeansResolverFactory resolver = new SpringBeansResolverFactory(applicationContext);

    assertThat(resolver.get("scoped")).isNull();
    assertThatThrownBy(() -> resolver.get("broken")).isSameAs(failure);
  }

  @Test
  public void expressionManagerExposesApplicationContextWhenNoBeansGiven() {
    SpringExpressionManager manager = new SpringExpressionManager(applicationContext);

    ELResolver resolver = manager.createElResolver();
    assertThat(resolver).isInstanceOf(CompositeELResolver.class);
    assertThat(resolver.getValue(elContext, null, "known")).isEqualTo("bean");
  }

  @Test
  public void expressionManagerExposesOnlyGivenBeansWhenProvided() {
    java.util.Map<Object, Object> beans = new java.util.HashMap<>();
    beans.put("mine", "value");
    SpringExpressionManager manager = new SpringExpressionManager(applicationContext, beans);

    ELResolver resolver = manager.createElResolver();
    assertThat(resolver.getValue(elContext, null, "mine")).isEqualTo("value");
    assertThat(resolver.getValue(elContext, null, "known")).isNull();
  }
}
