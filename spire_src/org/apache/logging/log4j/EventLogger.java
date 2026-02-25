package org.apache.logging.log4j;

import org.apache.logging.log4j.message.StructuredDataMessage;
import org.apache.logging.log4j.spi.ExtendedLogger;

public final class EventLogger {
   public static final Marker EVENT_MARKER = MarkerManager.getMarker("EVENT");
   private static final String NAME = "EventLogger";
   private static final String FQCN = EventLogger.class.getName();
   private static final ExtendedLogger LOGGER = LogManager.getContext(false).getLogger("EventLogger");

   private EventLogger() {
   }

   public static void logEvent(final StructuredDataMessage msg) {
      LOGGER.logIfEnabled(FQCN, Level.OFF, EVENT_MARKER, msg, null);
   }

   public static void logEvent(final StructuredDataMessage msg, final Level level) {
      LOGGER.logIfEnabled(FQCN, level, EVENT_MARKER, msg, null);
   }
}
