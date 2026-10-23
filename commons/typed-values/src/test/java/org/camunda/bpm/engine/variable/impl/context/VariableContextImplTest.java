package org.camunda.bpm.engine.variable.impl.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.context.VariableContext;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.Test;

/**
 * Covers {@link SingleVariableContext}, {@link CompositeVariableContext} and {@link EmptyVariableContext}.
 */
public class VariableContextImplTest {

  protected TypedValue a = Variables.stringValue("a");
  protected TypedValue b = Variables.integerValue(2);

  @Test
  public void singleContextResolvesOnlyItsOwnName() {
    VariableContext ctx = SingleVariableContext.singleVariable("x", a);

    assertThat(ctx.containsVariable("x")).isTrue();
    assertThat(ctx.containsVariable("y")).isFalse();
    assertThat(ctx.resolve("x")).isSameAs(a);
    assertThat(ctx.resolve("y")).isNull();
    assertThat(ctx.keySet()).containsExactly("x");
  }

  @Test
  public void singleContextWithNullNameMatchesOnlyNullName() {
    VariableContext ctx = new SingleVariableContext(null, a);

    assertThat(ctx.containsVariable(null)).isTrue();
    assertThat(ctx.containsVariable("x")).isFalse();
    assertThat(ctx.resolve(null)).isSameAs(a);
    assertThat(ctx.resolve("x")).isNull();
  }

  @Test
  public void compositeContextResolvesFromFirstMatchingDelegate() {
    VariableContext first = SingleVariableContext.singleVariable("x", a);
    VariableContext second = SingleVariableContext.singleVariable("x", b);
    VariableContext third = SingleVariableContext.singleVariable("y", b);

    CompositeVariableContext ctx = CompositeVariableContext.compose(first, second, third);

    assertThat(ctx.resolve("x")).isSameAs(a);
    assertThat(ctx.resolve("y")).isSameAs(b);
    assertThat(ctx.resolve("z")).isNull();
  }

  @Test
  public void compositeContextContainsAndKeySetSpanAllDelegates() {
    CompositeVariableContext ctx = new CompositeVariableContext(new VariableContext[] {
        SingleVariableContext.singleVariable("x", a),
        SingleVariableContext.singleVariable("y", b)});

    assertThat(ctx.containsVariable("x")).isTrue();
    assertThat(ctx.containsVariable("y")).isTrue();
    assertThat(ctx.containsVariable("z")).isFalse();
    assertThat(ctx.keySet()).containsExactlyInAnyOrder("x", "y");
  }

  @Test
  public void emptyCompositeContextResolvesNothing() {
    CompositeVariableContext ctx = CompositeVariableContext.compose();

    assertThat(ctx.resolve("x")).isNull();
    assertThat(ctx.containsVariable("x")).isFalse();
    assertThat(ctx.keySet()).isEmpty();
  }

  @Test
  public void emptyContextHasNoVariables() {
    VariableContext ctx = Variables.emptyVariableContext();

    assertThat(ctx.resolve("x")).isNull();
    assertThat(ctx.containsVariable("x")).isFalse();
    assertThat(ctx.keySet()).isEmpty();
  }
}
