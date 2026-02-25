package org.apache.logging.log4j.core.impl;

import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.LoaderUtil;

class ThrowableProxyHelper {
   private ThrowableProxyHelper() {
   }

   static ExtendedStackTraceElement[] toExtendedStackTrace(
      final ThrowableProxy src,
      final Stack<Class<?>> stack,
      final Map<String, ThrowableProxyHelper.CacheEntry> map,
      final StackTraceElement[] rootTrace,
      final StackTraceElement[] stackTrace
   ) {
      int stackLength;
      if (rootTrace != null) {
         int rootIndex = rootTrace.length - 1;

         int stackIndex;
         for (stackIndex = stackTrace.length - 1; rootIndex >= 0 && stackIndex >= 0 && rootTrace[rootIndex].equals(stackTrace[stackIndex]); stackIndex--) {
            rootIndex--;
         }

         src.setCommonElementCount(stackTrace.length - 1 - stackIndex);
         stackLength = stackIndex + 1;
      } else {
         src.setCommonElementCount(0);
         stackLength = stackTrace.length;
      }

      ExtendedStackTraceElement[] extStackTrace = new ExtendedStackTraceElement[stackLength];
      Class<?> clazz = stack.isEmpty() ? null : stack.peek();
      ClassLoader lastLoader = null;

      for (int i = stackLength - 1; i >= 0; i--) {
         StackTraceElement stackTraceElement = stackTrace[i];
         String className = stackTraceElement.getClassName();
         ExtendedClassInfo extClassInfo;
         if (clazz != null && className.equals(clazz.getName())) {
            ThrowableProxyHelper.CacheEntry entry = toCacheEntry(clazz, true);
            extClassInfo = entry.element;
            lastLoader = entry.loader;
            stack.pop();
            clazz = stack.isEmpty() ? null : stack.peek();
         } else {
            ThrowableProxyHelper.CacheEntry cacheEntry = map.get(className);
            if (cacheEntry != null) {
               extClassInfo = cacheEntry.element;
               if (cacheEntry.loader != null) {
                  lastLoader = cacheEntry.loader;
               }
            } else {
               ThrowableProxyHelper.CacheEntry entry = toCacheEntry(loadClass(lastLoader, className), false);
               extClassInfo = entry.element;
               map.put(className, entry);
               if (entry.loader != null) {
                  lastLoader = entry.loader;
               }
            }
         }

         extStackTrace[i] = new ExtendedStackTraceElement(stackTraceElement, extClassInfo);
      }

      return extStackTrace;
   }

   static ThrowableProxy[] toSuppressedProxies(final Throwable thrown, Set<Throwable> suppressedVisited) {
      try {
         Throwable[] suppressed = thrown.getSuppressed();
         if (suppressed != null && suppressed.length != 0) {
            List<ThrowableProxy> proxies = new ArrayList<>(suppressed.length);
            if (suppressedVisited == null) {
               suppressedVisited = new HashSet<>(suppressed.length);
            }

            for (int i = 0; i < suppressed.length; i++) {
               Throwable candidate = suppressed[i];
               if (suppressedVisited.add(candidate)) {
                  proxies.add(new ThrowableProxy(candidate, suppressedVisited));
               }
            }

            return proxies.toArray(new ThrowableProxy[proxies.size()]);
         } else {
            return ThrowableProxy.EMPTY_ARRAY;
         }
      } catch (Exception var6) {
         StatusLogger.getLogger().error(var6);
         return null;
      }
   }

   private static ThrowableProxyHelper.CacheEntry toCacheEntry(final Class<?> callerClass, final boolean exact) {
      String location = "?";
      String version = "?";
      ClassLoader lastLoader = null;
      if (callerClass != null) {
         try {
            CodeSource source = callerClass.getProtectionDomain().getCodeSource();
            if (source != null) {
               URL locationURL = source.getLocation();
               if (locationURL != null) {
                  String str = locationURL.toString().replace('\\', '/');
                  int index = str.lastIndexOf("/");
                  if (index >= 0 && index == str.length() - 1) {
                     index = str.lastIndexOf("/", index - 1);
                  }

                  location = str.substring(index + 1);
               }
            }
         } catch (Exception var10) {
         }

         Package pkg = callerClass.getPackage();
         if (pkg != null) {
            String ver = pkg.getImplementationVersion();
            if (ver != null) {
               version = ver;
            }
         }

         try {
            lastLoader = callerClass.getClassLoader();
         } catch (SecurityException var9) {
            lastLoader = null;
         }
      }

      return new ThrowableProxyHelper.CacheEntry(new ExtendedClassInfo(exact, location, version), lastLoader);
   }

   private static Class<?> loadClass(final ClassLoader lastLoader, final String className) {
      if (lastLoader != null) {
         try {
            Class<?> clazz = lastLoader.loadClass(className);
            if (clazz != null) {
               return clazz;
            }
         } catch (Throwable var6) {
         }
      }

      try {
         Class<?> clazz = LoaderUtil.loadClass(className);
         return clazz;
      } catch (NoClassDefFoundError | ClassNotFoundException var4) {
         return loadClass(className);
      } catch (SecurityException var5) {
         return null;
      }
   }

   private static Class<?> loadClass(final String className) {
      try {
         return Loader.loadClass(className, ThrowableProxyHelper.class.getClassLoader());
      } catch (NoClassDefFoundError | SecurityException | ClassNotFoundException var2) {
         return null;
      }
   }

   static final class CacheEntry {
      private final ExtendedClassInfo element;
      private final ClassLoader loader;

      private CacheEntry(final ExtendedClassInfo element, final ClassLoader loader) {
         this.element = element;
         this.loader = loader;
      }
   }
}
