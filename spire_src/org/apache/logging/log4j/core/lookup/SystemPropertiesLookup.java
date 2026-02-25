package org.apache.logging.log4j.core.lookup;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.status.StatusLogger;

@Plugin(name = "sys", category = "Lookup")
public class SystemPropertiesLookup extends AbstractLookup {
   private static final Logger LOGGER = StatusLogger.getLogger();
   private static final Marker LOOKUP = MarkerManager.getMarker("LOOKUP");

   @Override
   public String lookup(final LogEvent event, final String key) {
      try {
         return System.getProperty(key);
      } catch (Exception var4) {
         LOGGER.warn(LOOKUP, "Error while getting system property [{}].", key, var4);
         return null;
      }
   }
}
