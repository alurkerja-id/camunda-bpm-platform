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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.AssertionFailedError;
import junit.framework.TestResult;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

/**
 * Exercises the (JUnit 3 style) base class through a concrete subclass running against the cached test engine.
 */
public class AbstractProcessEngineTestCaseTest extends PluggableProcessEngineTestCase {

  private static final String CLASSPATH_PROCESS = "org/camunda/bpm/engine/impl/test/TestHelperCoverage.bpmn20.xml";

  private static BpmnModelInstance asyncProcess(String key) {
    return Bpmn.createExecutableProcess(key).startEvent().endEvent().camundaAsyncBefore().done();
  }

  private static BpmnModelInstance userTaskProcess(String key) {
    return Bpmn.createExecutableProcess(key).startEvent().userTask("task").endEvent().done();
  }

  // -- deployment helpers --------------------------------------------------------------------

  public void testDeploymentFromModelInstancesIsTrackedAndCleanedUp() {
    String id = deployment(userTaskProcess("modelA"), userTaskProcess("modelB"));

    assertEquals(id, deploymentId);
    assertTrue(deploymentIds.contains(id));
    assertEquals(2, repositoryService.createProcessDefinitionQuery().deploymentId(id).count());
  }

  public void testDeploymentFromClasspathResources() {
    String id = deployment(CLASSPATH_PROCESS);

    assertEquals(1, repositoryService.createProcessDefinitionQuery().deploymentId(id).processDefinitionKey("explicitRes").count());
  }

  public void testDeploymentForTenantWithModelInstances() {
    String id = deploymentForTenant("tenantA", userTaskProcess("tenantModel"));

    assertEquals("tenantA", repositoryService.createDeploymentQuery().deploymentId(id).singleResult().getTenantId());
  }

  public void testDeploymentForTenantWithResources() {
    String id = deploymentForTenant("tenantB", CLASSPATH_PROCESS);

    assertEquals("tenantB", repositoryService.createDeploymentQuery().deploymentId(id).singleResult().getTenantId());
  }

  public void testDeploymentForTenantWithResourceAndModel() {
    String id = deploymentForTenant("tenantC", CLASSPATH_PROCESS, userTaskProcess("extraModel"));

    assertEquals("tenantC", repositoryService.createDeploymentQuery().deploymentId(id).singleResult().getTenantId());
    assertEquals(2, repositoryService.createProcessDefinitionQuery().deploymentId(id).count());
  }

  @Deployment(resources = CLASSPATH_PROCESS)
  public void testAnnotationDeploymentIsAvailableInTest() {
    assertNotNull(deploymentId);
    assertEquals(1, repositoryService.createProcessDefinitionQuery().processDefinitionKey("explicitRes").count());
  }

  // -- assertions ------------------------------------------------------------------------------

  public void testAssertProcessEndedAndNotEnded() {
    deployment(userTaskProcess("assertProcess"));
    ProcessInstance instance = runtimeService.startProcessInstanceByKey("assertProcess");

    assertProcessNotEnded(instance.getId());
    try {
      assertProcessEnded(instance.getId());
      fail("expected AssertionFailedError");
    } catch (AssertionFailedError e) {
      assertEquals("Expected finished process instance '" + instance.getId() + "' but it was still in the db", e.getMessage());
    }

    runtimeService.deleteProcessInstance(instance.getId(), "done");

    assertProcessEnded(instance.getId());
    try {
      assertProcessNotEnded(instance.getId());
      fail("expected AssertionFailedError");
    } catch (AssertionFailedError e) {
      assertEquals("Expected process instance '" + instance.getId() + "' to be still active but it was not in the db", e.getMessage());
    }
  }

  public void testAssertCaseEnded() {
    assertCaseEnded("no-such-case-instance");
  }

  // -- job helpers -----------------------------------------------------------------------------

