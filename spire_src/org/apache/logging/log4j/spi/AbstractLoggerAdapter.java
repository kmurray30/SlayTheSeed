package org.apache.logging.log4j.spi;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.util.LoaderUtil;

public abstract class AbstractLoggerAdapter<L> implements LoggerAdapter<L>, LoggerContextShutdownAware {
   protected final Map<LoggerContext, ConcurrentMap<String, L>> registry = new ConcurrentHashMap<>();
   private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

   @Override
   public L getLogger(final String name) {
      LoggerContext context = this.getContext();
      ConcurrentMap<String, L> loggers = this.getLoggersInContext(context);
      L logger = loggers.get(name);
      if (logger != null) {
         return logger;
      } else {
         loggers.putIfAbsent(name, this.newLogger(name, context));
         return loggers.get(name);
      }
   }

   @Override
   public void contextShutdown(LoggerContext loggerContext) {
      this.registry.remove(loggerContext);
   }

   public ConcurrentMap<String, L> getLoggersInContext(final LoggerContext context) {
      this.lock.readLock().lock();

      ConcurrentMap<String, L> loggers;
      try {
         loggers = this.registry.get(context);
      } finally {
         this.lock.readLock().unlock();
      }

      if (loggers != null) {
         return loggers;
      } else {
         this.lock.writeLock().lock();

         Object var3;
         try {
            ConcurrentMap<String, L> var11 = this.registry.get(context);
            if (var11 == null) {
               var11 = new ConcurrentHashMap();
               this.registry.put(context, (ConcurrentMap<String, L>)var11);
               if (context instanceof LoggerContextShutdownEnabled) {
                  ((LoggerContextShutdownEnabled)context).addShutdownListener(this);
               }
            }

            var3 = var11;
         } finally {
            this.lock.writeLock().unlock();
         }

         return (ConcurrentMap<String, L>)var3;
      }
   }

   public Set<LoggerContext> getLoggerContexts() {
      return new HashSet<>(this.registry.keySet());
   }

   protected abstract L newLogger(final String name, final LoggerContext context);

   protected abstract LoggerContext getContext();

   protected LoggerContext getContext(final Class<?> callerClass) {
      ClassLoader cl = null;
      if (callerClass != null) {
         cl = callerClass.getClassLoader();
      }

      if (cl == null) {
         cl = LoaderUtil.getThreadContextClassLoader();
      }

      return LogManager.getContext(cl, false);
   }

   @Override
   public void close() {
      this.lock.writeLock().lock();

      try {
         this.registry.clear();
      } finally {
         this.lock.writeLock().unlock();
      }
   }
}
