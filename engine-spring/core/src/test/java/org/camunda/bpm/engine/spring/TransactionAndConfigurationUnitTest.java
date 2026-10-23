package org.camunda.bpm.engine.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;

import javax.sql.DataSource;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.camunda.bpm.engine.impl.cfg.TransactionListener;
import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Covers {@link SpringTransactionContext}, {@link SpringTransactionInterceptor},
 * {@link SpringTransactionsProcessEngineConfiguration} and {@link SpringConfigurationHelper}.
 */
public class TransactionAndConfigurationUnitTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  protected PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
  protected CommandContext commandContext = mock(CommandContext.class);
  protected TransactionListener listener = mock(TransactionListener.class);

  @Before
  public void initSynchronization() {
    TransactionSynchronizationManager.initSynchronization();
  }

  @After
  public void clearSynchronization() {
    TransactionSynchronizationManager.clear();
  }

  protected List<TransactionSynchronization> synchronizations() {
    return TransactionSynchronizationManager.getSynchronizations();
  }

  // SpringTransactionContext

  @Test
  public void contextTracksTransactionStateThroughSynchronizationCallbacks() {
    SpringTransactionContext context = new SpringTransactionContext(transactionManager, commandContext);
    TransactionSynchronization sync = synchronizations().get(0);

    sync.suspend();
    sync.resume();
    sync.flush();
    assertThat(context.lastTransactionState).isNull();

    sync.beforeCommit(false);
    assertThat(context.lastTransactionState).isEqualTo(TransactionState.COMMITTING);
    sync.afterCommit();
    assertThat(context.lastTransactionState).isEqualTo(TransactionState.COMMITTED);
    sync.beforeCompletion();
    assertThat(context.lastTransactionState).isEqualTo(TransactionState.ROLLINGBACK);
    sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
    assertThat(context.lastTransactionState).isEqualTo(TransactionState.ROLLINGBACK);
    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    assertThat(context.lastTransactionState).isEqualTo(TransactionState.ROLLED_BACK);
  }

  @Test
  public void contextIsInactiveWithoutActualTransactionOrAfterRollback() {
    SpringTransactionContext context = new SpringTransactionContext(transactionManager, commandContext);

    assertThat(context.isTransactionActive()).isFalse();

    TransactionSynchronizationManager.setActualTransactionActive(true);
    assertThat(context.isTransactionActive()).isTrue();

    context.lastTransactionState = TransactionState.ROLLINGBACK;
    assertThat(context.isTransactionActive()).isFalse();
    context.lastTransactionState = TransactionState.ROLLED_BACK;
    assertThat(context.isTransactionActive()).isFalse();
    context.lastTransactionState = TransactionState.COMMITTED;
    assertThat(context.isTransactionActive()).isTrue();
  }

  @Test
  public void commitIsNoOpAndRollbackMarksTransactionRollbackOnly() {
    TransactionStatus status = mock(TransactionStatus.class);
    when(transactionManager.getTransaction(null)).thenReturn(status);
    SpringTransactionContext context = new SpringTransactionContext(transactionManager, commandContext);

    context.commit();
    org.mockito.Mockito.verifyNoInteractions(status);

    context.rollback();
    verify(status).setRollbackOnly();
  }

  @Test
  public void listenersFireOnMatchingSynchronizationCallbackOnly() {
    SpringTransactionContext context = new SpringTransactionContext(transactionManager, commandContext);
    for (TransactionState state : new TransactionState[] {TransactionState.COMMITTING, TransactionState.COMMITTED,
        TransactionState.ROLLINGBACK, TransactionState.ROLLED_BACK}) {
      context.addTransactionListener(state, listener);
    }
    // index 0 is the state tracker registered by the constructor
    List<TransactionSynchronization> syncs = synchronizations();
    assertThat(syncs).hasSize(5);

    syncs.get(1).afterCommit();
    org.mockito.Mockito.verifyNoInteractions(listener);
    syncs.get(1).beforeCommit(false);
    verify(listener, org.mockito.Mockito.times(1)).execute(commandContext);

    syncs.get(2).beforeCommit(false);
    org.mockito.Mockito.verify(listener, org.mockito.Mockito.times(1)).execute(commandContext);
    syncs.get(2).afterCommit();
    verify(listener, org.mockito.Mockito.times(2)).execute(commandContext);

    syncs.get(3).afterCommit();
    verify(listener, org.mockito.Mockito.times(2)).execute(commandContext);
    syncs.get(3).beforeCompletion();
    verify(listener, org.mockito.Mockito.times(3)).execute(commandContext);

    syncs.get(4).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
    verify(listener, org.mockito.Mockito.times(3)).execute(commandContext);
    syncs.get(4).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    verify(listener, org.mockito.Mockito.times(4)).execute(commandContext);
  }

  @Test
  public void contextFactoryCreatesSpringContext() {
    assertThat(new SpringTransactionContextFactory(transactionManager).openTransactionContext(commandContext))
        .isInstanceOf(SpringTransactionContext.class);
  }

  // SpringTransactionInterceptor

  @Test
  @SuppressWarnings("unchecked")
  public void interceptorRunsNextCommandInsideTransactionWithConfiguredPropagation() throws Exception {
    TransactionStatus status = mock(TransactionStatus.class);
    org.mockito.ArgumentCaptor<TransactionDefinition> captor = org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);
    when(transactionManager.getTransaction(captor.capture())).thenReturn(status);
    Command<String> command = mock(Command.class);
    CommandInterceptor next = mock(CommandInterceptor.class);
    when(next.execute(command)).thenReturn("result");
    SpringTransactionInterceptor interceptor = new SpringTransactionInterceptor(
        transactionManager, TransactionDefinition.PROPAGATION_REQUIRES_NEW, null);
    interceptor.setNext(next);

    assertThat(interceptor.execute(command)).isEqualTo("result");

    assertThat(captor.getValue().getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    verify(transactionManager).commit(status);
  }

  @Test
  @SuppressWarnings({"unchecked", "deprecation"})
  public void interceptorWrapsTransactionSystemExceptionAsPersistenceFailure() {
    TransactionStatus status = mock(TransactionStatus.class);
    when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
    org.mockito.Mockito.doThrow(new TransactionSystemException("commit failed")).when(transactionManager).commit(status);
    Command<String> command = mock(Command.class);
    SpringTransactionInterceptor interceptor = new SpringTransactionInterceptor(
        transactionManager, TransactionDefinition.PROPAGATION_REQUIRED);
    interceptor.setNext(mock(CommandInterceptor.class));

    assertThatThrownBy(() -> interceptor.execute(command)).isInstanceOf(ProcessEngineException.class);
  }

  // SpringTransactionsProcessEngineConfiguration

  @Test
  public void configurationRequiresTransactionManagerForInterceptors() {
    SpringTransactionsProcessEngineConfiguration configuration = new SpringTransactionsProcessEngineConfiguration();

    assertThatThrownBy(configuration::getDefaultCommandInterceptorsTxRequired)
        .isInstanceOf(ProcessEngineException.class).hasMessageContaining("transactionManager is required");
  }

  @Test
  public void configurationBuildsSpringTransactionInterceptorsForBothPropagations() {
    SpringTransactionsProcessEngineConfiguration configuration = new SpringTransactionsProcessEngineConfiguration();
    configuration.setTransactionManager(transactionManager);

    Collection<CommandInterceptor> required = configuration.getDefaultCommandInterceptorsTxRequired();
    Collection<CommandInterceptor> requiresNew = configuration.getDefaultCommandInterceptorsTxRequiresNew();

    assertThat(required).hasAtLeastOneElementOfType(SpringTransactionInterceptor.class);
    assertThat(requiresNew).hasAtLeastOneElementOfType(SpringTransactionInterceptor.class);
    assertThat(configuration.getTransactionManager()).isSameAs(transactionManager);
  }

  @Test
  public void configurationInitializesSpringTransactionContextFactoryOnlyWithManager() {
    SpringTransactionsProcessEngineConfiguration without = new SpringTransactionsProcessEngineConfiguration();
    without.initTransactionContextFactory();
    assertThat(without.getTransactionContextFactory()).isNull();

    SpringTransactionsProcessEngineConfiguration with = new SpringTransactionsProcessEngineConfiguration();
    with.setTransactionManager(transactionManager);
    with.initTransactionContextFactory();
    assertThat(with.getTransactionContextFactory()).isInstanceOf(SpringTransactionContextFactory.class);
  }

  @Test
  public void configurationWrapsDataSourceInTransactionAwareProxyOnce() {
    SpringTransactionsProcessEngineConfiguration configuration = new SpringTransactionsProcessEngineConfiguration();
    DataSource plain = mock(DataSource.class);

    configuration.setDataSource(plain);
    assertThat(configuration.getDataSource()).isInstanceOf(TransactionAwareDataSourceProxy.class);

    TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(plain);
    configuration.setDataSource(proxy);
    assertThat(configuration.getDataSource()).isSameAs(proxy);
  }

  @Test
  public void configurationExposesDeploymentSettings() {
    SpringTransactionsProcessEngineConfiguration configuration = new SpringTransactionsProcessEngineConfiguration();
    assertThat(configuration.getDeploymentName()).isEqualTo("SpringAutoDeployment");
    assertThat(configuration.getDeploymentResources()).isEmpty();
    assertThat(configuration.isDeployChangedOnly()).isFalse();
    assertThat(configuration.getDeploymentTenantId()).isNull();

    Resource[] resources = {new ByteArrayResource(new byte[0])};
    configuration.setDeploymentName("name");
    configuration.setDeploymentResources(resources);
    configuration.setDeploymentTenantId("tenant");
    configuration.setDeployChangedOnly(true);

    assertThat(configuration.getDeploymentName()).isEqualTo("name");
    assertThat(configuration.getDeploymentResources()).isSameAs(resources);
    assertThat(configuration.getDeploymentTenantId()).isEqualTo("tenant");
    assertThat(configuration.isDeployChangedOnly()).isTrue();
  }

  protected ProcessEngine engineWithDeploymentBuilder(DeploymentBuilder builder) {
    ProcessEngine engine = mock(ProcessEngine.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    when(engine.getRepositoryService()).thenReturn(repositoryService);
    when(repositoryService.createDeployment()).thenReturn(builder);
    when(builder.enableDuplicateFiltering(org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(builder);
    when(builder.name(any(String.class))).thenReturn(builder);
    when(builder.tenantId(any())).thenReturn(builder);
    return engine;
  }

  @Test
  public void autoDeploymentSkipsWhenNoResources() {
    DeploymentBuilder builder = mock(DeploymentBuilder.class);
    ProcessEngine engine = engineWithDeploymentBuilder(builder);

    new SpringTransactionsProcessEngineConfiguration().autoDeployResources(engine);

    org.mockito.Mockito.verifyNoInteractions(builder);
  }

  @Test
  public void autoDeploymentNamesResourcesByTypeAndHandlesArchives() throws Exception {
    DeploymentBuilder builder = mock(DeploymentBuilder.class);
    ProcessEngine engine = engineWithDeploymentBuilder(builder);
    File file = folder.newFile("process.bpmn");
    Files.write(file.toPath(), "<x/>".getBytes(StandardCharsets.UTF_8));
    File zip = folder.newFile("archive.zip");
    Resource archive = new FileSystemResource(zip);
    Resource contextResource = new ContextByteArrayResource("<z/>".getBytes(StandardCharsets.UTF_8), "WEB-INF/ctx.bpmn");
    SpringTransactionsProcessEngineConfiguration configuration = new SpringTransactionsProcessEngineConfiguration();
    configuration.setDeploymentName("dep");
    configuration.setDeploymentTenantId("t1");
    configuration.setDeployChangedOnly(true);
    configuration.setDeploymentResources(new Resource[] {
        new ClassPathResource("org/camunda/bpm/engine/spring/test/autodeployment/autodeploy.a.bpmn20.xml"),
        new FileSystemResource(file),
        contextResource,
        new ByteArrayResource("<y/>".getBytes(StandardCharsets.UTF_8), "bytes.bpmn"),
        archive});

    configuration.autoDeployResources(engine);

    verify(builder).enableDuplicateFiltering(true);
    verify(builder).name("dep");
    verify(builder).tenantId("t1");
    verify(builder).addInputStream(org.mockito.ArgumentMatchers.endsWith("test-classes/org/camunda/bpm/engine/spring/test/autodeployment/autodeploy.a.bpmn20.xml"), any(InputStream.class));
    verify(builder).addInputStream(org.mockito.ArgumentMatchers.eq("WEB-INF/ctx.bpmn"), any(InputStream.class));
    verify(builder).addInputStream(org.mockito.ArgumentMatchers.eq(file.getAbsolutePath()), any(InputStream.class));
    verify(builder).addInputStream(org.mockito.ArgumentMatchers.eq("Byte array resource [bytes.bpmn]"), any(InputStream.class));
    verify(builder).addZipInputStream(any(java.util.zip.ZipInputStream.class));
    verify(builder).deploy();
  }

  @Test
  public void autoDeploymentWrapsUnreadableResources() {
    DeploymentBuilder builder = mock(DeploymentBuilder.class);
    ProcessEngine engine = engineWithDeploymentBuilder(builder);
    SpringTransactionsProcessEngineConfiguration configuration = new SpringTransactionsProcessEngineConfiguration();
    configuration.setDeploymentResources(new Resource[] {new ClassPathResource("does/not/exist.bpmn")});

    assertThatThrownBy(() -> configuration.autoDeployResources(engine))
        .isInstanceOf(ProcessEngineException.class).hasMessageContaining("couldn't auto deploy resource");
  }

  @Test
  public void fileResourceNameFallsBackToFilenameWhenNoFileAvailable() {
    SpringTransactionsProcessEngineConfiguration configuration = new SpringTransactionsProcessEngineConfiguration();
    Resource resource = new org.springframework.core.io.InputStreamResource(new ByteArrayInputStream(new byte[0])) {
      @Override
      public String getFilename() {
        return "stream.bpmn";
      }

      @Override
      public File getFile() throws IOException {
        throw new IOException("no file");
      }
    };

    assertThat(configuration.getFileResourceName(resource)).isEqualTo("stream.bpmn");
  }

  // SpringConfigurationHelper

  @Test
  public void helperFailsWhenContextDefinesNoProcessEngine() throws Exception {
    File xml = folder.newFile("empty-context.xml");
    Files.write(xml.toPath(), ("<beans xmlns=\"http://www.springframework.org/schema/beans\" "
        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
        + "xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd\"/>")
        .getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> SpringConfigurationHelper.buildProcessEngine(xml.toURI().toURL()))
        .isInstanceOf(ProcessEngineException.class).hasMessageContaining("no org.camunda.bpm.engine.ProcessEngine defined");
  }

  public static ProcessEngine stubProcessEngine() {
    return mock(ProcessEngine.class);
  }

  @Test
  public void helperReturnsProcessEngineBeanFromContext() throws Exception {
    File xml = folder.newFile("engine-context.xml");
    Files.write(xml.toPath(), ("<beans xmlns=\"http://www.springframework.org/schema/beans\" "
        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
        + "xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd\">"
        + "<bean id=\"engine\" class=\"" + TransactionAndConfigurationUnitTest.class.getName()
        + "\" factory-method=\"stubProcessEngine\"/></beans>").getBytes(StandardCharsets.UTF_8));

    assertThat(SpringConfigurationHelper.buildProcessEngine(xml.toURI().toURL())).isInstanceOf(ProcessEngine.class);
  }

  static class ContextByteArrayResource extends ByteArrayResource implements org.springframework.core.io.ContextResource {
    private final String path;

    ContextByteArrayResource(byte[] bytes, String path) {
      super(bytes);
      this.path = path;
    }

    @Override
    public String getPathWithinContext() {
      return path;
    }
  }
}
