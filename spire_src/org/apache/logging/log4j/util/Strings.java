package org.apache.logging.log4j.util;

import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

public final class Strings {
   private static final ThreadLocal<StringBuilder> tempStr = ThreadLocal.withInitial(StringBuilder::new);
   public static final String EMPTY = "";
   public static final String[] EMPTY_ARRAY = new String[0];
   public static final String LINE_SEPARATOR = PropertiesUtil.getProperties().getStringProperty("line.separator", "\n");

   public static String dquote(final String str) {
      return '"' + str + '"';
   }

   public static boolean isBlank(final String s) {
      if (s != null && !s.isEmpty()) {
         for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
               return false;
            }
         }

         return true;
      } else {
         return true;
      }
   }

   public static boolean isEmpty(final CharSequence cs) {
      return cs == null || cs.length() == 0;
   }

   public static boolean isNotBlank(final String s) {
      return !isBlank(s);
   }

   public static boolean isNotEmpty(final CharSequence cs) {
      return !isEmpty(cs);
   }

   public static String join(final Iterable<?> iterable, final char separator) {
      return iterable == null ? null : join(iterable.iterator(), separator);
   }

   public static String join(final Iterator<?> iterator, final char separator) {
      if (iterator == null) {
         return null;
      } else if (!iterator.hasNext()) {
         return "";
      } else {
         Object first = iterator.next();
         if (!iterator.hasNext()) {
            return Objects.toString(first, "");
         } else {
            StringBuilder buf = new StringBuilder(256);
            if (first != null) {
               buf.append(first);
            }

            while (iterator.hasNext()) {
               buf.append(separator);
               Object obj = iterator.next();
               if (obj != null) {
                  buf.append(obj);
               }
            }

            return buf.toString();
         }
      }
   }

   public static String left(final String str, final int len) {
      if (str == null) {
         return null;
      } else if (len < 0) {
         return "";
      } else {
         return str.length() <= len ? str : str.substring(0, len);
      }
   }

   public static String quote(final String str) {
      return '\'' + str + '\'';
   }

   public static String trimToNull(final String str) {
      String ts = str == null ? null : str.trim();
      return isEmpty(ts) ? null : ts;
   }

   private Strings() {
   }

   public static String toRootUpperCase(final String str) {
      return str.toUpperCase(Locale.ROOT);
   }

   public static String concat(String str1, String str2) {
      if (isEmpty(str1)) {
         return str2;
      } else if (isEmpty(str2)) {
         return str1;
      } else {
         StringBuilder sb = tempStr.get();

         String var3;
         try {
            var3 = sb.append(str1).append(str2).toString();
         } finally {
            sb.setLength(0);
         }

         return var3;
      }
   }

   public static String repeat(final String str, final int count) {
      Objects.requireNonNull(str, "str");
      if (count < 0) {
         throw new IllegalArgumentException("count");
      } else {
         StringBuilder sb = tempStr.get();

         String var7;
         try {
            for (int index = 0; index < count; index++) {
               sb.append(str);
            }

            var7 = sb.toString();
         } finally {
            sb.setLength(0);
         }

         return var7;
      }
   }
}
