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
package org.camunda.bpm.engine.impl.test;

import junit.framework.AssertionFailedError;

import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.model.bpmn.Bpmn;

/**
 * JUnit 3 test cases used by {@link AbstractProcessEngineTestCaseTest} to drive the base class
 * programmatically. Kept in a class whose name does not contain "Test" so that the engine surefire
 * include pattern does not pick these cases up and run them as real tests (some fail on purpose).
 */
public final class EngineCaseProbes {

  private EngineCaseProbes() {
  }

  /** Test cases executed programmatically so the failure branches of runBare can be observed. */
  public static class Probe extends PluggableProcessEngineTestCase {
    static boolean bodyRan;

    public Probe(String name) {
      super();
      setName(name);
    }

    public void testPasses() {
      bodyRan = true;
    }

    public void testAssertionFails() {
      throw new AssertionFailedError("deliberate assertion failure");
    }

    public void testErrors() {
      throw new IllegalStateException("deliberate error");
    }

    public void testLeavesIdentityAndTenantCheckDirty() {
      identityService.setAuthenticatedUserId("someone");
      processEngineConfiguration.setTenantCheckEnabled(false);
      bodyRan = true;
    }

    public void testDeploysWithoutCleaning() {
      deployment(Bpmn.createExecutableProcess("probeProcess").startEvent().userTask("task").endEvent().done());
      bodyRan = true;
    }

    @RequiredHistoryLevel("none")
    public void testWithSatisfiedHistoryLevel() {
      bodyRan = true;
    }

    @RequiredDatabase(includes = "no-such-database")
    public void testWithUnsatisfiedDatabase() {
      bodyRan = true;
    }
  }

  /** Engine test case whose job query always fails. */
  public static class JobsQueryFails extends PluggableProcessEngineTestCase {
    @Override
    public boolean areJobsAvailable() {
      throw new IllegalStateException("table locked");
    }
  }

  /** Engine test case that always reports available jobs. */
  public static class JobsAlwaysAvailable extends PluggableProcessEngineTestCase {
    @Override
    public boolean areJobsAvailable() {
      return true;
    }
  }
}
