package org.apache.logging.log4j.core.util;

import java.nio.charset.Charset;

public final class StringEncoder {
   private StringEncoder() {
   }

   public static byte[] toBytes(final String str, final Charset charset) {
      return str != null ? str.getBytes(charset != null ? charset : Charset.defaultCharset()) : null;
   }

   @Deprecated
   public static byte[] encodeSingleByteChars(final CharSequence s) {
      int length = s.length();
      byte[] result = new byte[length];
      encodeString(s, 0, length, result);
      return result;
   }

   @Deprecated
   public static int encodeIsoChars(final CharSequence charArray, int charIndex, final byte[] byteArray, int byteIndex, final int length) {
      int i;
      for (i = 0; i < length; i++) {
         char c = charArray.charAt(charIndex++);
         if (c > 255) {
            break;
         }

         byteArray[byteIndex++] = (byte)c;
      }

      return i;
   }

   @Deprecated
   public static int encodeString(final CharSequence charArray, int charOffset, int charLength, final byte[] byteArray) {
      int byteOffset = 0;
      int length = Math.min(charLength, byteArray.length);
      int charDoneIndex = charOffset + length;

      while (charOffset < charDoneIndex) {
         int done = encodeIsoChars(charArray, charOffset, byteArray, byteOffset, length);
         charOffset += done;
         byteOffset += done;
         if (done != length) {
            char c = charArray.charAt(charOffset++);
            if (Character.isHighSurrogate(c) && charOffset < charDoneIndex && Character.isLowSurrogate(charArray.charAt(charOffset))) {
               if (charLength > byteArray.length) {
                  charDoneIndex++;
                  charLength--;
               }

               charOffset++;
            }

            byteArray[byteOffset++] = 63;
            length = Math.min(charDoneIndex - charOffset, byteArray.length - byteOffset);
         }
      }

      return byteOffset;
   }
}
