package org.apache.logging.log4j.core.lookup;

import java.util.Map;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;

@Plugin(name = "main", category = "Lookup")
public class MainMapLookup extends MapLookup {
   static final MapLookup MAIN_SINGLETON = new MapLookup(MapLookup.newMap(0));

   public MainMapLookup() {
   }

   public MainMapLookup(final Map<String, String> map) {
      super(map);
   }

   public static void setMainArguments(final String... args) {
      if (args != null) {
         initMap(args, MAIN_SINGLETON.getMap());
      }
   }

   @Override
   public String lookup(final LogEvent event, final String key) {
      return MAIN_SINGLETON.getMap().get(key);
   }

   @Override
   public String lookup(final String key) {
      return MAIN_SINGLETON.getMap().get(key);
   }
}
