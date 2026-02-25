package org.apache.logging.log4j.util;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Objects;

final class LowLevelLogUtil {
   private static PrintWriter writer = new PrintWriter(System.err, true);

   public static void log(final String message) {
      if (message != null) {
         writer.println(message);
      }
   }

   public static void logException(final Throwable exception) {
      if (exception != null) {
         exception.printStackTrace(writer);
      }
   }

   public static void logException(final String message, final Throwable exception) {
      log(message);
      logException(exception);
   }

   public static void setOutputStream(final OutputStream out) {
      writer = new PrintWriter(Objects.requireNonNull(out), true);
   }

   public static void setWriter(final Writer writer) {
      LowLevelLogUtil.writer = new PrintWriter(Objects.requireNonNull(writer), true);
   }

   private LowLevelLogUtil() {
   }
}
