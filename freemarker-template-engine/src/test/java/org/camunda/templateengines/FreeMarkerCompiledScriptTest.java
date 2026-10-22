package org.camunda.templateengines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.junit.Test;

public class FreeMarkerCompiledScriptTest {

  protected FreeMarkerScriptEngine engine = new FreeMarkerScriptEngine();

  @Test
  public void shouldCompileFromStringAndEvaluateRepeatedly() throws ScriptException {
    CompiledScript script = ((Compilable) engine).compile("Hello ${name}!");
    Bindings bindings = new SimpleBindings();
    bindings.put("name", "a");
    assertThat(script.eval(bindings)).isEqualTo("Hello a!");
    bindings.put("name", "b");
    assertThat(script.eval(bindings)).isEqualTo("Hello b!");
    assertThat(script.getEngine()).isSameAs(engine);
  }

  @Test
  public void shouldCompileFromReader() throws ScriptException {
    CompiledScript script = engine.compile(new StringReader("${x}"));
    Bindings bindings = engine.createBindings();
    bindings.put("x", 5);
    assertThat(script.eval(bindings)).isEqualTo("5");
  }

  @Test
  public void shouldSupportAllConstructors() throws ScriptException {
    ScriptEngine e = engine;
    freemarker.template.Configuration cfg = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_29);
    assertThat(new FreeMarkerCompiledScript(e, "t1", cfg).eval(new SimpleBindings())).isEqualTo("t1");
    assertThat(new FreeMarkerCompiledScript(e, new StringReader("t2"), cfg).eval(new SimpleBindings())).isEqualTo("t2");
    assertThat(new FreeMarkerCompiledScript(e, "file.ftl", "t3", cfg).eval(new SimpleBindings())).isEqualTo("t3");
    assertThat(new FreeMarkerCompiledScript(e, "file.ftl", new StringReader("t4"), cfg).template.getName()).isEqualTo("file.ftl");
  }

  @Test
  public void shouldFailOnInvalidTemplateAtCompileTime() {
    assertThatThrownBy(() -> engine.compile("${}")).isInstanceOf(ScriptException.class);
  }

  @Test
  public void shouldFailOnRuntimeError() throws ScriptException {
    CompiledScript script = engine.compile("${missing}");
    assertThatThrownBy(() -> script.eval(new SimpleBindings())).isInstanceOf(ScriptException.class);
  }

  @Test
  public void shouldEvaluateWithContextEngineScope() throws ScriptException {
    CompiledScript script = engine.compile("${v}");
    javax.script.SimpleScriptContext ctx = new javax.script.SimpleScriptContext();
    ctx.setBindings(new SimpleBindings(java.util.Collections.singletonMap("v", "ctx")), ScriptContext.ENGINE_SCOPE);
    assertThat(script.eval(ctx)).isEqualTo("ctx");
  }
}
