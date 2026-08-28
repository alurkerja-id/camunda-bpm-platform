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
package org.camunda.bpm.engine.impl.digest._apacheCommonsCodec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Covers the vendored Apache Commons Codec Base64, which the password digests depend on.
 *
 * <p>The interesting behaviour sits at the edges: how many padding characters each input length
 * produces, what the URL-safe alphabet swaps, when chunking inserts separators, and which inputs
 * are refused outright.
 */
public class Base64Test {

  protected static String encode(byte[] data) {
    return new String(Base64.encodeBase64(data), StandardCharsets.UTF_8);
  }

  protected static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  // padding, one case per remainder ------------------------------------------------------------

  @Test
  public void shouldPadAccordingToTheRemainingBytes() {
    assertThat(encode(bytes("abc"))).isEqualTo("YWJj");
    assertThat(encode(bytes("ab"))).isEqualTo("YWI=");
    assertThat(encode(bytes("a"))).isEqualTo("YQ==");
  }

  @Test
  public void shouldRoundTripEveryRemainder() {
    for (String text : new String[] { "a", "ab", "abc", "abcd", "abcde", "abcdef" }) {
      byte[] encoded = Base64.encodeBase64(bytes(text));

      assertThat(new String(Base64.decodeBase64(encoded), StandardCharsets.UTF_8)).isEqualTo(text);
    }
  }

  @Test
  public void shouldReturnTheInputUntouchedWhenItIsNullOrEmpty() {
    assertThat(Base64.encodeBase64(null)).isNull();
    assertThat(Base64.encodeBase64(new byte[0])).isEmpty();
    assertThat(Base64.decodeBase64((byte[]) null)).isNull();
    assertThat(Base64.decodeBase64(new byte[0])).isEmpty();
  }

  // string flavours ------------------------------------------------------------------------------

  /**
   * The no-argument constructor chunks, so an instance appends a line separator where the static
   * encodeBase64 does not. Worth pinning - the two look interchangeable and are not.
   */
  @Test
  public void shouldEncodeToString() {
    assertThat(Base64.encodeBase64String(bytes("abc"))).startsWith("YWJj");
    assertThat(new Base64().encodeToString(bytes("abc"))).isEqualTo("YWJj\r\n");
    assertThat(new Base64(0).encodeToString(bytes("abc"))).isEqualTo("YWJj");
  }

  @Test
  public void shouldDecodeFromString() {
    assertThat(new String(Base64.decodeBase64("YWJj"), StandardCharsets.UTF_8)).isEqualTo("abc");
  }

  // url safe -------------------------------------------------------------------------------------

  @Test
  public void shouldSwapTheTwoUnsafeCharactersAndDropPadding() {
    // 0xFB 0xFF encodes to "+/8=" with the standard alphabet
    byte[] data = new byte[] { (byte) 0xfb, (byte) 0xff };

    assertThat(encode(data)).isEqualTo("+/8=");
    assertThat(new String(Base64.encodeBase64URLSafe(data), StandardCharsets.UTF_8))
        .isEqualTo("-_8");
    assertThat(Base64.encodeBase64URLSafeString(data)).isEqualTo("-_8");
  }

  @Test
  public void shouldDecodeBothAlphabets() {
    assertThat(Base64.decodeBase64("-_8")).isEqualTo(new byte[] { (byte) 0xfb, (byte) 0xff });
    assertThat(Base64.decodeBase64("+/8=")).isEqualTo(new byte[] { (byte) 0xfb, (byte) 0xff });
  }

  @Test
  public void shouldReportWhichAlphabetItEncodesWith() {
    assertThat(new Base64().isUrlSafe()).isFalse();
    assertThat(new Base64(true).isUrlSafe()).isTrue();
    assertThat(new Base64(false).isUrlSafe()).isFalse();
  }

  // chunking --------------------------------------------------------------------------------------

  @Test
  public void shouldBreakLongOutputIntoChunks() {
    byte[] data = new byte[100];

    String chunked = new String(Base64.encodeBase64Chunked(data), StandardCharsets.UTF_8);

    assertThat(chunked).contains("\r\n");
    assertThat(chunked).endsWith("\r\n");
    assertThat(new String(Base64.encodeBase64(data), StandardCharsets.UTF_8))
        .doesNotContain("\r\n");
  }

  @Test
  public void shouldRoundTripChunkedOutput() {
    byte[] data = bytes("the quick brown fox jumps over the lazy dog, twice over in fact");

    byte[] chunked = Base64.encodeBase64(data, true);

    assertThat(Base64.decodeBase64(chunked)).isEqualTo(data);
  }

  @Test
  public void shouldRoundLineLengthDownToAMultipleOfFour() {
    byte[] data = new byte[60];

    // 6 rounds down to 4, so every line holds 4 characters
    String encoded = new String(new Base64(6).encode(data), StandardCharsets.UTF_8);

    assertThat(encoded.split("\r\n")[0]).hasSize(4);
  }

