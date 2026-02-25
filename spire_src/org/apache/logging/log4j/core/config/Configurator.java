package org.apache.logging.log4j.core.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.util.NetUtils;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Strings;

public final class Configurator {
   private static final String FQCN = Configurator.class.getName();
   private static final Logger LOGGER = StatusLogger.getLogger();

   private static Log4jContextFactory getFactory() {
      LoggerContextFactory factory = LogManager.getFactory();
      if (factory instanceof Log4jContextFactory) {
         return (Log4jContextFactory)factory;
      } else {
         if (factory != null) {
            LOGGER.error(
               "LogManager returned an instance of {} which does not implement {}. Unable to initialize Log4j.",
               factory.getClass().getName(),
               Log4jContextFactory.class.getName()
            );
         } else {
            LOGGER.fatal("LogManager did not return a LoggerContextFactory. This indicates something has gone terribly wrong!");
         }

         return null;
      }
   }

   public static LoggerContext initialize(final ClassLoader loader, final ConfigurationSource source) {
      return initialize(loader, source, null);
   }

   public static LoggerContext initialize(final ClassLoader loader, final ConfigurationSource source, final Object externalContext) {
      try {
         Log4jContextFactory factory = getFactory();
         return factory == null ? null : factory.getContext(FQCN, loader, externalContext, false, source);
      } catch (Exception var4) {
         LOGGER.error("There was a problem obtaining a LoggerContext using the configuration source [{}]", source, var4);
         return null;
      }
   }

   public static LoggerContext initialize(final String name, final ClassLoader loader, final String configLocation) {
      return initialize(name, loader, configLocation, null);
   }

   public static LoggerContext initialize(final String name, final ClassLoader loader, final String configLocation, final Object externalContext) {
      if (Strings.isBlank(configLocation)) {
         return initialize(name, loader, (URI)null, externalContext);
      } else if (configLocation.contains(",")) {
         String[] parts = configLocation.split(",");
         String scheme = null;
         List<URI> uris = new ArrayList<>(parts.length);

         for (String part : parts) {
            URI uri = NetUtils.toURI(scheme != null ? scheme + ":" + part.trim() : part.trim());
            if (scheme == null && uri.getScheme() != null) {
               scheme = uri.getScheme();
            }

            uris.add(uri);
         }

         return initialize(name, loader, uris, externalContext);
      } else {
         return initialize(name, loader, NetUtils.toURI(configLocation), externalContext);
      }
   }

   public static LoggerContext initialize(final String name, final ClassLoader loader, final URI configLocation) {
      return initialize(name, loader, configLocation, null);
   }

   public static LoggerContext initialize(final String name, final ClassLoader loader, final URI configLocation, final Object externalContext) {
      try {
         Log4jContextFactory factory = getFactory();
         return factory == null ? null : factory.getContext(FQCN, loader, externalContext, false, configLocation, name);
      } catch (Exception var5) {
         LOGGER.error("There was a problem initializing the LoggerContext [{}] using configuration at [{}].", name, configLocation, var5);
         return null;
      }
   }

   public static LoggerContext initialize(final String name, final ClassLoader loader, final URI configLocation, final Entry<String, Object> entry) {
      try {
         Log4jContextFactory factory = getFactory();
         return factory == null ? null : factory.getContext(FQCN, loader, entry, false, configLocation, name);
      } catch (Exception var5) {
         LOGGER.error("There was a problem initializing the LoggerContext [{}] using configuration at [{}].", name, configLocation, var5);
         return null;
      }
   }

   public static LoggerContext initialize(final String name, final ClassLoader loader, final List<URI> configLocations, final Object externalContext) {
      try {
         Log4jContextFactory factory = getFactory();
         return factory == null ? null : factory.getContext(FQCN, loader, externalContext, false, configLocations, name);
      } catch (Exception var5) {
         LOGGER.error("There was a problem initializing the LoggerContext [{}] using configurations at [{}].", name, configLocations, var5);
         return null;
      }
   }

   public static LoggerContext initialize(final String name, final String configLocation) {
      return initialize(name, null, configLocation);
   }

   public static LoggerContext initialize(final Configuration configuration) {
      return initialize(null, configuration, null);
   }

   public static LoggerContext initialize(final ClassLoader loader, final Configuration configuration) {
      return initialize(loader, configuration, null);
   }

