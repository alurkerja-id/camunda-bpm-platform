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
package org.camunda.bpm.container.impl.deployment.scanning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.util.Map;
import org.junit.Test;

/**
 * Covers the VFS-or-classpath scanner choice in {@link ProcessApplicationScanningUtil}.
 *
 * <p>The VFS probe falls back to the classpath scanner when {@code org.jboss.vfs.VFS} cannot be
 * loaded, whether as a {@link ClassNotFoundException} or as a {@link LinkageError}. Any other
 * {@link Error} (e.g. a {@link StackOverflowError}) is no longer swallowed (java:S1181).
 */
public class ProcessApplicationScanningUtilTest {

  protected static class FailingClassLoader extends ClassLoader {

    protected final Throwable failure;

    FailingClassLoader(Throwable failure) {
      super(ProcessApplicationScanningUtilTest.class.getClassLoader());
      this.failure = failure;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
      if ("org.jboss.vfs.VFS".equals(name)) {
        if (failure instanceof ClassNotFoundException) {
          throw (ClassNotFoundException) failure;
        }
        if (failure instanceof Error) {
          throw (Error) failure;
        }
        throw (RuntimeException) failure;
      }
      return super.loadClass(name);
    }
  }

  protected URL metaFileUrl() {
    return ProcessApplicationScanningUtilTest.class.getResource("ProcessApplicationScanningUtilTest.class");
  }

  protected Map<String, byte[]> scan(Throwable failure) {
    return ProcessApplicationScanningUtil.findResources(new FailingClassLoader(failure), "", metaFileUrl());
  }

  @Test
  public void shouldFallBackToClasspathScannerOnClassNotFound() {
    assertThat(scan(new ClassNotFoundException("org.jboss.vfs.VFS"))).isNotNull();
  }

  @Test
  public void shouldFallBackToClasspathScannerOnLinkageError() {
    assertThat(scan(new NoClassDefFoundError("org/jboss/vfs/VFS"))).isNotNull();
  }

  @Test
  public void shouldFallBackToClasspathScannerOnRuntimeException() {
    assertThat(scan(new IllegalStateException("boom"))).isNotNull();
  }

  // deliberate change (java:S1181): previously swallowed by catch (Throwable)
  @Test
  public void shouldNotSwallowNonLinkageError() {
    assertThatThrownBy(() -> scan(new StackOverflowError()))
        .isInstanceOf(StackOverflowError.class);
  }
}