  @Test
  public void shouldNotChunkWhenLineLengthIsZeroOrNegative() {
    byte[] data = new byte[60];

    assertThat(new String(new Base64(0).encode(data), StandardCharsets.UTF_8))
        .doesNotContain("\r\n");
    assertThat(new String(new Base64(-1).encode(data), StandardCharsets.UTF_8))
        .doesNotContain("\r\n");
  }

  @Test
  public void shouldTreatNullSeparatorAsNoChunking() {
    byte[] data = new byte[60];

    assertThat(new String(new Base64(76, null).encode(data), StandardCharsets.UTF_8))
        .doesNotContain("\r\n");
  }

  @Test
  public void shouldRefuseSeparatorMadeOfBase64Characters() {
    assertThatThrownBy(() -> new Base64(76, bytes("A")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lineSeperator must not contain base64 characters");
  }

  // guards -----------------------------------------------------------------------------------------

  @Test
  public void shouldRefuseOutputBiggerThanTheGivenLimit() {
    assertThatThrownBy(() -> Base64.encodeBase64(new byte[100], false, false, 4))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Input array too big");
  }

  @Test
  public void shouldStayWithinTheLimitWhenItFits() {
    assertThat(Base64.encodeBase64(bytes("abc"), false, false, 100)).isNotEmpty();
    assertThat(Base64.encodeBase64(null, false, false, 4)).isNull();
    assertThat(Base64.encodeBase64(new byte[0], false, false, 4)).isEmpty();
  }

  // alphabet checks ---------------------------------------------------------------------------------

  @Test
  public void shouldRecogniseBase64Bytes() {
    assertThat(Base64.isBase64((byte) 'A')).isTrue();
    assertThat(Base64.isBase64((byte) 'z')).isTrue();
    assertThat(Base64.isBase64((byte) '0')).isTrue();
    assertThat(Base64.isBase64((byte) '+')).isTrue();
    assertThat(Base64.isBase64((byte) '/')).isTrue();
    assertThat(Base64.isBase64((byte) '=')).isTrue();
    assertThat(Base64.isBase64((byte) '*')).isFalse();
    assertThat(Base64.isBase64((byte) -1)).isFalse();
  }

  @Test
  public void shouldAcceptWhitespaceInsideAnArrayOfBase64Bytes() {
    assertThat(Base64.isArrayByteBase64(bytes("YWJj"))).isTrue();
    assertThat(Base64.isArrayByteBase64(bytes("YW Jj\r\n\t"))).isTrue();
    assertThat(Base64.isArrayByteBase64(new byte[0])).isTrue();
    assertThat(Base64.isArrayByteBase64(bytes("not base64 *"))).isFalse();
  }

  @Test
  public void shouldSkipCharactersOutsideTheAlphabetWhenDecoding() {
    assertThat(new String(Base64.decodeBase64("YW\r\nJj"), StandardCharsets.UTF_8))
        .isEqualTo("abc");
    assertThat(new String(Base64.decodeBase64("YW*Jj"), StandardCharsets.UTF_8)).isEqualTo("abc");
  }

  // the untyped Codec entry points ---------------------------------------------------------------------

  @Test
  public void shouldEncodeAndDecodeThroughTheObjectApi() {
    Base64 codec = new Base64(0);

    assertThat((byte[]) codec.encode(bytes("abc"))).isEqualTo(bytes("YWJj"));
    assertThat((byte[]) codec.decode(bytes("YWJj"))).isEqualTo(bytes("abc"));
    assertThat((byte[]) codec.decode("YWJj")).isEqualTo(bytes("abc"));
  }

  @Test
  public void shouldRefuseAnythingButByteArrayOrString() {
    Base64 codec = new Base64();

    assertThatThrownBy(() -> codec.encode("not a byte array"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("is not a byte[]");
    assertThatThrownBy(() -> codec.decode(1))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("is not a byte[] or a String");
  }

  // BigInteger flavour -----------------------------------------------------------------------------------

  @Test
  public void shouldRoundTripBigIntegers() {
    BigInteger value = new BigInteger("1234567890123456789012345678901234567890");

    assertThat(Base64.decodeInteger(Base64.encodeInteger(value))).isEqualTo(value);
    assertThat(Base64.decodeInteger(Base64.encodeInteger(BigInteger.ZERO)))
        .isEqualTo(BigInteger.ZERO);
    assertThat(Base64.decodeInteger(Base64.encodeInteger(BigInteger.ONE)))
        .isEqualTo(BigInteger.ONE);
  }

  @Test
  public void shouldRefuseEncodingANullBigInteger() {
    assertThatThrownBy(() -> Base64.encodeInteger(null))
        .isInstanceOf(NullPointerException.class);
  }
}
