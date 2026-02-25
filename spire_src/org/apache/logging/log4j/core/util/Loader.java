package org.apache.logging.log4j.core.util;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.LoaderUtil;
import org.apache.logging.log4j.util.PropertiesUtil;

public final class Loader {
   private static final Logger LOGGER = StatusLogger.getLogger();
   private static final String TSTR = "Caught Exception while in Loader.getResource. This may be innocuous.";

   private Loader() {
   }

   public static ClassLoader getClassLoader() {
      return getClassLoader(Loader.class, null);
   }

   public static ClassLoader getThreadContextClassLoader() {
      return LoaderUtil.getThreadContextClassLoader();
   }

   public static ClassLoader getClassLoader(final Class<?> class1, final Class<?> class2) {
      ClassLoader threadContextClassLoader = getThreadContextClassLoader();
      ClassLoader loader1 = class1 == null ? null : class1.getClassLoader();
      ClassLoader loader2 = class2 == null ? null : class2.getClassLoader();
      if (isChild(threadContextClassLoader, loader1)) {
         return isChild(threadContextClassLoader, loader2) ? threadContextClassLoader : loader2;
      } else {
         return isChild(loader1, loader2) ? loader1 : loader2;
      }
   }

   public static URL getResource(final String resource, final ClassLoader defaultLoader) {
      try {
         ClassLoader classLoader = getThreadContextClassLoader();
         if (classLoader != null) {
            LOGGER.trace("Trying to find [{}] using context class loader {}.", resource, classLoader);
            URL url = classLoader.getResource(resource);
            if (url != null) {
               return url;
            }
         }

         classLoader = Loader.class.getClassLoader();
         if (classLoader != null) {
            LOGGER.trace("Trying to find [{}] using {} class loader.", resource, classLoader);
            URL url = classLoader.getResource(resource);
            if (url != null) {
               return url;
            }
         }

         if (defaultLoader != null) {
            LOGGER.trace("Trying to find [{}] using {} class loader.", resource, defaultLoader);
            URL url = defaultLoader.getResource(resource);
            if (url != null) {
               return url;
            }
         }
      } catch (Throwable var4) {
         LOGGER.warn("Caught Exception while in Loader.getResource. This may be innocuous.", var4);
      }

      LOGGER.trace("Trying to find [{}] using ClassLoader.getSystemResource().", resource);
      return ClassLoader.getSystemResource(resource);
   }

   public static InputStream getResourceAsStream(final String resource, final ClassLoader defaultLoader) {
      try {
         ClassLoader classLoader = getThreadContextClassLoader();
         if (classLoader != null) {
            LOGGER.trace("Trying to find [{}] using context class loader {}.", resource, classLoader);
            InputStream is = classLoader.getResourceAsStream(resource);
            if (is != null) {
               return is;
            }
         }

         classLoader = Loader.class.getClassLoader();
         if (classLoader != null) {
            LOGGER.trace("Trying to find [{}] using {} class loader.", resource, classLoader);
            InputStream is = classLoader.getResourceAsStream(resource);
            if (is != null) {
               return is;
            }
         }

         if (defaultLoader != null) {
            LOGGER.trace("Trying to find [{}] using {} class loader.", resource, defaultLoader);
            InputStream is = defaultLoader.getResourceAsStream(resource);
            if (is != null) {
               return is;
            }
         }
      } catch (Throwable var4) {
         LOGGER.warn("Caught Exception while in Loader.getResource. This may be innocuous.", var4);
      }

      LOGGER.trace("Trying to find [{}] using ClassLoader.getSystemResource().", resource);
      return ClassLoader.getSystemResourceAsStream(resource);
   }

   private static boolean isChild(final ClassLoader loader1, final ClassLoader loader2) {
      if (loader1 != null && loader2 != null) {
         ClassLoader parent = loader1.getParent();

         while (parent != null && parent != loader2) {
            parent = parent.getParent();
         }

         return parent != null;
      } else {
         return loader1 != null;
      }
   }

   public static Class<?> initializeClass(final String className, final ClassLoader loader) throws ClassNotFoundException {
      return Class.forName(className, true, loader);
   }

   public static Class<?> loadClass(final String className, final ClassLoader loader) throws ClassNotFoundException {
      return loader != null ? loader.loadClass(className) : null;
   }

   public static Class<?> loadSystemClass(final String className) throws ClassNotFoundException {
      try {
         return Class.forName(className, true, ClassLoader.getSystemClassLoader());
      } catch (Throwable var2) {
         LOGGER.trace("Couldn't use SystemClassLoader. Trying Class.forName({}).", className, var2);
         return Class.forName(className);
      }
   }

   public static <T> T newInstanceOf(final String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
      ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

      Object var2;
      try {
         Thread.currentThread().setContextClassLoader(getClassLoader());
         var2 = LoaderUtil.newInstanceOf(className);
      } finally {
         Thread.currentThread().setContextClassLoader(contextClassLoader);
      }

      return (T)var2;
   }

   public static <T> T newCheckedInstanceOf(final String className, final Class<T> clazz) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
      ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

      Object var3;
      try {
         Thread.currentThread().setContextClassLoader(getClassLoader());
         var3 = LoaderUtil.newCheckedInstanceOf(className, clazz);
      } finally {
         Thread.currentThread().setContextClassLoader(contextClassLoader);
      }

      return (T)var3;
   }

   public static <T> T newCheckedInstanceOfProperty(final String propertyName, final Class<T> clazz) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
      String className = PropertiesUtil.getProperties().getStringProperty(propertyName);
      ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

      Object var4;
      try {
         Thread.currentThread().setContextClassLoader(getClassLoader());
         var4 = LoaderUtil.newCheckedInstanceOfProperty(propertyName, clazz);
      } finally {
         Thread.currentThread().setContextClassLoader(contextClassLoader);
      }

      return (T)var4;
   }

   public static boolean isClassAvailable(final String className) {
      ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

      boolean var2;
      try {
         Thread.currentThread().setContextClassLoader(getClassLoader());
         var2 = LoaderUtil.isClassAvailable(className);
      } finally {
         Thread.currentThread().setContextClassLoader(contextClassLoader);
      }

      return var2;
   }

   public static boolean isJansiAvailable() {
      return isClassAvailable("org.fusesource.jansi.AnsiRenderer");
   }

   public static Class<?> loadClass(final String className) throws ClassNotFoundException {
      ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

      Class var2;
      try {
         Thread.currentThread().setContextClassLoader(getClassLoader());
         var2 = LoaderUtil.loadClass(className);
      } finally {
         Thread.currentThread().setContextClassLoader(contextClassLoader);
      }

      return var2;
   }
}
