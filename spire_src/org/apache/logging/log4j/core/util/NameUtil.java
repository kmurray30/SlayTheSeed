package org.apache.logging.log4j.core.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.apache.logging.log4j.util.Strings;

public final class NameUtil {
   private NameUtil() {
   }

   public static String getSubName(final String name) {
      if (Strings.isEmpty(name)) {
         return null;
      } else {
         int i = name.lastIndexOf(46);
         return i > 0 ? name.substring(0, i) : "";
      }
   }

   public static String md5(final String input) {
      Objects.requireNonNull(input, "input");

      try {
         byte[] inputBytes = input.getBytes();
         MessageDigest digest = MessageDigest.getInstance("MD5");
         byte[] bytes = digest.digest(inputBytes);
         StringBuilder md5 = new StringBuilder(bytes.length * 2);

         for (byte b : bytes) {
            String hex = Integer.toHexString(255 & b);
            if (hex.length() == 1) {
               md5.append('0');
            }

            md5.append(hex);
         }

         return md5.toString();
      } catch (NoSuchAlgorithmException var10) {
         throw new RuntimeException(var10);
      }
   }
}
