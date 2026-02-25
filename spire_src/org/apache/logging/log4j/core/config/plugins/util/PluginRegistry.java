package org.apache.logging.log4j.core.config.plugins.util;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAliases;
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache;
import org.apache.logging.log4j.core.config.plugins.processor.PluginEntry;
import org.apache.logging.log4j.core.util.Loader;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Strings;

public class PluginRegistry {
   private static final Logger LOGGER = StatusLogger.getLogger();
   private static volatile PluginRegistry INSTANCE;
   private static final Object INSTANCE_LOCK = new Object();
   private final AtomicReference<Map<String, List<PluginType<?>>>> pluginsByCategoryRef = new AtomicReference<>();
   private final ConcurrentMap<Long, Map<String, List<PluginType<?>>>> pluginsByCategoryByBundleId = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, Map<String, List<PluginType<?>>>> pluginsByCategoryByPackage = new ConcurrentHashMap<>();

   private PluginRegistry() {
   }

   public static PluginRegistry getInstance() {
      PluginRegistry result = INSTANCE;
      if (result == null) {
         synchronized (INSTANCE_LOCK) {
            result = INSTANCE;
            if (result == null) {
               INSTANCE = result = new PluginRegistry();
            }
         }
      }

      return result;
   }

   public void clear() {
      this.pluginsByCategoryRef.set(null);
      this.pluginsByCategoryByPackage.clear();
      this.pluginsByCategoryByBundleId.clear();
   }

   public Map<Long, Map<String, List<PluginType<?>>>> getPluginsByCategoryByBundleId() {
      return this.pluginsByCategoryByBundleId;
   }

   public Map<String, List<PluginType<?>>> loadFromMainClassLoader() {
      Map<String, List<PluginType<?>>> existing = this.pluginsByCategoryRef.get();
      if (existing != null) {
         return existing;
      } else {
         Map<String, List<PluginType<?>>> newPluginsByCategory = this.decodeCacheFiles(Loader.getClassLoader());
         return this.pluginsByCategoryRef.compareAndSet(null, newPluginsByCategory) ? newPluginsByCategory : this.pluginsByCategoryRef.get();
      }
   }

   public void clearBundlePlugins(final long bundleId) {
      this.pluginsByCategoryByBundleId.remove(bundleId);
   }

   public Map<String, List<PluginType<?>>> loadFromBundle(final long bundleId, final ClassLoader loader) {
      Map<String, List<PluginType<?>>> existing = this.pluginsByCategoryByBundleId.get(bundleId);
      if (existing != null) {
         return existing;
      } else {
         Map<String, List<PluginType<?>>> newPluginsByCategory = this.decodeCacheFiles(loader);
         existing = this.pluginsByCategoryByBundleId.putIfAbsent(bundleId, newPluginsByCategory);
         return existing != null ? existing : newPluginsByCategory;
      }
   }

   private Map<String, List<PluginType<?>>> decodeCacheFiles(final ClassLoader loader) {
      long startTime = System.nanoTime();
      PluginCache cache = new PluginCache();

      try {
         Enumeration<URL> resources = loader.getResources("META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat");
         if (resources == null) {
            LOGGER.info("Plugin preloads not available from class loader {}", loader);
         } else {
            cache.loadCacheFiles(resources);
         }
      } catch (IOException var19) {
         LOGGER.warn("Unable to preload plugins", (Throwable)var19);
      }

      Map<String, List<PluginType<?>>> newPluginsByCategory = new HashMap<>();
      int pluginCount = 0;

      for (Entry<String, Map<String, PluginEntry>> outer : cache.getAllCategories().entrySet()) {
         String categoryLowerCase = outer.getKey();
         List<PluginType<?>> types = new ArrayList<>(outer.getValue().size());
         newPluginsByCategory.put(categoryLowerCase, types);

         for (Entry<String, PluginEntry> inner : outer.getValue().entrySet()) {
            PluginEntry entry = inner.getValue();
            String className = entry.getClassName();

            try {
               Class<?> clazz = loader.loadClass(className);
               PluginType<?> type = new PluginType<>(entry, clazz, entry.getName());
               types.add(type);
               pluginCount++;
            } catch (ClassNotFoundException var17) {
               LOGGER.info("Plugin [{}] could not be loaded due to missing classes.", className, var17);
            } catch (LinkageError var18) {
               LOGGER.info("Plugin [{}] could not be loaded due to linkage error.", className, var18);
            }
         }
      }

      int numPlugins = pluginCount;
      LOGGER.debug(() -> {
         long endTime = System.nanoTime();
         StringBuilder sb = new StringBuilder("Took ");
         DecimalFormat numFormat = new DecimalFormat("#0.000000");
         sb.append(numFormat.format((endTime - startTime) * 1.0E-9));
         sb.append(" seconds to load ").append(numPlugins);
         sb.append(" plugins from ").append(loader);
         return sb.toString();
      });
      return newPluginsByCategory;
   }

