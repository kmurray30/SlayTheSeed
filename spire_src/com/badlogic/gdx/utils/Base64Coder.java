package com.badlogic.gdx.utils;

public class Base64Coder {
   private static final String systemLineSeparator = "\n";
   public static final Base64Coder.CharMap regularMap = new Base64Coder.CharMap('+', '/');
   public static final Base64Coder.CharMap urlsafeMap = new Base64Coder.CharMap('-', '_');

   public static String encodeString(String s) {
      return encodeString(s, false);
   }

   public static String encodeString(String s, boolean useUrlsafeEncoding) {
      return new String(encode(s.getBytes(), useUrlsafeEncoding ? urlsafeMap.encodingMap : regularMap.encodingMap));
   }

   public static String encodeLines(byte[] in) {
      return encodeLines(in, 0, in.length, 76, "\n", regularMap.encodingMap);
   }

   public static String encodeLines(byte[] in, int iOff, int iLen, int lineLen, String lineSeparator, Base64Coder.CharMap charMap) {
      return encodeLines(in, iOff, iLen, lineLen, lineSeparator, charMap.encodingMap);
   }

   public static String encodeLines(byte[] in, int iOff, int iLen, int lineLen, String lineSeparator, char[] charMap) {
      int blockLen = lineLen * 3 / 4;
      if (blockLen <= 0) {
         throw new IllegalArgumentException();
      } else {
         int lines = (iLen + blockLen - 1) / blockLen;
         int bufLen = (iLen + 2) / 3 * 4 + lines * lineSeparator.length();
         StringBuilder buf = new StringBuilder(bufLen);
         int ip = 0;

         while (ip < iLen) {
            int l = Math.min(iLen - ip, blockLen);
            buf.append(encode(in, iOff + ip, l, charMap));
            buf.append(lineSeparator);
            ip += l;
         }

         return buf.toString();
      }
   }

   public static char[] encode(byte[] in) {
      return encode(in, regularMap.encodingMap);
   }

   public static char[] encode(byte[] in, Base64Coder.CharMap charMap) {
      return encode(in, 0, in.length, charMap);
   }

   public static char[] encode(byte[] in, char[] charMap) {
      return encode(in, 0, in.length, charMap);
   }

   public static char[] encode(byte[] in, int iLen) {
      return encode(in, 0, iLen, regularMap.encodingMap);
   }

   public static char[] encode(byte[] in, int iOff, int iLen, Base64Coder.CharMap charMap) {
      return encode(in, iOff, iLen, charMap.encodingMap);
   }

   public static char[] encode(byte[] in, int iOff, int iLen, char[] charMap) {
      int oDataLen = (iLen * 4 + 2) / 3;
      int oLen = (iLen + 2) / 3 * 4;
      char[] out = new char[oLen];
      int ip = iOff;
      int iEnd = iOff + iLen;

      for (int op = 0; ip < iEnd; op++) {
         int i0 = in[ip++] & 255;
         int i1 = ip < iEnd ? in[ip++] & 255 : 0;
         int i2 = ip < iEnd ? in[ip++] & 255 : 0;
         int o0 = i0 >>> 2;
         int o1 = (i0 & 3) << 4 | i1 >>> 4;
         int o2 = (i1 & 15) << 2 | i2 >>> 6;
         int o3 = i2 & 63;
         out[op++] = charMap[o0];
         out[op++] = charMap[o1];
         out[op] = op < oDataLen ? charMap[o2] : 61;
         out[++op] = op < oDataLen ? charMap[o3] : 61;
      }

      return out;
   }

   public static String decodeString(String s) {
      return decodeString(s, false);
   }

   public static String decodeString(String s, boolean useUrlSafeEncoding) {
      return new String(decode(s.toCharArray(), useUrlSafeEncoding ? urlsafeMap.decodingMap : regularMap.decodingMap));
   }

   public static byte[] decodeLines(String s) {
      return decodeLines(s, regularMap.decodingMap);
   }

   public static byte[] decodeLines(String s, Base64Coder.CharMap inverseCharMap) {
      return decodeLines(s, inverseCharMap.decodingMap);
   }

