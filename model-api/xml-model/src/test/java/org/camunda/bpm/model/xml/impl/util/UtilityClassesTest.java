package org.camunda.bpm.model.xml.impl.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.camunda.bpm.model.xml.ModelException;
import org.camunda.bpm.model.xml.ModelParseException;
import org.camunda.bpm.model.xml.ModelReferenceException;
import org.camunda.bpm.model.xml.ModelValidationException;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.impl.ModelInstanceImpl;
import org.junit.Test;

public class UtilityClassesTest {

  // QName

  @Test
  public void shouldParseQualifiedName() {
    QName qName = QName.parseQName("camunda:asyncBefore");
    assertEquals("camunda", qName.getQualifier());
    assertEquals("asyncBefore", qName.getLocalName());
    assertEquals("camunda:asyncBefore", qName.toString());
  }

  @Test
  public void shouldParseUnqualifiedName() {
    QName qName = QName.parseQName("name");
    assertNull(qName.getQualifier());
    assertEquals("name", qName.getLocalName());
    assertEquals("name", qName.toString());
  }

  @Test
  public void shouldOnlySplitAtFirstColon() {
    QName qName = QName.parseQName("a:b:c");
    assertEquals("a", qName.getQualifier());
    assertEquals("b:c", qName.getLocalName());
  }

  @Test
  public void shouldCombineWithoutQualifierWhenNullOrEmpty() {
    assertEquals("x", QName.combine(null, "x"));
    assertEquals("x", QName.combine("", "x"));
    assertEquals("q:x", QName.combine("q", "x"));
    assertEquals("x", new QName("x").toString());
  }

  @Test
  public void shouldComparePerQualifierAndLocalName() {
    QName a = new QName("q", "x");
    assertEquals(a, a);
    assertEquals(a, new QName("q", "x"));
    assertEquals(a.hashCode(), new QName("q", "x").hashCode());
    assertNotEquals(a, null);
    assertNotEquals(a, "q:x");
    assertNotEquals(a, new QName("q", "y"));
    assertNotEquals(a, new QName("p", "x"));
    assertNotEquals(a, new QName("x"));
    assertNotEquals(new QName("x"), a);
    assertNotEquals(new QName(null, null), new QName(null, "x"));
    assertEquals(new QName(null, null), new QName(null, null));
    assertEquals(new QName("x").hashCode(), new QName(null, "x").hashCode());
    assertNotEquals(new QName(null, "x"), new QName("q", "x"));
  }

  // ModelUtil value conversion

  @Test
  public void shouldConvertRawValuesToPrimitives() {
    assertTrue(ModelUtil.valueAsBoolean("true"));
    assertFalse(ModelUtil.valueAsBoolean("nonsense"));
    assertEquals(42, ModelUtil.valueAsInteger("42"));
    assertEquals(1.5f, ModelUtil.valueAsFloat("1.5"), 0f);
    assertEquals(2.25d, ModelUtil.valueAsDouble("2.25"), 0d);
    assertEquals((short) 12, ModelUtil.valueAsShort("12"));
  }

  @Test
  public void shouldRejectMalformedNumbers() {
    for (Runnable r : new Runnable[] {
        () -> ModelUtil.valueAsInteger("x"),
        () -> ModelUtil.valueAsFloat("x"),
        () -> ModelUtil.valueAsDouble("x"),
        () -> ModelUtil.valueAsShort("x"),
        () -> ModelUtil.valueAsShort("99999") }) {
      try {
        r.run();
        fail("expected ModelTypeException");
      } catch (ModelTypeException e) {
        assertNotNull(e.getMessage());
      }
    }
  }

  @Test
  public void shouldConvertPrimitivesToStrings() {
    assertEquals("true", ModelUtil.valueAsString(true));
    assertEquals("42", ModelUtil.valueAsString(42));
    assertEquals("1.5", ModelUtil.valueAsString(1.5f));
    assertEquals("2.25", ModelUtil.valueAsString(2.25d));
    assertEquals("12", ModelUtil.valueAsString((short) 12));
  }

  @Test
  public void shouldEnsureInstanceOf() {
    ModelUtil.ensureInstanceOf("text", CharSequence.class);
    try {
      ModelUtil.ensureInstanceOf("text", Integer.class);
      fail("expected ModelException");
    } catch (ModelException e) {
      assertTrue(e.getMessage().contains("java.lang.Integer"));
    }
  }

  @Test
  public void shouldCreateQName() {
    QName qName = ModelUtil.getQName("ns", "local");
    assertEquals("ns", qName.getQualifier());
    assertEquals("local", qName.getLocalName());
  }

  // ReflectUtil