  public void testExecuteAvailableJobsRunsEverythingRecursively() {
    deployment(asyncProcess("asyncA"));
    runtimeService.startProcessInstanceByKey("asyncA");
    assertTrue(areJobsAvailable());

    executeAvailableJobs();

    assertFalse(areJobsAvailable());
    assertEquals(0, runtimeService.createProcessInstanceQuery().count());
  }

  public void testExecuteAvailableJobsWithExpectedCount() {
    deployment(asyncProcess("asyncB"));
    runtimeService.startProcessInstanceByKey("asyncB");
    runtimeService.startProcessInstanceByKey("asyncB");

    executeAvailableJobs(2);

    assertFalse(areJobsAvailable());
  }

  public void testExecuteAvailableJobsFailsWhenFewerJobsRanThanExpected() {
    deployment(asyncProcess("asyncC"));
    runtimeService.startProcessInstanceByKey("asyncC");

    try {
      executeAvailableJobs(3);
      fail("expected AssertionFailedError");
    } catch (AssertionFailedError e) {
      assertEquals("executed less jobs than expected. expected <3> actual <1>", e.getMessage());
    }
  }

  public void testExecuteAvailableJobsFailsWhenMoreJobsRanThanExpected() {
    deployment(asyncProcess("asyncD"));
    runtimeService.startProcessInstanceByKey("asyncD");

    try {
      executeAvailableJobs(0);
      fail("expected AssertionFailedError");
    } catch (AssertionFailedError e) {
      assertEquals("executed more jobs than expected. expected <0> actual <1>", e.getMessage());
    }
  }

  public void testExecuteAvailableJobsNonRecursive() {
    deployment(Bpmn.createExecutableProcess("asyncE").startEvent().camundaAsyncBefore().endEvent().camundaAsyncBefore().done());
    runtimeService.startProcessInstanceByKey("asyncE");

    executeAvailableJobs(false);

    assertTrue("the second asynchronous continuation must be left for the next call", areJobsAvailable());
    executeAvailableJobs();
    assertFalse(areJobsAvailable());
  }

  public void testAreJobsAvailableIgnoresSuspendedAndExhaustedJobs() {
    deployment(asyncProcess("asyncF"));
    ProcessInstance instance = runtimeService.startProcessInstanceByKey("asyncF");
    assertTrue(areJobsAvailable());

    runtimeService.suspendProcessInstanceById(instance.getId());

    assertFalse(areJobsAvailable());
    runtimeService.activateProcessInstanceById(instance.getId());
    managementService.setJobRetries(managementService.createJobQuery().singleResult().getId(), 0);
    assertFalse(areJobsAvailable());
  }

  public void testJobWithFutureDueDateIsNotAvailable() {
    deployment(Bpmn.createExecutableProcess("timerProc").startEvent().intermediateCatchEvent().timerWithDuration("PT1H").endEvent().done());
    runtimeService.startProcessInstanceByKey("timerProc");

    assertFalse(areJobsAvailable());
  }

  public void testAreJobsAvailableIgnoringErrorsKeepsPreviousResult() {
    AbstractProcessEngineTestCase failing = new EngineCaseProbes.JobsQueryFails();

    assertTrue(failing.areJobsAvailableIgnoringErrors(true));
    assertFalse(failing.areJobsAvailableIgnoringErrors(false));
    assertTrue(areJobsAvailableIgnoringErrors(false) == areJobsAvailable());
  }

  public void testWaitForJobExecutorToProcessAllJobs() {
    deployment(asyncProcess("waitA"));
    runtimeService.startProcessInstanceByKey("waitA");

    waitForJobExecutorToProcessAllJobs(20000);

    assertFalse(areJobsAvailable());
    assertEquals(0, runtimeService.createProcessInstanceQuery().count());
  }

  @SuppressWarnings("deprecation")
  public void testWaitForJobExecutorToProcessAllJobsLegacyOverload() {
    deployment(asyncProcess("waitB"));
    runtimeService.startProcessInstanceByKey("waitB");

    waitForJobExecutorToProcessAllJobs(20000, 100);

    assertFalse(areJobsAvailable());
  }

