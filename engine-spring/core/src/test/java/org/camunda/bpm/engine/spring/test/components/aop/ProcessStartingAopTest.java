package org.camunda.bpm.engine.spring.test.components.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.aopalliance.intercept.MethodInvocation;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.spring.annotations.BusinessKey;
import org.camunda.bpm.engine.spring.annotations.ProcessVariable;
import org.camunda.bpm.engine.spring.annotations.StartProcess;
import org.camunda.bpm.engine.spring.components.aop.ProcessStartAnnotationBeanPostProcessor;
import org.camunda.bpm.engine.spring.components.aop.ProcessStartingMethodInterceptor;
import org.camunda.bpm.engine.spring.components.aop.ProcessStartingPointcutAdvisor;
import org.camunda.bpm.engine.spring.components.aop.util.MetaAnnotationMatchingPointcut;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.scheduling.annotation.AsyncResult;

/**
 * Covers {@link ProcessStartingMethodInterceptor}, {@link ProcessStartingPointcutAdvisor},
 * {@link ProcessStartAnnotationBeanPostProcessor} and {@link MetaAnnotationMatchingPointcut}.
 */
public class ProcessStartingAopTest {

  protected ProcessEngine processEngine;
  protected RuntimeService runtimeService;
  protected ProcessInstance processInstance;

  @Before
  public void setUp() {
    processEngine = mock(ProcessEngine.class);
    runtimeService = mock(RuntimeService.class);
    processInstance = mock(ProcessInstance.class);
    when(processEngine.getRuntimeService()).thenReturn(runtimeService);
    when(processInstance.getId()).thenReturn("pi-1");
    when(runtimeService.startProcessInstanceByKey(any(String.class), anyMap())).thenReturn(processInstance);
    when(runtimeService.startProcessInstanceByKey(any(String.class), any(String.class), anyMap())).thenReturn(processInstance);
  }

  public static class Starter {

    @StartProcess(processKey = "proc")
    public void returnsVoid(@ProcessVariable("v") String v) {
    }

    @StartProcess(processKey = "proc")
    public ProcessInstance returnsInstance(@ProcessVariable("v") String v) {
      return null;
    }

    @StartProcess(processKey = "proc", returnProcessInstanceId = true)
    public String returnsId(@BusinessKey String key, @ProcessVariable("a") String a, String untagged) {
      return "ignored";
    }

    @StartProcess(processKey = "proc")
    public Future<ProcessInstance> returnsFuture() {
      return null;
    }

    @StartProcess(processKey = "proc")
    public String returnsStringWithoutIdFlag() {
      return "plain";
    }

    @StartProcess(processKey = "")
    public void missingKey() {
    }

    @StartProcess(processKey = "proc")
    public void failing() {
      throw new IllegalStateException("boom");
    }

    public void notAnnotated() {
    }
  }

  protected MethodInvocation invocation(String methodName, Object result, Object... args) throws Throwable {
    Method method = null;
    for (Method m : Starter.class.getMethods()) {
      if (m.getName().equals(methodName)) {
        method = m;
      }
    }
    MethodInvocation invocation = mock(MethodInvocation.class);
    when(invocation.getMethod()).thenReturn(method);
    when(invocation.getArguments()).thenReturn(args);
    when(invocation.proceed()).thenReturn(result);
    return invocation;
  }

  @Test
  public void voidMethodStartsProcessWithVariablesAndReturnsNull() throws Throwable {
    ProcessStartingMethodInterceptor interceptor = new ProcessStartingMethodInterceptor(processEngine);

    Object result = interceptor.invoke(invocation("returnsVoid", null, "value"));

    assertThat(result).isNull();
    Map<String, Object> expected = new HashMap<>();
    expected.put("v", "value");
    verify(runtimeService).startProcessInstanceByKey("proc", expected);
  }

  @Test
  public void instanceReturningMethodGetsProcessInstance() throws Throwable {
    ProcessStartingMethodInterceptor interceptor = new ProcessStartingMethodInterceptor(processEngine);

    assertThat(interceptor.invoke(invocation("returnsInstance", null, "x"))).isSameAs(processInstance);
  }

  @Test
  public void businessKeyIsPassedAndIdReturnedWhenRequested() throws Throwable {
    ProcessStartingMethodInterceptor interceptor = new ProcessStartingMethodInterceptor(processEngine);

    Object result = interceptor.invoke(invocation("returnsId", "ignored", "bk-1", "a", "u"));

    assertThat(result).isEqualTo("pi-1");
    Map<String, Object> expected = new HashMap<>();
    expected.put("a", "a");
    verify(runtimeService).startProcessInstanceByKey("proc", "bk-1", expected);
  }

  @Test
  public void blankBusinessKeyStartsWithoutKey() throws Throwable {
    ProcessStartingMethodInterceptor interceptor = new ProcessStartingMethodInterceptor(processEngine);

    interceptor.invoke(invocation("returnsId", "ignored", "  ", "a", "u"));

    verify(runtimeService, never()).startProcessInstanceByKey(any(String.class), any(String.class), anyMap());
    verify(runtimeService).startProcessInstanceByKey(eq("proc"), anyMap());
  }

  @Test
  public void futureReturningMethodGetsAsyncResult() throws Throwable {
    ProcessStartingMethodInterceptor interceptor = new ProcessStartingMethodInterceptor(processEngine);

    Object result = interceptor.invoke(invocation("returnsFuture", null));

    assertThat(result).isInstanceOf(AsyncResult.class);
    assertThat(((AsyncResult<?>) result).get()).isSameAs(processInstance);
  }

  @Test
  public void otherReturnTypesKeepOriginalResult() throws Throwable {
    ProcessStartingMethodInterceptor interceptor = new ProcessStartingMethodInterceptor(processEngine);

    assertThat(interceptor.invoke(invocation("returnsStringWithoutIdFlag", "plain"))).isEqualTo("plain");
  }

