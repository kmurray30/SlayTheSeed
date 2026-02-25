package org.apache.logging.log4j;

import java.net.URI;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import org.apache.logging.log4j.internal.LogManagerStatus;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.StringFormatterMessageFactory;
import org.apache.logging.log4j.simple.SimpleLoggerContextFactory;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.apache.logging.log4j.spi.Provider;
import org.apache.logging.log4j.spi.Terminable;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.LoaderUtil;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.ProviderUtil;
import org.apache.logging.log4j.util.StackLocatorUtil;

public class LogManager {
   public static final String FACTORY_PROPERTY_NAME = "log4j2.loggerContextFactory";
   public static final String ROOT_LOGGER_NAME = "";
   private static final Logger LOGGER = StatusLogger.getLogger();
   private static final String FQCN = LogManager.class.getName();
   private static volatile LoggerContextFactory factory;

   protected LogManager() {
   }

   public static boolean exists(final String name) {
      return getContext().hasLogger(name);
   }

   public static LoggerContext getContext() {
      try {
         return factory.getContext(FQCN, null, null, true);
      } catch (IllegalStateException var1) {
         LOGGER.warn(var1.getMessage() + " Using SimpleLogger");
         return new SimpleLoggerContextFactory().getContext(FQCN, null, null, true);
      }
   }

   public static LoggerContext getContext(final boolean currentContext) {
      try {
         return factory.getContext(FQCN, null, null, currentContext, null, null);
      } catch (IllegalStateException var2) {
         LOGGER.warn(var2.getMessage() + " Using SimpleLogger");
         return new SimpleLoggerContextFactory().getContext(FQCN, null, null, currentContext, null, null);
      }
   }

   public static LoggerContext getContext(final ClassLoader loader, final boolean currentContext) {
      try {
         return factory.getContext(FQCN, loader, null, currentContext);
      } catch (IllegalStateException var3) {
         LOGGER.warn(var3.getMessage() + " Using SimpleLogger");
         return new SimpleLoggerContextFactory().getContext(FQCN, loader, null, currentContext);
      }
   }

   public static LoggerContext getContext(final ClassLoader loader, final boolean currentContext, final Object externalContext) {
      try {
         return factory.getContext(FQCN, loader, externalContext, currentContext);
      } catch (IllegalStateException var4) {
         LOGGER.warn(var4.getMessage() + " Using SimpleLogger");
         return new SimpleLoggerContextFactory().getContext(FQCN, loader, externalContext, currentContext);
      }
   }

   public static LoggerContext getContext(final ClassLoader loader, final boolean currentContext, final URI configLocation) {
      try {
         return factory.getContext(FQCN, loader, null, currentContext, configLocation, null);
      } catch (IllegalStateException var4) {
         LOGGER.warn(var4.getMessage() + " Using SimpleLogger");
         return new SimpleLoggerContextFactory().getContext(FQCN, loader, null, currentContext, configLocation, null);
      }
   }

   public static LoggerContext getContext(final ClassLoader loader, final boolean currentContext, final Object externalContext, final URI configLocation) {
      try {
         return factory.getContext(FQCN, loader, externalContext, currentContext, configLocation, null);
      } catch (IllegalStateException var5) {
         LOGGER.warn(var5.getMessage() + " Using SimpleLogger");
         return new SimpleLoggerContextFactory().getContext(FQCN, loader, externalContext, currentContext, configLocation, null);
      }
   }

   public static LoggerContext getContext(
      final ClassLoader loader, final boolean currentContext, final Object externalContext, final URI configLocation, final String name
   ) {
      try {
         return factory.getContext(FQCN, loader, externalContext, currentContext, configLocation, name);
      } catch (IllegalStateException var6) {
         LOGGER.warn(var6.getMessage() + " Using SimpleLogger");
         return new SimpleLoggerContextFactory().getContext(FQCN, loader, externalContext, currentContext, configLocation, name);
      }
   }

   protected static LoggerContext getContext(final String fqcn, final boolean currentContext) {
      try {
         return factory.getContext(fqcn, null, null, currentContext);
      } catch (IllegalStateException var3) {
         LOGGER.warn(var3.getMessage() + " Using SimpleLogger");
         return new SimpleLoggerContextFactory().getContext(fqcn, null, null, currentContext);
      }
   }

   protected static LoggerContext getContext(final String fqcn, final ClassLoader loader, final boolean currentContext) {
      try {
         return factory.getContext(fqcn, loader, null, currentContext);
      } catch (IllegalStateException var4) {
         LOGGER.warn(var4.getMessage() + " Using SimpleLogger");
         return new SimpleLoggerContextFactory().getContext(fqcn, loader, null, currentContext);
      }
   }

   protected static LoggerContext getContext(
      final String fqcn, final ClassLoader loader, final boolean currentContext, final URI configLocation, final String name
   ) {
      try {
         return factory.getContext(fqcn, loader, null, currentContext, configLocation, name);
      } catch (IllegalStateException var6) {
         LOGGER.warn(var6.getMessage() + " Using SimpleLogger");
         return new SimpleLoggerContextFactory().getContext(fqcn, loader, null, currentContext);
      }
   }

   public static void shutdown() {
      shutdown(false);
   }

   public static void shutdown(final boolean currentContext) {
      factory.shutdown(FQCN, null, currentContext, false);
   }

   public static void shutdown(final boolean currentContext, final boolean allContexts) {
      factory.shutdown(FQCN, null, currentContext, allContexts);
   }

