package org.apache.logging.log4j.core.selector;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import javax.naming.NamingException;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.ContextAnchor;
import org.apache.logging.log4j.core.net.JndiManager;
import org.apache.logging.log4j.status.StatusLogger;

public class JndiContextSelector implements NamedContextSelector {
   private static final LoggerContext CONTEXT = new LoggerContext("Default");
   private static final ConcurrentMap<String, LoggerContext> CONTEXT_MAP = new ConcurrentHashMap<>();
   private static final StatusLogger LOGGER = StatusLogger.getLogger();

   public JndiContextSelector() {
      if (!JndiManager.isJndiContextSelectorEnabled()) {
         throw new IllegalStateException("JNDI must be enabled by setting log4j2.enableJndiContextSelector=true");
      }
   }

   @Override
   public void shutdown(String fqcn, ClassLoader loader, boolean currentContext, boolean allContexts) {
      LoggerContext ctx = ContextAnchor.THREAD_CONTEXT.get();
      if (ctx == null) {
         String loggingContextName = this.getContextName();
         if (loggingContextName != null) {
            ctx = CONTEXT_MAP.get(loggingContextName);
         }
      }

      if (ctx != null) {
         ctx.stop(50L, TimeUnit.MILLISECONDS);
      }
   }

   @Override
   public boolean hasContext(String fqcn, ClassLoader loader, boolean currentContext) {
      LoggerContext ctx = ContextAnchor.THREAD_CONTEXT.get();
      if (ctx == null) {
         String loggingContextName = this.getContextName();
         if (loggingContextName == null) {
            return false;
         }

         ctx = CONTEXT_MAP.get(loggingContextName);
      }

      return ctx != null && ctx.isStarted();
   }

   @Override
   public LoggerContext getContext(final String fqcn, final ClassLoader loader, final boolean currentContext) {
      return this.getContext(fqcn, loader, currentContext, null);
   }

   @Override
   public LoggerContext getContext(final String fqcn, final ClassLoader loader, final boolean currentContext, final URI configLocation) {
      LoggerContext lc = ContextAnchor.THREAD_CONTEXT.get();
      if (lc != null) {
         return lc;
      } else {
         String loggingContextName = null;

         try (JndiManager jndiManager = JndiManager.getDefaultManager()) {
            loggingContextName = jndiManager.lookup("java:comp/env/log4j/context-name");
         } catch (NamingException var20) {
            LOGGER.error("Unable to lookup {}", "java:comp/env/log4j/context-name", var20);
         }

         return loggingContextName == null ? CONTEXT : this.locateContext(loggingContextName, null, configLocation);
      }
   }

   private String getContextName() {
      String loggingContextName = null;

      try (JndiManager jndiManager = JndiManager.getDefaultManager()) {
         loggingContextName = jndiManager.lookup("java:comp/env/log4j/context-name");
      } catch (NamingException var15) {
         LOGGER.error("Unable to lookup {}", "java:comp/env/log4j/context-name", var15);
      }

      return loggingContextName;
   }

   @Override
   public LoggerContext locateContext(final String name, final Object externalContext, final URI configLocation) {
      if (name == null) {
         LOGGER.error("A context name is required to locate a LoggerContext");
         return null;
      } else {
         if (!CONTEXT_MAP.containsKey(name)) {
            LoggerContext ctx = new LoggerContext(name, externalContext, configLocation);
            CONTEXT_MAP.putIfAbsent(name, ctx);
         }

         return CONTEXT_MAP.get(name);
      }
   }

   @Override
   public void removeContext(final LoggerContext context) {
      for (Entry<String, LoggerContext> entry : CONTEXT_MAP.entrySet()) {
         if (entry.getValue().equals(context)) {
            CONTEXT_MAP.remove(entry.getKey());
         }
      }
   }

   @Override
   public boolean isClassLoaderDependent() {
      return false;
   }

   @Override
   public LoggerContext removeContext(final String name) {
      return CONTEXT_MAP.remove(name);
   }

   @Override
   public List<LoggerContext> getLoggerContexts() {
      return Collections.unmodifiableList(new ArrayList<>(CONTEXT_MAP.values()));
   }
}
