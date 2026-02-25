package org.apache.logging.log4j.util;

import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.function.Predicate;
import org.apache.logging.log4j.status.StatusLogger;

public final class StackLocatorUtil {
   private static StackLocator stackLocator = null;
   private static volatile boolean errorLogged;

   private StackLocatorUtil() {
   }

   @PerformanceSensitive
   public static Class<?> getCallerClass(final int depth) {
      return stackLocator.getCallerClass(depth + 1);
   }

   public static StackTraceElement getStackTraceElement(final int depth) {
      return stackLocator.getStackTraceElement(depth + 1);
   }

   @PerformanceSensitive
   public static Class<?> getCallerClass(final String fqcn) {
      return getCallerClass(fqcn, "");
   }

   @PerformanceSensitive
   public static Class<?> getCallerClass(final String fqcn, final String pkg) {
      return stackLocator.getCallerClass(fqcn, pkg);
   }

   @PerformanceSensitive
   public static Class<?> getCallerClass(final Class<?> sentinelClass, final Predicate<Class<?>> callerPredicate) {
      return stackLocator.getCallerClass(sentinelClass, callerPredicate);
   }

   @PerformanceSensitive
   public static Class<?> getCallerClass(final Class<?> anchor) {
      return stackLocator.getCallerClass(anchor);
   }

   @PerformanceSensitive
   public static Stack<Class<?>> getCurrentStackTrace() {
      return stackLocator.getCurrentStackTrace();
   }

   public static StackTraceElement calcLocation(final String fqcnOfLogger) {
      try {
         return stackLocator.calcLocation(fqcnOfLogger);
      } catch (NoSuchElementException var2) {
         if (!errorLogged) {
            errorLogged = true;
            StatusLogger.getLogger().warn("Unable to locate stack trace element for {}", fqcnOfLogger, var2);
         }

         return null;
      }
   }

   static {
      stackLocator = StackLocator.getInstance();
   }
}
