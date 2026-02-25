package org.apache.logging.log4j.core.selector;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.ContextAnchor;

public class BasicContextSelector implements ContextSelector {
   private static final LoggerContext CONTEXT = new LoggerContext("Default");

   @Override
   public void shutdown(String fqcn, ClassLoader loader, boolean currentContext, boolean allContexts) {
      LoggerContext ctx = this.getContext(fqcn, loader, currentContext);
      if (ctx != null && ctx.isStarted()) {
         ctx.stop(50L, TimeUnit.MILLISECONDS);
      }
   }

   @Override
   public boolean hasContext(String fqcn, ClassLoader loader, boolean currentContext) {
      LoggerContext ctx = this.getContext(fqcn, loader, currentContext);
      return ctx != null && ctx.isStarted();
   }

   @Override
   public LoggerContext getContext(final String fqcn, final ClassLoader loader, final boolean currentContext) {
      LoggerContext ctx = ContextAnchor.THREAD_CONTEXT.get();
      return ctx != null ? ctx : CONTEXT;
   }

   @Override
   public LoggerContext getContext(final String fqcn, final ClassLoader loader, final boolean currentContext, final URI configLocation) {
      LoggerContext ctx = ContextAnchor.THREAD_CONTEXT.get();
      return ctx != null ? ctx : CONTEXT;
   }

   public LoggerContext locateContext(final String name, final String configLocation) {
      return CONTEXT;
   }

   @Override
   public void removeContext(final LoggerContext context) {
   }

   @Override
   public boolean isClassLoaderDependent() {
      return false;
   }

   @Override
   public List<LoggerContext> getLoggerContexts() {
      List<LoggerContext> list = new ArrayList<>();
      list.add(CONTEXT);
      return Collections.unmodifiableList(list);
   }
}