   public static byte[] decodeLines(String s, byte[] inverseCharMap) {
      char[] buf = new char[s.length()];
      int p = 0;

      for (int ip = 0; ip < s.length(); ip++) {
         char c = s.charAt(ip);
         if (c != ' ' && c != '\r' && c != '\n' && c != '\t') {
            buf[p++] = c;
         }
      }

      return decode(buf, 0, p, inverseCharMap);
   }

   public static byte[] decode(String s) {
      return decode(s.toCharArray());
   }

   public static byte[] decode(String s, Base64Coder.CharMap inverseCharMap) {
      return decode(s.toCharArray(), inverseCharMap);
   }

   public static byte[] decode(char[] in, byte[] inverseCharMap) {
      return decode(in, 0, in.length, inverseCharMap);
   }

   public static byte[] decode(char[] in, Base64Coder.CharMap inverseCharMap) {
      return decode(in, 0, in.length, inverseCharMap);
   }

   public static byte[] decode(char[] in) {
      return decode(in, 0, in.length, regularMap.decodingMap);
   }

   public static byte[] decode(char[] in, int iOff, int iLen, Base64Coder.CharMap inverseCharMap) {
      return decode(in, iOff, iLen, inverseCharMap.decodingMap);
   }

   public static byte[] decode(char[] in, int iOff, int iLen, byte[] inverseCharMap) {
      if (iLen % 4 != 0) {
         throw new IllegalArgumentException("Length of Base64 encoded input string is not a multiple of 4.");
      } else {
         while (iLen > 0 && in[iOff + iLen - 1] == '=') {
            iLen--;
         }

         int oLen = iLen * 3 / 4;
         byte[] out = new byte[oLen];
         int ip = iOff;
         int iEnd = iOff + iLen;
         int op = 0;

         while (ip < iEnd) {
            int i0 = in[ip++];
            int i1 = in[ip++];
            int i2 = ip < iEnd ? in[ip++] : 65;
            int i3 = ip < iEnd ? in[ip++] : 65;
            if (i0 <= 127 && i1 <= 127 && i2 <= 127 && i3 <= 127) {
               int b0 = inverseCharMap[i0];
               int b1 = inverseCharMap[i1];
               int b2 = inverseCharMap[i2];
               int b3 = inverseCharMap[i3];
               if (b0 >= 0 && b1 >= 0 && b2 >= 0 && b3 >= 0) {
                  int o0 = b0 << 2 | b1 >>> 4;
                  int o1 = (b1 & 15) << 4 | b2 >>> 2;
                  int o2 = (b2 & 3) << 6 | b3;
                  out[op++] = (byte)o0;
                  if (op < oLen) {
                     out[op++] = (byte)o1;
                  }

                  if (op < oLen) {
                     out[op++] = (byte)o2;
                  }
                  continue;
               }

               throw new IllegalArgumentException("Illegal character in Base64 encoded data.");
            }

            throw new IllegalArgumentException("Illegal character in Base64 encoded data.");
         }

         return out;
      }
   }

   private Base64Coder() {
   }

   public static class CharMap {
      protected final char[] encodingMap = new char[64];
      protected final byte[] decodingMap = new byte[128];

      public CharMap(char char63, char char64) {
         int i = 0;
         char c = 'A';

         while (c <= 'Z') {
            this.encodingMap[i++] = c++;
         }

         c = 'a';

         while (c <= 'z') {
            this.encodingMap[i++] = c++;
         }

         c = '0';

         while (c <= '9') {
            this.encodingMap[i++] = c++;
         }

         this.encodingMap[i++] = char63;
         this.encodingMap[i++] = char64;

         for (int var7 = 0; var7 < this.decodingMap.length; var7++) {
            this.decodingMap[var7] = -1;
         }

         for (int var8 = 0; var8 < 64; var8++) {
            this.decodingMap[this.encodingMap[var8]] = (byte)var8;
         }
      }

      public byte[] getDecodingMap() {
         return this.decodingMap;
      }

      public char[] getEncodingMap() {
         return this.encodingMap;
      }
   }
}
