package org.apache.logging.log4j.core.selector;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.ContextAnchor;
import org.apache.logging.log4j.spi.LoggerContextShutdownAware;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.StackLocatorUtil;

public class ClassLoaderContextSelector implements ContextSelector, LoggerContextShutdownAware {
   private static final AtomicReference<LoggerContext> DEFAULT_CONTEXT = new AtomicReference<>();
   protected static final StatusLogger LOGGER = StatusLogger.getLogger();
   protected static final ConcurrentMap<String, AtomicReference<WeakReference<LoggerContext>>> CONTEXT_MAP = new ConcurrentHashMap<>();

   @Override
   public void shutdown(final String fqcn, final ClassLoader loader, final boolean currentContext, final boolean allContexts) {
      LoggerContext ctx = null;
      if (currentContext) {
         ctx = ContextAnchor.THREAD_CONTEXT.get();
      } else if (loader != null) {
         ctx = this.findContext(loader);
      } else {
         Class<?> clazz = StackLocatorUtil.getCallerClass(fqcn);
         if (clazz != null) {
            ctx = this.findContext(clazz.getClassLoader());
         }

         if (ctx == null) {
            ctx = ContextAnchor.THREAD_CONTEXT.get();
         }
      }

      if (ctx != null) {
         ctx.stop(50L, TimeUnit.MILLISECONDS);
      }
   }

   @Override
   public void contextShutdown(org.apache.logging.log4j.spi.LoggerContext loggerContext) {
      if (loggerContext instanceof LoggerContext) {
         this.removeContext((LoggerContext)loggerContext);
      }
   }

   @Override
   public boolean hasContext(final String fqcn, final ClassLoader loader, final boolean currentContext) {
      LoggerContext ctx;
      if (currentContext) {
         ctx = ContextAnchor.THREAD_CONTEXT.get();
      } else if (loader != null) {
         ctx = this.findContext(loader);
      } else {
         Class<?> clazz = StackLocatorUtil.getCallerClass(fqcn);
         if (clazz != null) {
            ctx = this.findContext(clazz.getClassLoader());
         } else {
            ctx = ContextAnchor.THREAD_CONTEXT.get();
         }
      }

      return ctx != null && ctx.isStarted();
   }

   private LoggerContext findContext(ClassLoader loaderOrNull) {
      ClassLoader loader = loaderOrNull != null ? loaderOrNull : ClassLoader.getSystemClassLoader();
      String name = this.toContextMapKey(loader);
      AtomicReference<WeakReference<LoggerContext>> ref = CONTEXT_MAP.get(name);
      if (ref != null) {
         WeakReference<LoggerContext> weakRef = ref.get();
         return weakRef.get();
      } else {
         return null;
      }
   }

   @Override
   public LoggerContext getContext(final String fqcn, final ClassLoader loader, final boolean currentContext) {
      return this.getContext(fqcn, loader, currentContext, null);
   }

   @Override
   public LoggerContext getContext(final String fqcn, final ClassLoader loader, final boolean currentContext, final URI configLocation) {
      return this.getContext(fqcn, loader, null, currentContext, configLocation);
   }

   @Override
   public LoggerContext getContext(
      final String fqcn, final ClassLoader loader, final Entry<String, Object> entry, final boolean currentContext, final URI configLocation
   ) {
      if (currentContext) {
         LoggerContext ctx = ContextAnchor.THREAD_CONTEXT.get();
         return ctx != null ? ctx : this.getDefault();
      } else if (loader != null) {
         return this.locateContext(loader, entry, configLocation);
      } else {
         Class<?> clazz = StackLocatorUtil.getCallerClass(fqcn);
         if (clazz != null) {
            return this.locateContext(clazz.getClassLoader(), entry, configLocation);
         } else {
            LoggerContext lc = ContextAnchor.THREAD_CONTEXT.get();
            return lc != null ? lc : this.getDefault();
         }
      }
   }

   @Override
   public void removeContext(final LoggerContext context) {
      for (Entry<String, AtomicReference<WeakReference<LoggerContext>>> entry : CONTEXT_MAP.entrySet()) {
         LoggerContext ctx = entry.getValue().get().get();
         if (ctx == context) {
            CONTEXT_MAP.remove(entry.getKey());
         }
      }
   }

   @Override
   public boolean isClassLoaderDependent() {
      return true;
   }

   @Override
   public List<LoggerContext> getLoggerContexts() {
      List<LoggerContext> list = new ArrayList<>();

      for (AtomicReference<WeakReference<LoggerContext>> ref : CONTEXT_MAP.values()) {
         LoggerContext ctx = ref.get().get();
         if (ctx != null) {
            list.add(ctx);
         }
      }

      return Collections.unmodifiableList(list);
   }

   private LoggerContext locateContext(final ClassLoader loaderOrNull, final Entry<String, Object> entry, final URI configLocation) {
      ClassLoader loader = loaderOrNull != null ? loaderOrNull : ClassLoader.getSystemClassLoader();
      String name = this.toContextMapKey(loader);
      AtomicReference<WeakReference<LoggerContext>> ref = CONTEXT_MAP.get(name);
      if (ref == null) {
         if (configLocation == null) {
            for (ClassLoader parent = loader.getParent(); parent != null; parent = parent.getParent()) {
               ref = CONTEXT_MAP.get(this.toContextMapKey(parent));
               if (ref != null) {
                  WeakReference<LoggerContext> r = ref.get();
                  LoggerContext ctx = r.get();
                  if (ctx != null) {
                     return ctx;
                  }
               }
            }
         }

         LoggerContext ctx = this.createContext(name, configLocation);
         if (entry != null) {
            ctx.putObject(entry.getKey(), entry.getValue());
         }

         LoggerContext newContext = CONTEXT_MAP.computeIfAbsent(name, k -> new AtomicReference<>(new WeakReference<>(ctx))).get().get();
         if (newContext == ctx) {
            ctx.addShutdownListener(this);
         }

         return newContext;
      } else {
         WeakReference<LoggerContext> weakRef = ref.get();
         LoggerContext ctxx = weakRef.get();
         if (ctxx != null) {
            if (entry != null && ctxx.getObject(entry.getKey()) == null) {
               ctxx.putObject(entry.getKey(), entry.getValue());
            }

            if (ctxx.getConfigLocation() == null && configLocation != null) {
               LOGGER.debug("Setting configuration to {}", configLocation);
               ctxx.setConfigLocation(configLocation);
            } else if (ctxx.getConfigLocation() != null && configLocation != null && !ctxx.getConfigLocation().equals(configLocation)) {
               LOGGER.warn("locateContext called with URI {}. Existing LoggerContext has URI {}", configLocation, ctxx.getConfigLocation());
            }

            return ctxx;
         } else {
            ctxx = this.createContext(name, configLocation);
            if (entry != null) {
               ctxx.putObject(entry.getKey(), entry.getValue());
            }

            ref.compareAndSet(weakRef, new WeakReference<>(ctxx));
            return ctxx;
         }
      }
   }

   protected LoggerContext createContext(final String name, final URI configLocation) {
      return new LoggerContext(name, null, configLocation);
   }

   protected String toContextMapKey(final ClassLoader loader) {
      return Integer.toHexString(System.identityHashCode(loader));
   }

   protected LoggerContext getDefault() {
      LoggerContext ctx = DEFAULT_CONTEXT.get();
      if (ctx != null) {
         return ctx;
      } else {
         DEFAULT_CONTEXT.compareAndSet(null, this.createContext(this.defaultContextName(), null));
         return DEFAULT_CONTEXT.get();
      }
   }

   protected String defaultContextName() {
      return "Default";
   }
}