   public static void shutdown(final LoggerContext context) {
      if (context instanceof Terminable) {
         ((Terminable)context).terminate();
      }
   }

   public static LoggerContextFactory getFactory() {
      return factory;
   }

   public static void setFactory(final LoggerContextFactory factory) {
      LogManager.factory = factory;
   }

   public static Logger getFormatterLogger() {
      return getFormatterLogger(StackLocatorUtil.getCallerClass(2));
   }

   public static Logger getFormatterLogger(final Class<?> clazz) {
      return getLogger(clazz != null ? clazz : StackLocatorUtil.getCallerClass(2), StringFormatterMessageFactory.INSTANCE);
   }

   public static Logger getFormatterLogger(final Object value) {
      return getLogger(value != null ? value.getClass() : StackLocatorUtil.getCallerClass(2), StringFormatterMessageFactory.INSTANCE);
   }

   public static Logger getFormatterLogger(final String name) {
      return name == null ? getFormatterLogger(StackLocatorUtil.getCallerClass(2)) : getLogger(name, StringFormatterMessageFactory.INSTANCE);
   }

   private static Class<?> callerClass(final Class<?> clazz) {
      if (clazz != null) {
         return clazz;
      } else {
         Class<?> candidate = StackLocatorUtil.getCallerClass(3);
         if (candidate == null) {
            throw new UnsupportedOperationException("No class provided, and an appropriate one cannot be found.");
         } else {
            return candidate;
         }
      }
   }

   public static Logger getLogger() {
      return getLogger(StackLocatorUtil.getCallerClass(2));
   }

   public static Logger getLogger(final Class<?> clazz) {
      Class<?> cls = callerClass(clazz);
      return getContext(cls.getClassLoader(), false).getLogger(cls);
   }

   public static Logger getLogger(final Class<?> clazz, final MessageFactory messageFactory) {
      Class<?> cls = callerClass(clazz);
      return getContext(cls.getClassLoader(), false).getLogger(cls, messageFactory);
   }

   public static Logger getLogger(final MessageFactory messageFactory) {
      return getLogger(StackLocatorUtil.getCallerClass(2), messageFactory);
   }

   public static Logger getLogger(final Object value) {
      return getLogger(value != null ? value.getClass() : StackLocatorUtil.getCallerClass(2));
   }

   public static Logger getLogger(final Object value, final MessageFactory messageFactory) {
      return getLogger(value != null ? value.getClass() : StackLocatorUtil.getCallerClass(2), messageFactory);
   }

   public static Logger getLogger(final String name) {
      return (Logger)(name != null ? getContext(false).getLogger(name) : getLogger(StackLocatorUtil.getCallerClass(2)));
   }

   public static Logger getLogger(final String name, final MessageFactory messageFactory) {
      return (Logger)(name != null ? getContext(false).getLogger(name, messageFactory) : getLogger(StackLocatorUtil.getCallerClass(2), messageFactory));
   }

   protected static Logger getLogger(final String fqcn, final String name) {
      return factory.getContext(fqcn, null, null, false).getLogger(name);
   }

   public static Logger getRootLogger() {
      return getLogger("");
   }

   static {
      PropertiesUtil managerProps = PropertiesUtil.getProperties();
      String factoryClassName = managerProps.getStringProperty("log4j2.loggerContextFactory");
      if (factoryClassName != null) {
         try {
            factory = LoaderUtil.newCheckedInstanceOf(factoryClassName, LoggerContextFactory.class);
         } catch (ClassNotFoundException var8) {
            LOGGER.error("Unable to locate configured LoggerContextFactory {}", factoryClassName);
         } catch (Exception var9) {
            LOGGER.error("Unable to create configured LoggerContextFactory {}", factoryClassName, var9);
         }
      }

      if (factory == null) {
         SortedMap<Integer, LoggerContextFactory> factories = new TreeMap<>();
         if (!ProviderUtil.hasProviders()) {
            LOGGER.error("Log4j2 could not find a logging implementation. Please add log4j-core to the classpath. Using SimpleLogger to log to the console...");
            factory = new SimpleLoggerContextFactory();
         } else {
            for (Provider provider : ProviderUtil.getProviders()) {
               Class<? extends LoggerContextFactory> factoryClass = provider.loadLoggerContextFactory();
               if (factoryClass != null) {
                  try {
                     factories.put(provider.getPriority(), factoryClass.newInstance());
                  } catch (Exception var7) {
                     LOGGER.error("Unable to create class {} specified in provider URL {}", factoryClass.getName(), provider.getUrl(), var7);
                  }
               }
            }

            if (factories.isEmpty()) {
               LOGGER.error(
                  "Log4j2 could not find a logging implementation. Please add log4j-core to the classpath. Using SimpleLogger to log to the console..."
               );
               factory = new SimpleLoggerContextFactory();
            } else if (factories.size() == 1) {
               factory = factories.get(factories.lastKey());
            } else {
               StringBuilder sb = new StringBuilder("Multiple logging implementations found: \n");

               for (Entry<Integer, LoggerContextFactory> entry : factories.entrySet()) {
                  sb.append("Factory: ").append(entry.getValue().getClass().getName());
                  sb.append(", Weighting: ").append(entry.getKey()).append('\n');
               }

               factory = factories.get(factories.lastKey());
               sb.append("Using factory: ").append(factory.getClass().getName());
               LOGGER.warn(sb.toString());
            }
         }

         LogManagerStatus.setInitialized(true);
      }
   }
}