  @Test
  public void shouldFindResources() throws IOException {
    String name = "org/camunda/bpm/model/xml/testmodel/instance/AnimalTest.xml";
    try (InputStream stream = ReflectUtil.getResourceAsStream(name)) {
      assertNotNull(stream);
    }
    assertNotNull(ReflectUtil.getResource(name));
    assertNotNull(ReflectUtil.getResource(name, getClass().getClassLoader()));
    assertTrue(ReflectUtil.getResourceAsFile(name).isFile());
    assertNull(ReflectUtil.getResourceAsStream("does/not/exist.xml"));
    assertNull(ReflectUtil.getResource("does/not/exist.xml"));
  }

  @Test
  public void shouldCreateInstanceThroughMatchingConstructor() {
    StringBuilder sb = ReflectUtil.createInstance(StringBuilder.class, "abc");
    assertEquals("abc", sb.toString());
  }

  @Test
  public void shouldWrapConstructorLookupFailure() {
    try {
      ReflectUtil.createInstance(File.class, 1, 2, 3);
      fail("expected ModelException");
    } catch (ModelException e) {
      assertTrue(e.getMessage().contains("java.io.File"));
      assertNotNull(e.getCause());
    }
  }

  // IoUtil

  @Test
  public void shouldCloseSilently() {
    AtomicBoolean closed = new AtomicBoolean();
    IoUtil.closeSilently(() -> closed.set(true));
    assertTrue(closed.get());

    IoUtil.closeSilently(null);
    Closeable failing = () -> {
      throw new IOException("boom");
    };
    IoUtil.closeSilently(failing);
  }

  @Test
  public void shouldReadStreamAsTrimmedLines() throws IOException {
    InputStream in = new ByteArrayInputStream("  a  \n  b\n".getBytes(StandardCharsets.UTF_8));
    assertEquals("ab", IoUtil.getStringFromInputStream(in));
  }

  @Test
  public void shouldConvertOutputStreamToInputStream() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(new byte[] { 1, 2, 3 });
    InputStream in = IoUtil.convertOutputStreamToInputStream(out);
    assertEquals(3, in.available());
    assertEquals(1, in.read());
  }

  @Test
  public void shouldWriteDocumentAsString() {
    ModelInstanceImpl modelInstance = (ModelInstanceImpl) new TestModelParser().parseModelFromStream(
        ReflectUtil.getResourceAsStream("org/camunda/bpm/model/xml/testmodel/instance/AnimalTest.xml"));
    String xml = IoUtil.convertXmlDocumentToString(modelInstance.getDocument());
    assertTrue(xml, xml.contains("<animals"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    IoUtil.writeDocumentToOutputStream(modelInstance.getDocument(), out);
    assertEquals(xml, new String(out.toByteArray(), StandardCharsets.UTF_8));
  }

  @Test
  public void shouldToleratePartiallySupportedTransformerFactoryControls() {
    javax.xml.transform.TransformerFactory factory = javax.xml.transform.TransformerFactory.newInstance();
    IoUtil.denyExternalAccess(factory);
    assertNotNull(factory);
  }

  // exceptions

  @Test
  public void shouldKeepMessageAndCauseOfModelExceptions() {
    Throwable cause = new IllegalStateException("root");

    assertNull(new ModelException().getMessage());
    assertSame(cause, new ModelException(cause).getCause());

    assertNull(new ModelParseException().getMessage());
    assertEquals("m", new ModelParseException("m").getMessage());
    assertSame(cause, new ModelParseException("m", cause).getCause());
    assertSame(cause, new ModelParseException(cause).getCause());

    assertNull(new ModelReferenceException().getMessage());
    assertEquals("m", new ModelReferenceException("m").getMessage());
    assertSame(cause, new ModelReferenceException("m", cause).getCause());
    assertSame(cause, new ModelReferenceException(cause).getCause());

    assertNull(new ModelValidationException().getMessage());
    assertEquals("m", new ModelValidationException("m").getMessage());
    assertSame(cause, new ModelValidationException("m", cause).getCause());
    assertSame(cause, new ModelValidationException(cause).getCause());

    assertNull(new ModelIoException().getMessage());
    assertEquals("m", new ModelIoException("m").getMessage());
    assertSame(cause, new ModelIoException("m", cause).getCause());
    assertSame(cause, new ModelIoException(cause).getCause());
  }

  @Test
  public void shouldDescribeTypeMismatch() {
    ModelTypeException e = new ModelTypeException("abc", Integer.class);
    assertTrue(e.getMessage(), e.getMessage().contains("abc"));
    assertTrue(e.getMessage(), e.getMessage().contains("Integer"));
    assertEquals("plain", new ModelTypeException("plain").getMessage());
  }
}
