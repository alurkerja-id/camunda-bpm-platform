package org.camunda.bpm.engine.variable.impl.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Covers {@link FileValueImpl}, its builder and the untyped/null handling of {@link Variables}.
 */
public class FileValueImplTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void fileValueWithoutContentHasNoStream() {
    FileValueImpl value = new FileValueImpl(ValueType.FILE, "f");

    assertThat(value.getValue()).isNull();
    assertThat(value.getByteArray()).isNull();
    assertThat(value.getEncoding()).isNull();
    assertThat(value.getEncodingAsCharset()).isNull();
    assertThat(value.getType()).isEqualTo(ValueType.FILE);
  }

  @Test
  public void settersAreReflected() {
    FileValueImpl value = new FileValueImpl(ValueType.FILE, "f");
    value.setValue(new byte[] {1, 2});
    value.setMimeType("a/b");
    value.setEncoding(StandardCharsets.ISO_8859_1);
    value.setTransient(true);

    assertThat(value.getByteArray()).containsExactly(1, 2);
    assertThat(org.camunda.commons.utils.IoUtil.inputStreamAsByteArray(value.getValue())).containsExactly(1, 2);
    assertThat(value.getMimeType()).isEqualTo("a/b");
    assertThat(value.getEncoding()).isEqualTo("ISO-8859-1");
    assertThat(value.getEncodingAsCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
    assertThat(value.isTransient()).isTrue();
    assertThat(value.toString()).contains("filename=f").contains("mimeType=a/b").contains("isTransient=true");
  }

  @Test
  public void builderRequiresFilename() {
    assertThatThrownBy(() -> Variables.fileValue((String) null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void builderReadsFromStreamBytesAndFile() throws Exception {
    byte[] bytes = "data".getBytes(StandardCharsets.UTF_8);
    File file = folder.newFile("data.txt");
    Files.write(file.toPath(), bytes);

    FileValue fromStream = Variables.fileValue("s").file(new ByteArrayInputStream(bytes)).create();
    FileValue fromBytes = Variables.fileValue("b").file(bytes).encoding("UTF-8").mimeType("text/plain").create();
    FileValue fromFile = Variables.fileValue("f").file(file).encoding(StandardCharsets.UTF_8).create();

    assertThat(org.camunda.commons.utils.IoUtil.inputStreamAsByteArray(fromStream.getValue())).isEqualTo(bytes);
    assertThat(fromBytes.getEncodingAsCharset()).isEqualTo(StandardCharsets.UTF_8);
    assertThat(fromFile.getEncoding()).isEqualTo("UTF-8");
    assertThat(org.camunda.commons.utils.IoUtil.inputStreamAsByteArray(fromFile.getValue())).isEqualTo(bytes);
  }

  @Test
  public void builderWrapsMissingFileAsIllegalArgument() {
    File missing = new File(folder.getRoot(), "missing");

    assertThatThrownBy(() -> Variables.fileValue("x").file(missing)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void fileFactoryUsesFileNameAndDetectedMimeType() throws Exception {
    File file = folder.newFile("note.txt");
    Files.write(file.toPath(), new byte[] {1});

    FileValue value = Variables.fileValue(file);
    FileValue transientValue = Variables.fileValue(file, true);

    assertThat(value.getFilename()).isEqualTo("note.txt");
    assertThat(value.getMimeType()).isEqualTo("text/plain");
    assertThat(value.isTransient()).isFalse();
    assertThat(transientValue.isTransient()).isTrue();
  }

  @Test
  public void untypedValueKeepsTransientStateOfTypedInput() {
    TypedValue transientString = Variables.stringValue("s", true);

    assertThat(Variables.untypedValue(transientString).isTransient()).isTrue();
    assertThat(Variables.untypedValue("s").isTransient()).isFalse();
    assertThat(Variables.untypedValue(Variables.stringValue("s"), true).isTransient()).isTrue();
  }

  @Test
  public void untypedValueAppliesTransientFlagToFileAndBuilders() {
    FileValue file = Variables.fileValue("f").create();
    Object builder = Variables.objectValue("o");

    assertThat(Variables.untypedValue(file, true).isTransient()).isTrue();
    assertThat(Variables.untypedValue(builder, true).isTransient()).isTrue();
    assertThat(Variables.untypedValue(builder).getType()).isEqualTo(ValueType.OBJECT);
  }

  @Test
  public void untypedNullValueSupportsTransient() {
    assertThat(Variables.untypedValue(null).getValue()).isNull();
    assertThat(Variables.untypedValue(null).isTransient()).isFalse();
    assertThat(Variables.untypedNullValue(true).isTransient()).isTrue();
    assertThat(Variables.untypedValue(Variables.untypedNullValue(), true).isTransient()).isTrue();
  }

  @Test
  public void unknownObjectsBecomeUntypedValues() {
    TypedValue value = Variables.untypedValue(new Object());

    assertThat(value).isInstanceOf(UntypedValueImpl.class);
    assertThat(value.getType()).isNull();
  }

  @Test
  public void builderWrapsUnreadableStreamAsIllegalArgument() {
    java.io.InputStream broken = new java.io.InputStream() {
      @Override
      public int read() throws java.io.IOException {
        throw new java.io.IOException("boom");
      }
    };

    assertThatThrownBy(() -> Variables.fileValue("x").file(broken)).isInstanceOf(IllegalArgumentException.class);
  }
}