   public static LoggerContext initialize(final ClassLoader loader, final Configuration configuration, final Object externalContext) {
      try {
         Log4jContextFactory factory = getFactory();
         return factory == null ? null : factory.getContext(FQCN, loader, externalContext, false, configuration);
      } catch (Exception var4) {
         LOGGER.error("There was a problem initializing the LoggerContext using configuration {}", configuration.getName(), var4);
         return null;
      }
   }

   public static void reconfigure(final Configuration configuration) {
      try {
         Log4jContextFactory factory = getFactory();
         if (factory != null) {
            factory.getContext(FQCN, null, null, false).reconfigure(configuration);
         }
      } catch (Exception var2) {
         LOGGER.error("There was a problem initializing the LoggerContext using configuration {}", configuration.getName(), var2);
      }
   }

   public static void reconfigure() {
      try {
         Log4jContextFactory factory = getFactory();
         if (factory != null) {
            factory.getSelector().getContext(FQCN, null, false).reconfigure();
         } else {
            LOGGER.warn("Unable to reconfigure - Log4j has not been initialized.");
         }
      } catch (Exception var1) {
         LOGGER.error("Error encountered trying to reconfigure logging", (Throwable)var1);
      }
   }

   public static void reconfigure(final URI uri) {
      try {
         Log4jContextFactory factory = getFactory();
         if (factory != null) {
            factory.getSelector().getContext(FQCN, null, false).setConfigLocation(uri);
         } else {
            LOGGER.warn("Unable to reconfigure - Log4j has not been initialized.");
         }
      } catch (Exception var2) {
         LOGGER.error("Error encountered trying to reconfigure logging", (Throwable)var2);
      }
   }

   public static void setAllLevels(final String parentLogger, final Level level) {
      LoggerContext loggerContext = LoggerContext.getContext(false);
      Configuration config = loggerContext.getConfiguration();
      boolean set = setLevel(parentLogger, level, config);

      for (Entry<String, LoggerConfig> entry : config.getLoggers().entrySet()) {
         if (entry.getKey().startsWith(parentLogger)) {
            set |= setLevel(entry.getValue(), level);
         }
      }

      if (set) {
         loggerContext.updateLoggers();
      }
   }

   private static boolean setLevel(final LoggerConfig loggerConfig, final Level level) {
      boolean set = !loggerConfig.getLevel().equals(level);
      if (set) {
         loggerConfig.setLevel(level);
      }

      return set;
   }

   public static void setLevel(final Map<String, Level> levelMap) {
      LoggerContext loggerContext = LoggerContext.getContext(false);
      Configuration config = loggerContext.getConfiguration();
      boolean set = false;

      for (Entry<String, Level> entry : levelMap.entrySet()) {
         String loggerName = entry.getKey();
         Level level = entry.getValue();
         set |= setLevel(loggerName, level, config);
      }

      if (set) {
         loggerContext.updateLoggers();
      }
   }

   public static void setLevel(final String loggerName, final Level level) {
      LoggerContext loggerContext = LoggerContext.getContext(false);
      if (Strings.isEmpty(loggerName)) {
         setRootLevel(level);
      } else if (setLevel(loggerName, level, loggerContext.getConfiguration())) {
         loggerContext.updateLoggers();
      }
   }

   private static boolean setLevel(final String loggerName, final Level level, final Configuration config) {
      LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
      boolean set;
      if (!loggerName.equals(loggerConfig.getName())) {
         loggerConfig = new LoggerConfig(loggerName, level, true);
         config.addLogger(loggerName, loggerConfig);
         loggerConfig.setLevel(level);
         set = true;
      } else {
         set = setLevel(loggerConfig, level);
      }

      return set;
   }

   public static void setRootLevel(final Level level) {
      LoggerContext loggerContext = LoggerContext.getContext(false);
      LoggerConfig loggerConfig = loggerContext.getConfiguration().getRootLogger();
      if (!loggerConfig.getLevel().equals(level)) {
         loggerConfig.setLevel(level);
         loggerContext.updateLoggers();
      }
   }

   public static void shutdown(final LoggerContext ctx) {
      if (ctx != null) {
         ctx.stop();
      }
   }

   public static boolean shutdown(final LoggerContext ctx, final long timeout, final TimeUnit timeUnit) {
      return ctx != null ? ctx.stop(timeout, timeUnit) : true;
   }

   private Configurator() {
   }
}