  @Test
  public void missingProcessKeyIsRejected() throws Throwable {
    ProcessStartingMethodInterceptor interceptor = new ProcessStartingMethodInterceptor(processEngine);
    MethodInvocation invocation = invocation("missingKey", null);

    assertThatThrownBy(() -> interceptor.invoke(invocation))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name of process");
  }

  @Test
  public void methodWithoutAnnotationIsRejected() throws Throwable {
    ProcessStartingMethodInterceptor interceptor = new ProcessStartingMethodInterceptor(processEngine);
    MethodInvocation invocation = invocation("notAnnotated", null);

    assertThatThrownBy(() -> interceptor.invoke(invocation))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("@StartProcess");
  }

  @Test
  public void failuresAreWrappedInRuntimeException() throws Throwable {
    ProcessStartingMethodInterceptor interceptor = new ProcessStartingMethodInterceptor(processEngine);
    MethodInvocation invocation = invocation("failing", null);
    when(invocation.proceed()).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> interceptor.invoke(invocation))
        .isInstanceOf(RuntimeException.class).hasCauseInstanceOf(IllegalStateException.class);
    verify(runtimeService, never()).startProcessInstanceByKey(any(String.class), anyMap());
  }

  @Test
  public void advisorMatchesOnlyAnnotatedMethods() throws Exception {
    ProcessStartingPointcutAdvisor advisor = new ProcessStartingPointcutAdvisor(processEngine);

    assertThat(advisor.isPerInstance()).isTrue();
    assertThat(advisor.getAdvice()).isInstanceOf(ProcessStartingMethodInterceptor.class);
    assertThat(advisor.getPointcut().getMethodMatcher()
        .matches(Starter.class.getMethod("returnsVoid", String.class), Starter.class)).isTrue();
    assertThat(advisor.getPointcut().getMethodMatcher()
        .matches(Starter.class.getMethod("notAnnotated"), Starter.class)).isFalse();
  }

  @Test
  public void postProcessorProxiesAnnotatedBeansOnly() throws Exception {
    ProcessStartAnnotationBeanPostProcessor processor = new ProcessStartAnnotationBeanPostProcessor();
    processor.setProcessEngine(processEngine);
    processor.afterPropertiesSet();
    processor.setProxyTargetClass(true);

    Starter starter = new Starter();
    Object proxied = processor.postProcessAfterInitialization(starter, "starter");
    Object plain = new Object();

    assertThat(AopUtils.isAopProxy(proxied)).isTrue();
    assertThat(processor.postProcessBeforeInitialization(plain, "plain")).isSameAs(plain);
    assertThat(processor.postProcessAfterInitialization(plain, "plain")).isSameAs(plain);

    ((Starter) proxied).returnsVoid("v");
    verify(runtimeService).startProcessInstanceByKey(eq("proc"), anyMap());
  }

  @Test
  public void postProcessorAddsAdvisorToExistingProxyAndSkipsInfrastructure() throws Exception {
    ProcessStartAnnotationBeanPostProcessor processor = new ProcessStartAnnotationBeanPostProcessor();
    processor.setProcessEngine(processEngine);
    processor.afterPropertiesSet();

    ProxyFactory factory = new ProxyFactory(new Starter());
    factory.setProxyTargetClass(true);
    Object existing = factory.getProxy();

    assertThat(processor.postProcessAfterInitialization(existing, "starter")).isSameAs(existing);
    assertThat(((org.springframework.aop.framework.Advised) existing).getAdvisors()).hasSize(1);

    Object infrastructure = mock(org.springframework.aop.framework.AopInfrastructureBean.class);
    assertThat(processor.postProcessAfterInitialization(infrastructure, "infra")).isSameAs(infrastructure);
  }

  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
  @interface Marker {
  }

  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
  @Marker
  @interface MetaMarker {
  }

  @Marker
  static class MarkedClass {
    @MetaMarker
    public void meta() {
    }

    public void plain() {
    }
  }

  @Test
  public void pointcutRequiresAtLeastOneAnnotationType() {
    assertThatThrownBy(() -> new MetaAnnotationMatchingPointcut(null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void pointcutMatchesClassAndMethodAnnotationsIncludingMetaAnnotations() throws Exception {
    MetaAnnotationMatchingPointcut methodOnly = new MetaAnnotationMatchingPointcut(null, Marker.class);
    MetaAnnotationMatchingPointcut both = new MetaAnnotationMatchingPointcut(Marker.class, Marker.class);
    MetaAnnotationMatchingPointcut classOnly = new MetaAnnotationMatchingPointcut(Marker.class, null);
    MetaAnnotationMatchingPointcut inheritedClass = new MetaAnnotationMatchingPointcut(Marker.class, true);

    Method meta = MarkedClass.class.getMethod("meta");
    Method plain = MarkedClass.class.getMethod("plain");

    assertThat(methodOnly.getClassFilter().matches(Object.class)).isTrue();
    assertThat(methodOnly.getMethodMatcher().matches(meta, MarkedClass.class)).isTrue();
    assertThat(methodOnly.getMethodMatcher().matches(plain, MarkedClass.class)).isFalse();
    assertThat(both.getClassFilter().matches(MarkedClass.class)).isTrue();
    assertThat(both.getClassFilter().matches(Object.class)).isFalse();
    assertThat(classOnly.getMethodMatcher().matches(plain, MarkedClass.class)).isTrue();
    assertThat(inheritedClass.getClassFilter().matches(MarkedClass.class)).isTrue();
    assertThat(inheritedClass.getMethodMatcher().matches(plain, MarkedClass.class)).isTrue();
  }
}
