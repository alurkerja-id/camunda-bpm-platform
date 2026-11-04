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
package org.camunda.bpm.impl.juel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.camunda.bpm.impl.juel.Parser.ExtensionHandler;
import org.camunda.bpm.impl.juel.Parser.ExtensionPoint;
import org.camunda.bpm.impl.juel.Parser.ParseException;
import org.junit.Test;

/**
 * Drives the syntax-extension hooks of {@link Parser}: a scanner that hands out extension tokens
 * for a few extra keywords, and handlers registered at each {@link ExtensionPoint}.
 */
public class ParserExtensionTest {

  // the parser looks handlers up by token identity, so scanner and tests must share instances
  static final Scanner.ExtensionToken xor = new Scanner.ExtensionToken("xor");
  static final Scanner.ExtensionToken xand = new Scanner.ExtensionToken("xand");
  static final Scanner.ExtensionToken xeq = new Scanner.ExtensionToken("xeq");
  static final Scanner.ExtensionToken xcmp = new Scanner.ExtensionToken("xcmp");
  static final Scanner.ExtensionToken xadd = new Scanner.ExtensionToken("xadd");
  static final Scanner.ExtensionToken xmul = new Scanner.ExtensionToken("xmul");
  static final Scanner.ExtensionToken xnot = new Scanner.ExtensionToken("xnot");
  static final Scanner.ExtensionToken xlit = new Scanner.ExtensionToken("xlit");

  /** Hands out an extension token for keywords such as {@code xor}, {@code xadd}, {@code xlit}. */
  static class ExtScanner extends Scanner {
    ExtScanner(String input) {
      super(input);
    }

    @Override
    protected Token keyword(String s) {
      switch (s) {
        case "xor":
          return xor;
        case "xand":
          return xand;
        case "xeq":
          return xeq;
        case "xcmp":
          return xcmp;
        case "xadd":
          return xadd;
        case "xmul":
          return xmul;
        case "xnot":
          return xnot;
        case "xlit":
          return xlit;
        default:
          return super.keyword(s);
      }
    }
  }

  /** Records how often it was called and with how many children, and builds a marker string. */
  static class RecordingHandler extends ExtensionHandler {
    final String marker;
    final List<Integer> childCounts = new ArrayList<>();

    RecordingHandler(ExtensionPoint point, String marker) {
      super(point);
      this.marker = marker;
    }

    @Override
    public AstNode createAstNode(AstNode... children) {
      childCounts.add(children.length);
      return new AstString(marker);
    }
  }

  static class ExtParser extends Parser {
    ExtParser(Builder context, String input) {
      super(context, input);
    }

    @Override
    protected Scanner createScanner(String expression) {
      return new ExtScanner(expression);
    }

    List<FunctionNode> functionNodes() {
      return getFunctions();
    }

    List<IdentifierNode> identifierNodes() {
      return getIdentifiers();
    }

    Scanner.Token currentToken() {
      return getToken();
    }

    void failWith(String expected) throws ParseException {
      fail(expected);
    }
  }


  private RecordingHandler register(Parser parser, Scanner.ExtensionToken token, ExtensionPoint point) {
    RecordingHandler handler = new RecordingHandler(point, token.getImage());
    parser.putExtensionHandler(token, handler);
    return handler;
  }

  private Parser parser(String expression) {
    return new ExtParser(new Builder(), expression);
  }

  private String parse(Parser parser) throws Exception {
    return parser.tree().getRoot().getStructuralId(null);
  }

  @Test
  public void shouldExposeExtensionPointOfHandler() {
    assertThat(new RecordingHandler(ExtensionPoint.ADD, "m").getExtensionPoint()).isEqualTo(ExtensionPoint.ADD);
    assertThat(ExtensionPoint.values()).hasSize(8);
  }

