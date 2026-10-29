package org.camunda.commons.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Covers the reader, file and failure paths of {@link IoUtil} and the messages of {@link IoUtilLogger}.
 */
public class IoUtilExtraTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void shouldReadReaderAsString() {
    assertThat(IoUtil.readerAsString(new StringReader("héllo"))).isEqualTo("héllo");
  }

  @Test
  public void shouldCloseReaderAfterReading() {
    boolean[] closed = {false};
    Reader reader = new StringReader("x") {
      @Override
      public void close() {
        closed[0] = true;
      }
    };

    IoUtil.readerAsString(reader);

    assertThat(closed[0]).isTrue();
  }

  @Test
  public void shouldWrapReaderFailure() {
    Reader broken = new Reader() {
      @Override
      public int read(char[] buf, int off, int len) throws IOException {
        throw new IOException("boom");
      }

      @Override
      public void close() {
      }
    };

    assertThatThrownBy(() -> IoUtil.readerAsString(broken))
        .isInstanceOf(IoUtilException.class)
        .hasMessageContaining("Unable to read from reader")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  public void shouldWrapInputStreamFailure() {
    InputStream broken = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }
    };

    assertThatThrownBy(() -> IoUtil.inputStreamAsByteArray(broken))
        .isInstanceOf(IoUtilException.class)
        .hasMessageContaining("Unable to read input stream");
  }

  @Test
  public void shouldCloseSilently() {
    IoUtil.closeSilently(null);
    IoUtil.closeSilently(new ByteArrayInputStream(new byte[0]));
    Closeable failing = () -> {
      throw new IOException("ignored");
    };
    IoUtil.closeSilently(failing);
  }

  @Test
  public void shouldReadFilesByFileObject() throws IOException {
    File file = folder.newFile("a.txt");
    Files.write(file.toPath(), "abc".getBytes(StandardCharsets.UTF_8));

    assertThat(IoUtil.fileAsString(file)).isEqualTo("abc");
    assertThat(IoUtil.fileAsByteArray(file)).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
    assertThat(IoUtil.inputStreamAsString(IoUtil.fileAsStream(file))).isEqualTo("abc");
  }

  @Test
  public void shouldFailForMissingFileObjects() {
    File missing = new File(folder.getRoot(), "missing.txt");

    assertThatThrownBy(() -> IoUtil.fileAsString(missing))
        .isInstanceOf(IoUtilException.class).hasMessageContaining(missing.getAbsolutePath());
    assertThatThrownBy(() -> IoUtil.fileAsByteArray(missing)).isInstanceOf(IoUtilException.class);
    assertThatThrownBy(() -> IoUtil.fileAsStream(missing)).isInstanceOf(IoUtilException.class);
  }

  @Test
  public void shouldReadClasspathFileAsStreamByName() {
    assertThat(IoUtil.inputStreamAsString(IoUtil.fileAsStream(IoUtilTest.TEST_FILE_NAME))).isEqualTo("This is a Test!");
  }

  @Test
  public void shouldRejectNullFilenames() {
    assertThatThrownBy(() -> IoUtil.getClasspathFile(null))
        .isInstanceOf(IoUtilException.class).hasMessageContaining("Parameter 'filename' can not be null");
    assertThatThrownBy(() -> IoUtil.getClasspathFile(null, getClass().getClassLoader()))
        .isInstanceOf(IoUtilException.class);
  }

  @Test
  public void shouldResolveClasspathFileWithGivenAndFallbackClassLoader() {
    File viaGiven = IoUtil.getClasspathFile(IoUtilTest.TEST_FILE_NAME, getClass().getClassLoader());
    File viaFallback = IoUtil.getClasspathFile(IoUtilTest.TEST_FILE_NAME, new ClassLoader(null) { });

    assertThat(viaGiven).exists();
    assertThat(viaFallback).isEqualTo(viaGiven);
  }

  @Test
  public void shouldFailForUnknownClasspathFile() {
    assertThatThrownBy(() -> IoUtil.getClasspathFile("does/not/exist.txt", getClass().getClassLoader()))
        .isInstanceOf(IoUtilException.class)
        .hasMessageContaining("Unable to find file with path 'does/not/exist.txt'");
  }
}