  public void testWaitForJobExecutorToProcessAllJobsTimesOut() {
    AbstractProcessEngineTestCase neverDone = new EngineCaseProbes.JobsAlwaysAvailable();
    neverDone.initializeProcessEngine();
    neverDone.initializeServices();
    int waitTime = processEngineConfiguration.getJobExecutor().getWaitTimeInMillis();
    processEngineConfiguration.getJobExecutor().setWaitTimeInMillis(50);
    try {
      neverDone.waitForJobExecutorToProcessAllJobs(10);
      fail("expected ProcessEngineException");
    } catch (ProcessEngineException e) {
      assertEquals("time limit of 100 was exceeded", e.getMessage());
    } finally {
      processEngineConfiguration.getJobExecutor().setWaitTimeInMillis(waitTime);
      Thread.interrupted();
    }
  }

  public void testWaitForJobExecutorOnCondition() {
    deployment(asyncProcess("condA"));
    runtimeService.startProcessInstanceByKey("condA");

    waitForJobExecutorOnCondition(20000, () -> runtimeService.createProcessInstanceQuery().count() == 0);

    assertEquals(0, runtimeService.createProcessInstanceQuery().count());
  }

  @SuppressWarnings("deprecation")
  public void testWaitForJobExecutorOnConditionLegacyOverload() {
    waitForJobExecutorOnCondition(20000, 100, () -> true);
  }

  public void testWaitForJobExecutorOnConditionTimesOut() {
    int waitTime = processEngineConfiguration.getJobExecutor().getWaitTimeInMillis();
    processEngineConfiguration.getJobExecutor().setWaitTimeInMillis(50);
    try {
      waitForJobExecutorOnCondition(10, () -> false);
      fail("expected ProcessEngineException");
    } catch (ProcessEngineException e) {
      assertEquals("time limit of 100 was exceeded", e.getMessage());
    } finally {
      processEngineConfiguration.getJobExecutor().setWaitTimeInMillis(waitTime);
      Thread.interrupted();
    }
  }

  public void testWaitForJobExecutorOnConditionWrapsConditionFailure() {
    try {
      waitForJobExecutorOnCondition(20000, () -> {
        throw new IllegalStateException("condition broke");
      });
      fail("expected ProcessEngineException");
    } catch (ProcessEngineException e) {
      assertEquals("Exception while waiting on condition: condition broke", e.getMessage());
    }
  }

  // -- activity instances, authorization --------------------------------------------------------

  public void testGetInstancesForActivityId() {
    deployment(Bpmn.createExecutableProcess("tree").startEvent().userTask("task").endEvent().done());
    ProcessInstance instance = runtimeService.startProcessInstanceByKey("tree");
    ActivityInstance tree = runtimeService.getActivityInstance(instance.getId());

    List<ActivityInstance> found = getInstancesForActivityId(tree, "task");

    assertEquals(1, found.size());
    assertEquals("task", found.get(0).getActivityId());
    assertEquals(1, getInstancesForActivitiyId(tree, "task").size());
    assertTrue(getInstancesForActivityId(tree, "nope").isEmpty());
    assertEquals(1, getInstancesForActivityId(tree, tree.getActivityId()).size());
  }

  public void testRunAsUserEnablesAuthorizationOnlyDuringRunnable() {
    final AtomicInteger calls = new AtomicInteger();
    assertFalse(processEngineConfiguration.isAuthorizationEnabled());

    runAsUser("kermit", Collections.singletonList("accounting"), () -> {
      calls.incrementAndGet();
      assertTrue(processEngineConfiguration.isAuthorizationEnabled());
      assertEquals("kermit", identityService.getCurrentAuthentication().getUserId());
    });

    assertEquals(1, calls.get());
    assertFalse(processEngineConfiguration.isAuthorizationEnabled());
    assertNull(identityService.getCurrentAuthentication().getUserId());
  }

