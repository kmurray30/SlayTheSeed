package org.apache.commons.codec.net;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.BitSet;
import org.apache.commons.codec.BinaryDecoder;
import org.apache.commons.codec.BinaryEncoder;
import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.StringDecoder;
import org.apache.commons.codec.StringEncoder;
import org.apache.commons.codec.binary.StringUtils;

public class QuotedPrintableCodec implements BinaryEncoder, BinaryDecoder, StringEncoder, StringDecoder {
   private final Charset charset;
   private final boolean strict;
   private static final BitSet PRINTABLE_CHARS = new BitSet(256);
   private static final byte ESCAPE_CHAR = 61;
   private static final byte TAB = 9;
   private static final byte SPACE = 32;
   private static final byte CR = 13;
   private static final byte LF = 10;
   private static final int SAFE_LENGTH = 73;

   public QuotedPrintableCodec() {
      this(Charsets.UTF_8, false);
   }

   public QuotedPrintableCodec(boolean strict) {
      this(Charsets.UTF_8, strict);
   }

   public QuotedPrintableCodec(Charset charset) {
      this(charset, false);
   }

   public QuotedPrintableCodec(Charset charset, boolean strict) {
      this.charset = charset;
      this.strict = strict;
   }

   public QuotedPrintableCodec(String charsetName) throws IllegalCharsetNameException, IllegalArgumentException, UnsupportedCharsetException {
      this(Charset.forName(charsetName), false);
   }

   private static final int encodeQuotedPrintable(int b, ByteArrayOutputStream buffer) {
      buffer.write(61);
      char hex1 = Character.toUpperCase(Character.forDigit(b >> 4 & 15, 16));
      char hex2 = Character.toUpperCase(Character.forDigit(b & 15, 16));
      buffer.write(hex1);
      buffer.write(hex2);
      return 3;
   }

   private static int getUnsignedOctet(int index, byte[] bytes) {
      int b = bytes[index];
      if (b < 0) {
         b += 256;
      }

      return b;
   }

   private static int encodeByte(int b, boolean encode, ByteArrayOutputStream buffer) {
      if (encode) {
         return encodeQuotedPrintable(b, buffer);
      } else {
         buffer.write(b);
         return 1;
      }
   }

   private static boolean isWhitespace(int b) {
      return b == 32 || b == 9;
   }

   public static final byte[] encodeQuotedPrintable(BitSet printable, byte[] bytes) {
      return encodeQuotedPrintable(printable, bytes, false);
   }

   public static final byte[] encodeQuotedPrintable(BitSet printable, byte[] bytes, boolean strict) {
      if (bytes == null) {
         return null;
      } else {
         if (printable == null) {
            printable = PRINTABLE_CHARS;
         }

         ByteArrayOutputStream buffer = new ByteArrayOutputStream();
         if (strict) {
            int pos = 1;

            for (int i = 0; i < bytes.length - 3; i++) {
               int b = getUnsignedOctet(i, bytes);
               if (pos < 73) {
                  pos += encodeByte(b, !printable.get(b), buffer);
               } else {
                  encodeByte(b, !printable.get(b) || isWhitespace(b), buffer);
                  buffer.write(61);
                  buffer.write(13);
                  buffer.write(10);
                  pos = 1;
               }
            }

            int b = getUnsignedOctet(bytes.length - 3, bytes);
            boolean encode = !printable.get(b) || isWhitespace(b) && pos > 68;
            pos += encodeByte(b, encode, buffer);
            if (pos > 71) {
               buffer.write(61);
               buffer.write(13);
               buffer.write(10);
            }

            for (int ix = bytes.length - 2; ix < bytes.length; ix++) {
               b = getUnsignedOctet(ix, bytes);
               encode = !printable.get(b) || ix > bytes.length - 2 && isWhitespace(b);
               encodeByte(b, encode, buffer);
            }
         } else {
            for (byte c : bytes) {
               int b = c;
               if (c < 0) {
                  b = 256 + c;
               }

               if (printable.get(b)) {
                  buffer.write(b);
               } else {
                  encodeQuotedPrintable(b, buffer);
               }
            }
         }

         return buffer.toByteArray();
      }
   }

   public static final byte[] decodeQuotedPrintable(byte[] bytes) throws DecoderException {
      if (bytes == null) {
         return null;
      } else {
         ByteArrayOutputStream buffer = new ByteArrayOutputStream();

         for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i];
            if (b == 61) {
               try {
                  if (bytes[++i] != 13) {
                     int u = Utils.digit16(bytes[i]);
                     int l = Utils.digit16(bytes[++i]);
                     buffer.write((char)((u << 4) + l));
                  }
               } catch (ArrayIndexOutOfBoundsException var6) {
                  throw new DecoderException("Invalid quoted-printable encoding", var6);
               }
            } else if (b != 13 && b != 10) {
               buffer.write(b);
            }
         }

         return buffer.toByteArray();
      }
   }

   @Override
   public byte[] encode(byte[] bytes) {
      return encodeQuotedPrintable(PRINTABLE_CHARS, bytes, this.strict);
   }

   @Override
   public byte[] decode(byte[] bytes) throws DecoderException {
      return decodeQuotedPrintable(bytes);
   }

   @Override
   public String encode(String str) throws EncoderException {
      return this.encode(str, this.getCharset());
   }

   public String decode(String str, Charset charset) throws DecoderException {
      return str == null ? null : new String(this.decode(StringUtils.getBytesUsAscii(str)), charset);
   }

   public String decode(String str, String charset) throws DecoderException, UnsupportedEncodingException {
      return str == null ? null : new String(this.decode(StringUtils.getBytesUsAscii(str)), charset);
   }

   @Override
   public String decode(String str) throws DecoderException {
      return this.decode(str, this.getCharset());
   }

   @Override
   public Object encode(Object obj) throws EncoderException {
      if (obj == null) {
         return null;
      } else if (obj instanceof byte[]) {
         return this.encode((byte[])obj);
      } else if (obj instanceof String) {
         return this.encode((String)obj);
      } else {
         throw new EncoderException("Objects of type " + obj.getClass().getName() + " cannot be quoted-printable encoded");
      }
   }

   @Override
   public Object decode(Object obj) throws DecoderException {
      if (obj == null) {
         return null;
      } else if (obj instanceof byte[]) {
         return this.decode((byte[])obj);
      } else if (obj instanceof String) {
         return this.decode((String)obj);
      } else {
         throw new DecoderException("Objects of type " + obj.getClass().getName() + " cannot be quoted-printable decoded");
      }
   }

   public Charset getCharset() {
      return this.charset;
   }

   public String getDefaultCharset() {
      return this.charset.name();
   }

   public String encode(String str, Charset charset) {
      return str == null ? null : StringUtils.newStringUsAscii(this.encode(str.getBytes(charset)));
   }

   public String encode(String str, String charset) throws UnsupportedEncodingException {
      return str == null ? null : StringUtils.newStringUsAscii(this.encode(str.getBytes(charset)));
   }

   static {
      for (int i = 33; i <= 60; i++) {
         PRINTABLE_CHARS.set(i);
      }

      for (int i = 62; i <= 126; i++) {
         PRINTABLE_CHARS.set(i);
      }

      PRINTABLE_CHARS.set(9);
      PRINTABLE_CHARS.set(32);
   }
}
