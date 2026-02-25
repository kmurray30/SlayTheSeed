package org.apache.logging.log4j.core.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFileWatcher;
import org.apache.logging.log4j.core.config.ConfigurationListener;
import org.apache.logging.log4j.core.config.Reconfigurable;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;
import org.apache.logging.log4j.status.StatusLogger;

public class WatcherFactory {
   private static Logger LOGGER = StatusLogger.getLogger();
   private static PluginManager pluginManager = new PluginManager("Watcher");
   private static volatile WatcherFactory factory;
   private final Map<String, PluginType<?>> plugins;

   private WatcherFactory(List<String> packages) {
      pluginManager.collectPlugins(packages);
      this.plugins = pluginManager.getPlugins();
   }

   public static WatcherFactory getInstance(List<String> packages) {
      if (factory == null) {
         synchronized (pluginManager) {
            if (factory == null) {
               factory = new WatcherFactory(packages);
            }
         }
      }

      return factory;
   }

   public Watcher newWatcher(
      Source source,
      final Configuration configuration,
      final Reconfigurable reconfigurable,
      final List<ConfigurationListener> configurationListeners,
      long lastModifiedMillis
   ) {
      if (source.getFile() != null) {
         return new ConfigurationFileWatcher(configuration, reconfigurable, configurationListeners, lastModifiedMillis);
      } else {
         String name = source.getURI().getScheme();
         PluginType<?> pluginType = this.plugins.get(name);
         if (pluginType != null) {
            return instantiate(name, (Class<Watcher>)pluginType.getPluginClass(), configuration, reconfigurable, configurationListeners, lastModifiedMillis);
         } else {
            LOGGER.info("No Watcher plugin is available for protocol '{}'", name);
            return null;
         }
      }
   }

   public static <T extends Watcher> T instantiate(
      String name,
      final Class<T> clazz,
      final Configuration configuration,
      final Reconfigurable reconfigurable,
      final List<ConfigurationListener> listeners,
      long lastModifiedMillis
   ) {
      Objects.requireNonNull(clazz, "No class provided");

      try {
         Constructor<T> constructor = clazz.getConstructor(Configuration.class, Reconfigurable.class, List.class, long.class);
         return constructor.newInstance(configuration, reconfigurable, listeners, lastModifiedMillis);
      } catch (NoSuchMethodException var8) {
         throw new IllegalArgumentException("No valid constructor for Watcher plugin " + name, var8);
      } catch (InstantiationException | LinkageError var9) {
         throw new IllegalArgumentException(var9);
      } catch (IllegalAccessException var10) {
         throw new IllegalStateException(var10);
      } catch (InvocationTargetException var11) {
         Throwables.rethrow(var11.getCause());
         throw new InternalError("Unreachable");
      }
   }
}
