package org.apache.logging.log4j.core.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.lookup.Interpolator;
import org.apache.logging.log4j.core.lookup.MapLookup;
import org.apache.logging.log4j.core.lookup.StrLookup;

@Plugin(name = "properties", category = "Core", printObject = true)
public final class PropertiesPlugin {
   private PropertiesPlugin() {
   }

   @PluginFactory
   public static StrLookup configureSubstitutor(@PluginElement("Properties") final Property[] properties, @PluginConfiguration final Configuration config) {
      if (properties == null) {
         return new Interpolator(config.getProperties());
      } else {
         Map<String, String> map = new HashMap<>(config.getProperties());

         for (Property prop : properties) {
            map.put(prop.getName(), prop.getValue());
         }

         return new Interpolator(new MapLookup(map), config.getPluginPackages());
      }
   }
}
