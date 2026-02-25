package org.apache.logging.log4j.spi;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Properties;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;

public class Provider {
   public static final String FACTORY_PRIORITY = "FactoryPriority";
   public static final String THREAD_CONTEXT_MAP = "ThreadContextMap";
   public static final String LOGGER_CONTEXT_FACTORY = "LoggerContextFactory";
   private static final Integer DEFAULT_PRIORITY = -1;
   private static final Logger LOGGER = StatusLogger.getLogger();
   private final Integer priority;
   private final String className;
   private final Class<? extends LoggerContextFactory> loggerContextFactoryClass;
   private final String threadContextMap;
   private final Class<? extends ThreadContextMap> threadContextMapClass;
   private final String versions;
   private final URL url;
   private final WeakReference<ClassLoader> classLoader;

   public Provider(final Properties props, final URL url, final ClassLoader classLoader) {
      this.url = url;
      this.classLoader = new WeakReference<>(classLoader);
      String weight = props.getProperty("FactoryPriority");
      this.priority = weight == null ? DEFAULT_PRIORITY : Integer.valueOf(weight);
      this.className = props.getProperty("LoggerContextFactory");
      this.threadContextMap = props.getProperty("ThreadContextMap");
      this.loggerContextFactoryClass = null;
      this.threadContextMapClass = null;
      this.versions = null;
   }

   public Provider(final Integer priority, final String versions, final Class<? extends LoggerContextFactory> loggerContextFactoryClass) {
      this(priority, versions, loggerContextFactoryClass, null);
   }

   public Provider(
      final Integer priority,
      final String versions,
      final Class<? extends LoggerContextFactory> loggerContextFactoryClass,
      final Class<? extends ThreadContextMap> threadContextMapClass
   ) {
      this.url = null;
      this.classLoader = null;
      this.priority = priority;
      this.loggerContextFactoryClass = loggerContextFactoryClass;
      this.threadContextMapClass = threadContextMapClass;
      this.className = null;
      this.threadContextMap = null;
      this.versions = versions;
   }

   public String getVersions() {
      return this.versions;
   }

   public Integer getPriority() {
      return this.priority;
   }

   public String getClassName() {
      return this.loggerContextFactoryClass != null ? this.loggerContextFactoryClass.getName() : this.className;
   }

   public Class<? extends LoggerContextFactory> loadLoggerContextFactory() {
      if (this.loggerContextFactoryClass != null) {
         return this.loggerContextFactoryClass;
      } else if (this.className == null) {
         return null;
      } else {
         ClassLoader loader = this.classLoader.get();
         if (loader == null) {
            return null;
         } else {
            try {
               Class<?> clazz = loader.loadClass(this.className);
               if (LoggerContextFactory.class.isAssignableFrom(clazz)) {
                  return clazz.asSubclass(LoggerContextFactory.class);
               }
            } catch (Exception var3) {
               LOGGER.error("Unable to create class {} specified in {}", this.className, this.url.toString(), var3);
            }

            return null;
         }
      }
   }

   public String getThreadContextMap() {
      return this.threadContextMapClass != null ? this.threadContextMapClass.getName() : this.threadContextMap;
   }

   public Class<? extends ThreadContextMap> loadThreadContextMap() {
      if (this.threadContextMapClass != null) {
         return this.threadContextMapClass;
      } else if (this.threadContextMap == null) {
         return null;
      } else {
         ClassLoader loader = this.classLoader.get();
         if (loader == null) {
            return null;
         } else {
            try {
               Class<?> clazz = loader.loadClass(this.threadContextMap);
               if (ThreadContextMap.class.isAssignableFrom(clazz)) {
                  return clazz.asSubclass(ThreadContextMap.class);
               }
            } catch (Exception var3) {
               LOGGER.error("Unable to create class {} specified in {}", this.threadContextMap, this.url.toString(), var3);
            }

            return null;
         }
      }
   }

   public URL getUrl() {
      return this.url;
   }

   @Override
   public String toString() {
      StringBuilder result = new StringBuilder("Provider[");
      if (!DEFAULT_PRIORITY.equals(this.priority)) {
         result.append("priority=").append(this.priority).append(", ");
      }

      if (this.threadContextMap != null) {
         result.append("threadContextMap=").append(this.threadContextMap).append(", ");
      } else if (this.threadContextMapClass != null) {
         result.append("threadContextMapClass=").append(this.threadContextMapClass.getName());
      }

      if (this.className != null) {
         result.append("className=").append(this.className).append(", ");
      } else if (this.loggerContextFactoryClass != null) {
         result.append("class=").append(this.loggerContextFactoryClass.getName());
      }

      if (this.url != null) {
         result.append("url=").append(this.url);
      }

      ClassLoader loader;
      if (this.classLoader != null && (loader = this.classLoader.get()) != null) {
         result.append(", classLoader=").append(loader);
      } else {
         result.append(", classLoader=null(not reachable)");
      }

      result.append("]");
      return result.toString();
   }

   @Override
   public boolean equals(final Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Provider provider = (Provider)o;
         if (this.priority != null ? this.priority.equals(provider.priority) : provider.priority == null) {
            if (this.className != null ? this.className.equals(provider.className) : provider.className == null) {
               if (this.loggerContextFactoryClass != null
                  ? this.loggerContextFactoryClass.equals(provider.loggerContextFactoryClass)
                  : provider.loggerContextFactoryClass == null) {
                  return this.versions != null ? this.versions.equals(provider.versions) : provider.versions == null;
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      int result = this.priority != null ? this.priority.hashCode() : 0;
      result = 31 * result + (this.className != null ? this.className.hashCode() : 0);
      result = 31 * result + (this.loggerContextFactoryClass != null ? this.loggerContextFactoryClass.hashCode() : 0);
      return 31 * result + (this.versions != null ? this.versions.hashCode() : 0);
   }
}
