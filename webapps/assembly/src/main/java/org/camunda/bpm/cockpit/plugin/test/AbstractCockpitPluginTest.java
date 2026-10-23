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
package org.camunda.bpm.cockpit.plugin.test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.LogManager;

import org.apache.ibatis.logging.LogFactory;
import org.camunda.bpm.cockpit.Cockpit;
import org.camunda.bpm.cockpit.db.CommandExecutor;
import org.camunda.bpm.cockpit.db.QueryService;
import org.camunda.bpm.cockpit.impl.DefaultCockpitRuntimeDelegate;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.pvm.PvmException;
import org.camunda.bpm.engine.impl.util.IoUtil;
import org.camunda.bpm.engine.impl.util.ReflectUtil;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;

/**
 *
 * @author nico.rehwaldt
 */
public abstract class AbstractCockpitPluginTest {

  private static final TestCockpitRuntimeDelegate RUNTIME_DELEGATE = new TestCockpitRuntimeDelegate();
  private static final String DEFAULT_BPMN_RESOURCE_NAME = "process.bpmn20.xml";

  static {
    readJavaUtilLoggingConfigFromClasspath();

    // this ensures that mybatis uses the jdk logging
    LogFactory.useJdkLogging();
    // with an upgrade of mybatis, this might have to become org.mybatis.generator.logging.LogFactory.forceJavaLogging();
  }

  private static void readJavaUtilLoggingConfigFromClasspath() {
    InputStream inputStream = ReflectUtil.getResourceAsStream("logging.properties");
    try {
      if (inputStream != null) {
        LogManager.getLogManager().readConfiguration(inputStream);

        String redirectCommons = LogManager.getLogManager().getProperty("redirect.commons.logging");
        if ((redirectCommons != null) && (!redirectCommons.equalsIgnoreCase("false"))) {
          System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Jdk14Logger");
        }
      }
    } catch (Exception e) {
      throw new PvmException("couldn't initialize logging properly", e);
    } finally {
      IoUtil.closeSilently(inputStream);
    }
  }

  @Rule
  public ProcessEngineRule processEngineRule = new ProcessEngineRule(true);

  @BeforeClass
  public static void beforeClass() {
    Cockpit.setCockpitRuntimeDelegate(RUNTIME_DELEGATE);
  }

  @AfterClass
  public static void afterClass() {
    Cockpit.setCockpitRuntimeDelegate(null);
  }

  @Before
  public void before() {
    RUNTIME_DELEGATE.ENGINE = getProcessEngine();
  }

  @After
  public void after() {
    RUNTIME_DELEGATE.ENGINE = null;
    getProcessEngine().getIdentityService().clearAuthentication();
  }

  public ProcessEngine getProcessEngine() {
    return processEngineRule.getProcessEngine();
  }

  protected CommandExecutor getCommandExecutor() {
    return Cockpit.getCommandExecutor("default");
  }

  protected QueryService getQueryService() {
    return Cockpit.getQueryService("default");
  }

  public void executeAvailableJobs() {
    ManagementService managementService = getProcessEngine().getManagementService();
    List<Job> jobs = managementService.createJobQuery().withRetriesLeft().list();

    if (jobs.isEmpty()) {
      return;
    }

    for (Job job : jobs) {
      try {
        managementService.executeJob(job.getId());
      } catch (Exception e) {};
    }

    executeAvailableJobs();
  }

  public Deployment deploy(String... resources) {
    return deploy(createDeploymentBuilder(), Collections.<BpmnModelInstance> emptyList(), Arrays.asList(resources));
  }

  public Deployment deployForTenant(String tenantId, String... resources) {
    return deploy(createDeploymentBuilder().tenantId(tenantId), Collections.<BpmnModelInstance> emptyList(), Arrays.asList(resources));
  }

  protected Deployment deploy(DeploymentBuilder deploymentBuilder, List<BpmnModelInstance> bpmnModelInstances, List<String> resources) {
    int i = 0;
    for (BpmnModelInstance bpmnModelInstance : bpmnModelInstances) {
      deploymentBuilder.addModelInstance(i + "_" + DEFAULT_BPMN_RESOURCE_NAME, bpmnModelInstance);
      i++;
    }

    for (String resource : resources) {
      deploymentBuilder.addClasspathResource(resource);
    }

    Deployment deployment = deploymentBuilder.deploy();

    processEngineRule.manageDeployment(deployment);

    return deployment;
  }

  protected DeploymentBuilder createDeploymentBuilder() {
    return getProcessEngine().getRepositoryService().createDeployment();
  }

  private static class TestCockpitRuntimeDelegate extends DefaultCockpitRuntimeDelegate {

    public ProcessEngine ENGINE;

    @Override
    public ProcessEngine getProcessEngine(String processEngineName) {

      // always return default engine for plugin tests
      return ENGINE;
    }
  }
}
