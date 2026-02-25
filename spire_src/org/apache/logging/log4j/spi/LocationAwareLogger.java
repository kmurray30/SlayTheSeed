package org.apache.logging.log4j.spi;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;

public interface LocationAwareLogger {
   void logMessage(
      final Level level, final Marker marker, final String fqcn, final StackTraceElement location, final Message message, final Throwable throwable
   );
}
