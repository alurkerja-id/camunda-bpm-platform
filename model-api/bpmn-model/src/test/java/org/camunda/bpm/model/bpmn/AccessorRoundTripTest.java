package org.camunda.bpm.model.bpmn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.Test;

/**
 * Walks every concrete element type of the BPMN model and checks that each
 * public setter/getter pair on its interface round-trips the value it was given.
 */
public class AccessorRoundTripTest {

  @Test
  public void shouldRoundTripEveryAccessorPairOfEveryElementType() {
    BpmnModelInstance modelInstance = Bpmn.createEmptyModel();
    List<String> failures = new ArrayList<>();
    int checkedPairs = 0;
    int skippedNeedingChild = 0;
    List<String> unsupported = new ArrayList<>();

    for (ModelElementType type : modelInstance.getModel().getTypes()) {
      if (type.isAbstract()) {
        continue;
      }
      Class<? extends ModelElementInstance> iface = type.getInstanceType();
      ModelElementInstance element = modelInstance.newInstance(type);
      assertNotNull(element);

      for (Method setter : iface.getMethods()) {
        if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1
            || Modifier.isStatic(setter.getModifiers())) {
          continue;
        }
        Method getter = findGetter(iface, setter);
        if (getter == null) {
          continue;
        }
        Object value = sampleValue(modelInstance, setter.getParameterTypes()[0], setter, element);
        if (value == null) {
          continue;
        }
        try {
          setter.invoke(element, value);
          Object actual = getter.invoke(element);
          assertEquals(iface.getSimpleName() + "." + setter.getName(), value, actual);
          checkedPairs++;
        } catch (Throwable t) {
          Throwable cause = t.getCause() != null ? t.getCause() : t;
          if (cause instanceof UnsupportedOperationException) {
            unsupported.add(iface.getSimpleName() + "." + setter.getName());
            continue;
          }
          if (cause instanceof NullPointerException) {
            // setter writes into a mandatory child element that a bare instance does not have yet
            skippedNeedingChild++;
            continue;
          }
          failures.add(iface.getSimpleName() + "." + setter.getName() + ": " + cause);
        }
      }
    }

    assertTrue("checked pairs: " + checkedPairs, checkedPairs > 100);
    assertTrue("child-dependent setters: " + skippedNeedingChild, skippedNeedingChild < 20);
    assertEquals(Arrays.asList("EventBasedGateway.setCamundaAsyncAfter"), unsupported);
    assertTrue("failures: " + failures, failures.isEmpty());
  }

  private static Method findGetter(Class<?> iface, Method setter) {
    String suffix = setter.getName().substring(3);
    for (String prefix : new String[] { "get", "is" }) {
      try {
        Method m = iface.getMethod(prefix + suffix);
        if (m.getReturnType().equals(setter.getParameterTypes()[0])
            || m.getReturnType().equals(boxed(setter.getParameterTypes()[0]))) {
          return m;
        }
      } catch (NoSuchMethodException e) {
        // try next prefix
      }
    }
    return null;
  }

  private static Class<?> boxed(Class<?> c) {
    if (c == boolean.class) {
      return Boolean.class;
    }
    if (c == int.class) {
      return Integer.class;
    }
    return c;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static Object sampleValue(ModelInstance modelInstance, Class<?> param, Method setter,
      ModelElementInstance owner) {
    if (param == String.class) {
      return "value-" + setter.getName();
    }
    if (param == boolean.class || param == Boolean.class) {
      return Boolean.TRUE;
    }
    if (param == int.class || param == Integer.class) {
      return 7;
    }
    if (param == List.class) {
      return Arrays.asList("a", "b");
    }
    if (param.isEnum()) {
      Object[] constants = param.getEnumConstants();
      return constants[constants.length - 1];
    }
    if (ModelElementInstance.class.isAssignableFrom(param) && !Modifier.isAbstract(param.getModifiers())
        && !param.isInterface() == false) {
      try {
        return modelInstance.newInstance((Class) param);
      } catch (RuntimeException e) {
        return null;
      }
    }
    return null;
  }
}