  public void testRunAsUserResetsStateWhenRunnableFails() {
    try {
      runAsUser("gonzo", Arrays.asList("g1", "g2"), () -> {
        throw new IllegalStateException("inside");
      });
      fail("expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertEquals("inside", e.getMessage());
    }

    assertFalse(processEngineConfiguration.isAuthorizationEnabled());
    assertNull(identityService.getCurrentAuthentication().getUserId());
  }

  // -- runBare life cycle -----------------------------------------------------------------------

  private TestResult runProbe(String name) {
    EngineCaseProbes.Probe.bodyRan = false;
    TestResult result = new TestResult();
    new EngineCaseProbes.Probe(name).run(result);
    return result;
  }

  public void testRunBareRunsPassingTest() {
    TestResult result = runProbe("testPasses");

    assertEquals(1, result.runCount());
    assertEquals(0, result.failureCount());
    assertEquals(0, result.errorCount());
    assertTrue(EngineCaseProbes.Probe.bodyRan);
  }

  public void testRunBareReportsAssertionFailure() {
    TestResult result = runProbe("testAssertionFails");

    assertEquals(1, result.failureCount());
    assertEquals(0, result.errorCount());
    assertEquals("deliberate assertion failure", result.failures().nextElement().exceptionMessage());
  }

  public void testRunBareReportsError() {
    TestResult result = runProbe("testErrors");

    assertEquals(0, result.failureCount());
    assertEquals(1, result.errorCount());
    assertEquals("deliberate error", result.errors().nextElement().exceptionMessage());
  }

  public void testRunBareResetsIdentityAndTenantCheck() {
    TestResult result = runProbe("testLeavesIdentityAndTenantCheckDirty");

    assertEquals(0, result.errorCount() + result.failureCount());
    assertTrue(EngineCaseProbes.Probe.bodyRan);
    assertTrue(identityService.getCurrentAuthentication() == null || identityService.getCurrentAuthentication().getUserId() == null);
    assertTrue(processEngineConfiguration.isTenantCheckEnabled());
  }

  public void testRunBareDeletesDeploymentsCreatedByTest() {
    long before = repositoryService.createDeploymentQuery().count();

    TestResult result = runProbe("testDeploysWithoutCleaning");

    assertEquals(0, result.errorCount() + result.failureCount());
    assertEquals(before, repositoryService.createDeploymentQuery().count());
  }

  public void testRunBareRunsWhenHistoryLevelIsSatisfied() {
    TestResult result = runProbe("testWithSatisfiedHistoryLevel");

    assertEquals(0, result.errorCount() + result.failureCount());
    assertTrue(EngineCaseProbes.Probe.bodyRan);
  }

  public void testRunBareSkipsTestWhenRequiredDatabaseDiffers() {
    TestResult result = runProbe("testWithUnsatisfiedDatabase");

    assertEquals(1, result.runCount());
    assertEquals(0, result.errorCount() + result.failureCount());
    assertFalse(EngineCaseProbes.Probe.bodyRan);
  }

  public void testHistoryCleanupJobsAreRemovedAfterTest() {
    EngineCaseProbes.Probe probe = new EngineCaseProbes.Probe("testPasses");
    probe.initializeProcessEngine();
    probe.initializeServices();
    historyService.cleanUpHistoryAsync(true);
    assertEquals(1, historyService.findHistoryCleanupJobs().size());

    probe.deleteHistoryCleanupJobs();

    assertTrue(historyService.findHistoryCleanupJobs().isEmpty());
    // the job log rows written for the cleanup job are not part of what is under test
    TestHelper.assertAndEnsureCleanDbAndCache(processEngine, false);
  }

  public void testServiceReferencesAreClearable() {
    EngineCaseProbes.Probe probe = new EngineCaseProbes.Probe("testPasses");
    probe.initializeProcessEngine();
    probe.initializeServices();
    assertNotNull(probe.runtimeService);
    assertNotNull(probe.decisionService);

    probe.clearServiceReferences();

    assertNull(probe.runtimeService);
    assertNull(probe.processEngineConfiguration);
    assertNull(probe.decisionService);
  }
}
