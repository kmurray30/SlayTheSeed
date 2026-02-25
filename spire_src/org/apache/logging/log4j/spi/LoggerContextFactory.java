package org.apache.logging.log4j.spi;

import java.net.URI;

public interface LoggerContextFactory {
   default void shutdown(String fqcn, ClassLoader loader, boolean currentContext, boolean allContexts) {
      if (this.hasContext(fqcn, loader, currentContext)) {
         LoggerContext ctx = this.getContext(fqcn, loader, null, currentContext);
         if (ctx instanceof Terminable) {
            ((Terminable)ctx).terminate();
         }
      }
   }

   default boolean hasContext(String fqcn, ClassLoader loader, boolean currentContext) {
      return false;
   }

   LoggerContext getContext(String fqcn, ClassLoader loader, Object externalContext, boolean currentContext);

   LoggerContext getContext(String fqcn, ClassLoader loader, Object externalContext, boolean currentContext, URI configLocation, String name);

   void removeContext(LoggerContext context);

   default boolean isClassLoaderDependent() {
      return true;
   }
}
