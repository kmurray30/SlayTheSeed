package org.apache.logging.log4j.core.config.plugins.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

public final class PluginUtil {
   private PluginUtil() {
   }

   public static Map<String, PluginType<?>> collectPluginsByCategory(final String category) {
      Objects.requireNonNull(category, "category");
      return collectPluginsByCategoryAndPackage(category, Collections.emptyList());
   }

   public static Map<String, PluginType<?>> collectPluginsByCategoryAndPackage(final String category, final List<String> packages) {
      Objects.requireNonNull(category, "category");
      Objects.requireNonNull(packages, "packages");
      PluginManager pluginManager = new PluginManager(category);
      pluginManager.collectPlugins(packages);
      return pluginManager.getPlugins();
   }

   public static <V> V instantiatePlugin(Class<V> pluginClass) {
      Objects.requireNonNull(pluginClass, "pluginClass");
      Method pluginFactoryMethod = findPluginFactoryMethod(pluginClass);

      try {
         return (V)pluginFactoryMethod.invoke(null);
      } catch (InvocationTargetException | IllegalAccessException var4) {
         String message = String.format("failed to instantiate plugin of type %s using the factory method %s", pluginClass, pluginFactoryMethod);
         throw new IllegalStateException(message, var4);
      }
   }

   public static Method findPluginFactoryMethod(final Class<?> pluginClass) {
      Objects.requireNonNull(pluginClass, "pluginClass");

      for (Method method : pluginClass.getDeclaredMethods()) {
         boolean methodAnnotated = method.isAnnotationPresent(PluginFactory.class);
         if (methodAnnotated) {
            boolean methodStatic = Modifier.isStatic(method.getModifiers());
            if (methodStatic) {
               return method;
            }
         }
      }

      throw new IllegalStateException("no factory method found for class " + pluginClass);
   }
}
