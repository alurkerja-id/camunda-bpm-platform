package org.camunda.templateengines;

import static org.assertj.core.api.Assertions.assertThat;

import javax.script.ScriptEngine;

import org.junit.Test;

public class FreeMarkerScriptEngineFactoryTest {

  protected FreeMarkerScriptEngineFactory factory = new FreeMarkerScriptEngineFactory();

  @Test
  public void shouldExposeMetadata() {
    assertThat(factory.getEngineName()).isEqualTo("freemarker");
    assertThat(factory.getLanguageName()).isEqualTo("freemarker");
    assertThat(factory.getEngineVersion()).isEqualTo(FreeMarkerScriptEngineFactory.VERSION);
    assertThat(factory.getLanguageVersion()).isEqualTo(FreeMarkerScriptEngineFactory.VERSION);
    assertThat(factory.getExtensions()).containsExactly("ftl");
    assertThat(factory.getMimeTypes()).isEmpty();
    assertThat(factory.getNames()).containsExactly("freemarker", "Freemarker", "FreeMarker");
  }

  @Test
  public void shouldResolveParameters() {
    assertThat(factory.getParameter(ScriptEngine.NAME)).isEqualTo("freemarker");
    assertThat(factory.getParameter(ScriptEngine.ENGINE)).isEqualTo("freemarker");
    assertThat(factory.getParameter(ScriptEngine.ENGINE_VERSION)).isEqualTo(FreeMarkerScriptEngineFactory.VERSION);
    assertThat(factory.getParameter(ScriptEngine.LANGUAGE)).isEqualTo("freemarker");
    assertThat(factory.getParameter(ScriptEngine.LANGUAGE_VERSION)).isEqualTo(FreeMarkerScriptEngineFactory.VERSION);
    assertThat(factory.getParameter("THREADING")).isEqualTo("MULTITHREADED");
    assertThat(factory.getParameter("unknown")).isNull();
  }

  @Test
  public void shouldBuildSyntax() {
    assertThat(factory.getMethodCallSyntax("obj", "call", "a", "b")).isEqualTo("${obj.call(a, b)}");
    assertThat(factory.getMethodCallSyntax("obj", "call")).isEqualTo("${obj.call()}");
    assertThat(factory.getOutputStatement("text")).isEqualTo("text");
    assertThat(factory.getProgram("one", "two")).isEqualTo("one\ntwo");
    assertThat(factory.joinStrings(",", null)).isNull();
  }

  @Test
  public void shouldCreateEngineBoundToFactory() {
    assertThat(factory.getScriptEngine().getFactory()).isSameAs(factory);
  }
}
