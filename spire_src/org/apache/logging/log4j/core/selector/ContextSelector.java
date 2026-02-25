package org.apache.logging.log4j.core.selector;

import java.net.URI;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.core.LoggerContext;

public interface ContextSelector {
   long DEFAULT_STOP_TIMEOUT = 50L;

   default void shutdown(final String fqcn, final ClassLoader loader, final boolean currentContext, final boolean allContexts) {
      if (this.hasContext(fqcn, loader, currentContext)) {
         this.getContext(fqcn, loader, currentContext).stop(50L, TimeUnit.MILLISECONDS);
      }
   }

   default boolean hasContext(String fqcn, ClassLoader loader, boolean currentContext) {
      return false;
   }

   LoggerContext getContext(String fqcn, ClassLoader loader, boolean currentContext);

   default LoggerContext getContext(String fqcn, ClassLoader loader, Entry<String, Object> entry, boolean currentContext) {
      LoggerContext lc = this.getContext(fqcn, loader, currentContext);
      if (lc != null) {
         lc.putObject(entry.getKey(), entry.getValue());
      }

      return lc;
   }

   LoggerContext getContext(String fqcn, ClassLoader loader, boolean currentContext, URI configLocation);

   default LoggerContext getContext(String fqcn, ClassLoader loader, Entry<String, Object> entry, boolean currentContext, URI configLocation) {
      LoggerContext lc = this.getContext(fqcn, loader, currentContext, configLocation);
      if (lc != null) {
         lc.putObject(entry.getKey(), entry.getValue());
      }

      return lc;
   }

   List<LoggerContext> getLoggerContexts();

   void removeContext(LoggerContext context);

   default boolean isClassLoaderDependent() {
      return true;
   }
}
