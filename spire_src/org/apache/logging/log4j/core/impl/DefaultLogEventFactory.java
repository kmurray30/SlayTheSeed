package org.apache.logging.log4j.core.impl;

import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.message.Message;

public class DefaultLogEventFactory implements LogEventFactory, LocationAwareLogEventFactory {
   private static final DefaultLogEventFactory instance = new DefaultLogEventFactory();

   public static DefaultLogEventFactory getInstance() {
      return instance;
   }

   @Override
   public LogEvent createEvent(
      final String loggerName,
      final Marker marker,
      final String fqcn,
      final Level level,
      final Message data,
      final List<Property> properties,
      final Throwable t
   ) {
      return new Log4jLogEvent(loggerName, marker, fqcn, level, data, properties, t);
   }

   @Override
   public LogEvent createEvent(
      final String loggerName,
      final Marker marker,
      final String fqcn,
      final StackTraceElement location,
      final Level level,
      final Message data,
      final List<Property> properties,
      final Throwable t
   ) {
      return new Log4jLogEvent(loggerName, marker, fqcn, location, level, data, properties, t);
   }
}
