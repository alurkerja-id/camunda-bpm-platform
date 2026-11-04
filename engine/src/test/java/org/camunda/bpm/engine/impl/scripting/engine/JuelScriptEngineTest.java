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
package org.camunda.bpm.engine.impl.scripting.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import org.camunda.bpm.impl.juel.jakarta.el.ELException;
import org.junit.Before;
import org.junit.Test;

/**
 * Exercises the JSR-223 JUEL engine directly: evaluation from strings and readers, variable and
 * function resolution through the script context, and the built-in {@code lang:import} helper.
 */
public class JuelScriptEngineTest {

  /** Static helpers imported through lang:import. */
  public static class Helpers {
    public static int twice(int value) {
      return value * 2;
    }

    public int notStatic() {
      return 0;
    }

    static int notPublic() {
      return 0;
    }
  }

  protected JuelScriptEngine engine;
  protected ScriptContext context;

  @Before
  public void setUp() {
    engine = new JuelScriptEngine();
    context = new SimpleScriptContext();
    context.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
  }

  @Test
  public void shouldCreateFactoryLazilyAndKeepIt() {
    assertThat(engine.getFactory()).isInstanceOf(JuelScriptEngineFactory.class);
    assertThat(engine.getFactory()).isSameAs(engine.getFactory());
  }

  @Test
  public void shouldUseTheFactoryItWasCreatedWith() {
    JuelScriptEngineFactory factory = new JuelScriptEngineFactory();

    assertThat(new JuelScriptEngine(factory).getFactory()).isSameAs(factory);
  }

  @Test
  public void shouldCreateEmptySimpleBindings() {
    Bindings bindings = engine.createBindings();

    assertThat(bindings).isEmpty();
    assertThat(engine.createBindings()).isNotSameAs(bindings);
  }

  @Test
  public void shouldEvaluateExpressionAgainstContextVariables() throws Exception {
    context.setAttribute("a", 20, ScriptContext.ENGINE_SCOPE);
    context.setAttribute("b", 22, ScriptContext.ENGINE_SCOPE);

    assertThat(engine.eval("${a + b}", context)).isEqualTo(42L);
  }

  @Test
  public void shouldEvaluateLiteralTextWithoutExpression() throws Exception {
    assertThat(engine.eval("just text", context)).isEqualTo("just text");
  }

  @Test
  public void shouldResolveVariableFromGlobalScope() throws Exception {
    context.setBindings(engine.createBindings(), ScriptContext.GLOBAL_SCOPE);
    context.setAttribute("greeting", "hi", ScriptContext.GLOBAL_SCOPE);

    assertThat(engine.eval("${greeting}", context)).isEqualTo("hi");
  }

  @Test
  public void shouldEvaluateFromReader() throws Exception {
    context.setAttribute("x", 3, ScriptContext.ENGINE_SCOPE);

    assertThat(engine.eval(new StringReader("${x * 2}"), context)).isEqualTo(6L);
  }

  @Test
  public void shouldReadReaderLargerThanOneBuffer() throws Exception {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 20000; i++) {
      text.append('z');
    }

