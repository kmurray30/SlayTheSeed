package org.apache.logging.log4j.simple;

import java.net.URI;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;

public class SimpleLoggerContextFactory implements LoggerContextFactory {
   private static LoggerContext context = new SimpleLoggerContext();

   @Override
   public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext, final boolean currentContext) {
      return context;
   }

   @Override
   public LoggerContext getContext(
      final String fqcn, final ClassLoader loader, final Object externalContext, final boolean currentContext, final URI configLocation, final String name
   ) {
      return context;
   }

   @Override
   public void removeContext(final LoggerContext removeContext) {
   }

   @Override
   public boolean isClassLoaderDependent() {
      return false;
   }
}