   public Map<String, List<PluginType<?>>> loadFromPackage(final String pkg) {
      if (Strings.isBlank(pkg)) {
         return Collections.emptyMap();
      } else {
         Map<String, List<PluginType<?>>> existing = this.pluginsByCategoryByPackage.get(pkg);
         if (existing != null) {
            return existing;
         } else {
            long startTime = System.nanoTime();
            ResolverUtil resolver = new ResolverUtil();
            ClassLoader classLoader = Loader.getClassLoader();
            if (classLoader != null) {
               resolver.setClassLoader(classLoader);
            }

            resolver.findInPackage(new PluginRegistry.PluginTest(), pkg);
            Map<String, List<PluginType<?>>> newPluginsByCategory = new HashMap<>();

            for (Class<?> clazz : resolver.getClasses()) {
               Plugin plugin = clazz.getAnnotation(Plugin.class);
               String categoryLowerCase = plugin.category().toLowerCase();
               List<PluginType<?>> list = newPluginsByCategory.get(categoryLowerCase);
               if (list == null) {
                  newPluginsByCategory.put(categoryLowerCase, list = new ArrayList<>());
               }

               PluginEntry mainEntry = new PluginEntry();
               String mainElementName = plugin.elementType().equals("") ? plugin.name() : plugin.elementType();
               mainEntry.setKey(plugin.name().toLowerCase());
               mainEntry.setName(plugin.name());
               mainEntry.setCategory(plugin.category());
               mainEntry.setClassName(clazz.getName());
               mainEntry.setPrintable(plugin.printObject());
               mainEntry.setDefer(plugin.deferChildren());
               PluginType<?> mainType = new PluginType<>(mainEntry, clazz, mainElementName);
               list.add(mainType);
               PluginAliases pluginAliases = clazz.getAnnotation(PluginAliases.class);
               if (pluginAliases != null) {
                  for (String alias : pluginAliases.value()) {
                     PluginEntry aliasEntry = new PluginEntry();
                     String aliasElementName = plugin.elementType().equals("") ? alias.trim() : plugin.elementType();
                     aliasEntry.setKey(alias.trim().toLowerCase());
                     aliasEntry.setName(plugin.name());
                     aliasEntry.setCategory(plugin.category());
                     aliasEntry.setClassName(clazz.getName());
                     aliasEntry.setPrintable(plugin.printObject());
                     aliasEntry.setDefer(plugin.deferChildren());
                     PluginType<?> aliasType = new PluginType<>(aliasEntry, clazz, aliasElementName);
                     list.add(aliasType);
                  }
               }
            }

            LOGGER.debug(() -> {
               long endTime = System.nanoTime();
               StringBuilder sb = new StringBuilder("Took ");
               DecimalFormat numFormat = new DecimalFormat("#0.000000");
               sb.append(numFormat.format((endTime - startTime) * 1.0E-9));
               sb.append(" seconds to load ").append(resolver.getClasses().size());
               sb.append(" plugins from package ").append(pkg);
               return sb.toString();
            });
            existing = this.pluginsByCategoryByPackage.putIfAbsent(pkg, newPluginsByCategory);
            return existing != null ? existing : newPluginsByCategory;
         }
      }
   }

   public static class PluginTest implements ResolverUtil.Test {
      @Override
      public boolean matches(final Class<?> type) {
         return type != null && type.isAnnotationPresent(Plugin.class);
      }

      @Override
      public String toString() {
         return "annotated with @" + Plugin.class.getSimpleName();
      }

      @Override
      public boolean matches(final URI resource) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean doesMatchClass() {
         return true;
      }

      @Override
      public boolean doesMatchResource() {
         return false;
      }
   }
}
