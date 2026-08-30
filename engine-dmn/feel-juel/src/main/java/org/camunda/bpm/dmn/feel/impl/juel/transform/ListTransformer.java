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
package org.camunda.bpm.dmn.feel.impl.juel.transform;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.dmn.feel.impl.juel.FeelEngineLogger;
import org.camunda.bpm.dmn.feel.impl.juel.FeelLogger;

public class ListTransformer implements FeelToJuelTransformer {

  public static final FeelEngineLogger LOG = FeelLogger.ENGINE_LOGGER;
  /**
   * The regex that splitExpression used to run. Kept because it is public API, but no longer used:
   * see splitExpression for why.
   *
   * @deprecated splitting is done by scanning the expression, this constant is here for
   *             compatibility only
   */
  @Deprecated
  public static final String COMMA_SEPARATOR_REGEX = ",(?=([^\"]*\"[^\"]*\")*[^\"]*$)";

  public boolean canTransform(String feelExpression) {
    return splitExpression(feelExpression).size() > 1;
  }

  public String transform(FeelToJuelTransform transform, String feelExpression, String inputName) {
    List<String> juelExpressions = transformExpressions(transform, feelExpression, inputName);
    return joinExpressions(juelExpressions);
  }

  protected List<String> collectExpressions(String feelExpression) {
    return splitExpression(feelExpression);
  }

  /**
   * Splits on commas that are not enclosed in quotes. This used to be a single regex with a
   * look-ahead, {@code ,(?=([^"]*"[^"]*")*[^"]*$)}, whose nested quantifier drove the matcher into
   * deep recursion: a perfectly valid list of 5000 quoted entries ended in a StackOverflowError,
   * and the cost grew far faster than the input.
   *
   * <p>The look-ahead accepted a comma exactly when the rest of the expression held an even number
   * of quotes, so that is what this counts - in one pass, without recursion.
   */
  private List<String> splitExpression(String feelExpression) {
    int totalQuotes = 0;
    for (int i = 0; i < feelExpression.length(); i++) {
      if (feelExpression.charAt(i) == '"') {
        totalQuotes++;
      }
    }

    List<String> parts = new ArrayList<>();
    int quotesSeen = 0;
    int partStart = 0;
    for (int i = 0; i < feelExpression.length(); i++) {
      char character = feelExpression.charAt(i);
      if (character == '"') {
        quotesSeen++;
      }
      // quotes after this position are totalQuotes - quotesSeen, and the look-ahead required that
      // count to be even
      else if (character == ',' && (totalQuotes - quotesSeen) % 2 == 0) {
        parts.add(feelExpression.substring(partStart, i));
        partStart = i + 1;
      }
    }
    parts.add(feelExpression.substring(partStart));
    return parts;
  }

  protected List<String> transformExpressions(FeelToJuelTransform transform, String feelExpression, String inputName) {
    List<String> expressions = collectExpressions(feelExpression);
    List<String> juelExpressions = new ArrayList<String>();
    for (String expression : expressions) {
      if (!expression.trim().isEmpty()) {
        String juelExpression = transform.transformSimplePositiveUnaryTest(expression, inputName);
        juelExpressions.add(juelExpression);
      }
      else {
        throw LOG.invalidListExpression(feelExpression);
      }
    }
    return juelExpressions;
  }

  protected String joinExpressions(List<String> juelExpressions) {
    StringBuilder builder = new StringBuilder();
    builder.append("(").append(juelExpressions.get(0)).append(")");
    for (int i = 1; i < juelExpressions.size(); i++) {
      builder.append(" || (").append(juelExpressions.get(i)).append(")");
    }
    return builder.toString();
  }

}