    assertThat(engine.eval(new StringReader(text.toString()), context)).isEqualTo(text.toString());
  }

  @Test
  public void shouldWrapReaderFailureInScriptException() {
    Reader failing = new Reader() {
      @Override
      public int read(char[] cbuf, int off, int len) throws IOException {
        throw new IOException("boom");
      }

      @Override
      public void close() {
      }
    };

    assertThatThrownBy(() -> engine.eval(failing, context))
        .isInstanceOf(ScriptException.class)
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  public void shouldWrapParseErrorInScriptException() {
    assertThatThrownBy(() -> engine.eval("${1 +}", context))
        .isInstanceOf(ScriptException.class)
        .hasCauseInstanceOf(ELException.class);
  }

  @Test
  public void shouldWrapEvaluationErrorInScriptException() {
    assertThatThrownBy(() -> engine.eval("${missing.property}", context))
        .isInstanceOf(ScriptException.class)
        .hasCauseInstanceOf(ELException.class);
  }

  @Test
  public void shouldReuseElContextStoredInScriptContext() throws Exception {
    engine.eval("${1}", context);
    Object elContext = context.getAttribute("elcontext");

    engine.eval("${2}", context);

    assertThat(elContext).isNotNull();
    assertThat(context.getAttribute("elcontext")).isSameAs(elContext);
  }

  @Test
  public void shouldRegisterBuiltInsInScriptContext() throws Exception {
    engine.eval("${1}", context);

    assertThat(context.getAttribute("context")).isSameAs(context);
    // finding: JuelScriptEngine declares no print(Object), so getPrintMethod() falls into its
    // "will never occur" catch and out:print is registered as null
    assertThat(context.getAttribute("out:print")).isNull();
    assertThat(context.getAttribute("lang:import")).isInstanceOf(Method.class);
  }

  @Test
  public void shouldImportStaticPublicMethodsOfClass() throws Exception {
    JuelScriptEngine.importFunctions(context, "h", Helpers.class);

    assertThat(context.getAttribute("h:twice")).isInstanceOf(Method.class);
    assertThat(context.getAttribute("h:notStatic")).isNull();
    assertThat(context.getAttribute("h:notPublic")).isNull();
    assertThat(engine.eval("${h:twice(21)}", context)).isEqualTo(42);
  }

  @Test
  public void shouldImportFunctionsByClassName() throws Exception {
    JuelScriptEngine.importFunctions(context, "h", Helpers.class.getName());

    assertThat(engine.eval("${h:twice(4)}", context)).isEqualTo(8);
  }

  @Test
  public void shouldImportFunctionsThroughLangImportExpression() throws Exception {
    context.setAttribute("helpers", Helpers.class, ScriptContext.ENGINE_SCOPE);

    engine.eval("${lang:import(context, 'h', helpers)}", context);

    assertThat(context.getAttribute("h:twice")).isInstanceOf(Method.class);
  }

  @Test
  public void shouldRejectImportOfUnknownClassName() {
    assertThatThrownBy(() -> JuelScriptEngine.importFunctions(context, "h", "does.not.Exist"))
        .isInstanceOf(ELException.class);
  }

  @Test
  public void shouldRejectImportOfNeitherClassNorName() {
    assertThatThrownBy(() -> JuelScriptEngine.importFunctions(context, "h", 42))
        .isInstanceOf(ELException.class)
        .hasMessageContaining("Class or class name is missing");
  }

  @Test
  public void shouldFailOnFunctionThatIsNotAMethod() {
    context.setAttribute("x:f", "not a method", ScriptContext.ENGINE_SCOPE);

    assertThatThrownBy(() -> engine.eval("${x:f()}", context)).isInstanceOf(ScriptException.class);
  }

  @Test
  public void shouldFailOnUnknownFunction() {
    assertThatThrownBy(() -> engine.eval("${nope:f()}", context)).isInstanceOf(ScriptException.class);
  }

  @Test
  public void shouldKeepValueExpressionStoredAsVariable() throws Exception {
    engine.eval("${1}", context);
    context.setAttribute("n", 5, ScriptContext.ENGINE_SCOPE);
    Object elContext = context.getAttribute("elcontext");
    org.camunda.bpm.impl.juel.jakarta.el.VariableMapper mapper =
        ((org.camunda.bpm.impl.juel.jakarta.el.ELContext) elContext).getVariableMapper();

    assertThat(mapper.resolveVariable("n").getValue((org.camunda.bpm.impl.juel.jakarta.el.ELContext) elContext)).isEqualTo(5);
    assertThat(mapper.resolveVariable("undefined")).isNull();

    org.camunda.bpm.impl.juel.jakarta.el.ValueExpression replacement = mapper.resolveVariable("n");
    assertThat(mapper.setVariable("n", replacement)).isNotNull();
    assertThat(context.getAttribute("n")).isSameAs(replacement);
    assertThat(mapper.resolveVariable("n")).isSameAs(replacement);
  }
}
