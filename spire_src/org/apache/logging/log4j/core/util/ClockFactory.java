package org.apache.logging.log4j.core.util;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.core.time.PreciseClock;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.Supplier;

public final class ClockFactory {
   public static final String PROPERTY_NAME = "log4j.Clock";
   private static final StatusLogger LOGGER = StatusLogger.getLogger();

   private ClockFactory() {
   }

   public static Clock getClock() {
      return createClock();
   }

   private static Map<String, Supplier<Clock>> aliases() {
      Map<String, Supplier<Clock>> result = new HashMap<>();
      result.put("SystemClock", SystemClock::new);
      result.put("SystemMillisClock", SystemMillisClock::new);
      result.put("CachedClock", CachedClock::instance);
      result.put("CoarseCachedClock", CoarseCachedClock::instance);
      result.put("org.apache.logging.log4j.core.util.CachedClock", CachedClock::instance);
      result.put("org.apache.logging.log4j.core.util.CoarseCachedClock", CoarseCachedClock::instance);
      return result;
   }

   private static Clock createClock() {
      String userRequest = PropertiesUtil.getProperties().getStringProperty("log4j.Clock");
      if (userRequest == null) {
         LOGGER.trace("Using default SystemClock for timestamps.");
         return logSupportedPrecision(new SystemClock());
      } else {
         Supplier<Clock> specified = aliases().get(userRequest);
         if (specified != null) {
            LOGGER.trace("Using specified {} for timestamps.", userRequest);
            return logSupportedPrecision(specified.get());
         } else {
            try {
               Clock result = Loader.newCheckedInstanceOf(userRequest, Clock.class);
               LOGGER.trace("Using {} for timestamps.", result.getClass().getName());
               return logSupportedPrecision(result);
            } catch (Exception var4) {
               String fmt = "Could not create {}: {}, using default SystemClock for timestamps.";
               LOGGER.error("Could not create {}: {}, using default SystemClock for timestamps.", userRequest, var4);
               return logSupportedPrecision(new SystemClock());
            }
         }
      }
   }

   private static Clock logSupportedPrecision(final Clock clock) {
      String support = clock instanceof PreciseClock ? "supports" : "does not support";
      LOGGER.debug("{} {} precise timestamps.", clock.getClass().getName(), support);
      return clock;
   }
}