  @Test
  public void shouldCallBinaryHandlerAtEachBinaryExtensionPoint() throws Exception {
    ExtensionPoint[] points = {ExtensionPoint.OR, ExtensionPoint.AND, ExtensionPoint.EQ,
        ExtensionPoint.CMP, ExtensionPoint.ADD, ExtensionPoint.MUL};
    Scanner.ExtensionToken[] tokens = {xor, xand, xeq, xcmp, xadd, xmul};
    for (int i = 0; i < points.length; i++) {
      Parser parser = parser("${a " + tokens[i].getImage() + " b}");
      RecordingHandler handler = register(parser, tokens[i], points[i]);

      String root = parse(parser);

      assertThat(handler.childCounts).as(points[i].name()).containsExactly(2);
      assertThat(root).as(points[i].name()).contains("'" + tokens[i].getImage() + "'");
    }
  }

  @Test
  public void shouldCallUnaryHandlerWithOneChild() throws Exception {
    Parser parser = parser("${xnot a}");
    RecordingHandler handler = register(parser, xnot, ExtensionPoint.UNARY);

    String root = parse(parser);

    assertThat(handler.childCounts).containsExactly(1);
    assertThat(root).contains("'xnot'");
  }

  @Test
  public void shouldCallLiteralHandlerWithoutChildren() throws Exception {
    Parser parser = parser("${xlit}");
    RecordingHandler handler = register(parser, xlit, ExtensionPoint.LITERAL);

    String root = parse(parser);

    assertThat(handler.childCounts).containsExactly(0);
    assertThat(root).contains("'xlit'");
  }

  @Test
  public void shouldIgnoreHandlerRegisteredAtOtherExtensionPointWhenTokenFollowsOperand() throws Exception {
    // an OR-extension token after an AND-level operand is left for the enclosing level, so the
    // handler is invoked exactly once and by the OR level, not by AND/EQ/CMP/ADD/MUL.
    Parser parser = parser("${a xor b}");
    RecordingHandler handler = register(parser, xor, ExtensionPoint.OR);

    parse(parser);

    assertThat(handler.childCounts).containsExactly(2);
  }

  @Test
  public void shouldRejectExtensionTokenWhoseHandlerSitsAtAnotherPoint() {
    // handler registered for ADD but the token appears where only a literal could stand
    Parser parser = parser("${xadd}");
    register(parser, xadd, ExtensionPoint.ADD);

    assertThatThrownBy(() -> parse(parser)).isInstanceOf(ParseException.class);
  }

  @Test
  public void shouldRejectExtensionTokenInUnaryPositionWhoseHandlerIsNotUnary() {
    Parser parser = parser("${xnot a}");
    register(parser, xnot, ExtensionPoint.MUL);

    assertThatThrownBy(() -> parse(parser)).isInstanceOf(ParseException.class);
  }

  @Test
  public void shouldFailOnIntegerLiteralTooLargeForLong() throws Exception {
    Parser parser = parser("${1}");
    parser.tree();

    assertThatThrownBy(() -> parser.parseInteger("99999999999999999999"))
        .isInstanceOf(ParseException.class)
        .hasMessageContaining("INTEGER");
  }

  @Test
  public void shouldFailOnMalformedFloatLiteral() throws Exception {
    Parser parser = parser("${1}");
    parser.tree();

    assertThatThrownBy(() -> parser.parseFloat("not-a-number"))
        .isInstanceOf(ParseException.class)
        .hasMessageContaining("FLOAT");
  }

  @Test
  public void shouldCollectIdentifiersAndFunctionsWhileParsing() throws Exception {
    ExtParser parser = new ExtParser(new Builder(), "${a + fn:f(b)}");

    parser.tree();

    assertThat(parser.identifierNodes()).extracting(IdentifierNode::getName).containsExactly("a", "b");
    assertThat(parser.functionNodes()).extracting(FunctionNode::getName).containsExactly("fn:f");
    assertThat(parser.currentToken().getSymbol()).isEqualTo(Scanner.Symbol.EOF);
  }

  @Test
  public void shouldReportExpectedTextAndEncounteredTokenOnFail() throws Exception {
    ExtParser parser = new ExtParser(new Builder(), "${a}");
    parser.tree();

    assertThatThrownBy(() -> parser.failWith("something else"))
        .isInstanceOf(ParseException.class)
        .hasMessageContaining("something else");
  }
}
