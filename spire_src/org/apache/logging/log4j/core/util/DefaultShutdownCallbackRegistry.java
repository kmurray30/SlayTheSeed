package org.apache.logging.log4j.core.util;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.AbstractLifeCycle;
import org.apache.logging.log4j.core.LifeCycle;
import org.apache.logging.log4j.core.LifeCycle2;
import org.apache.logging.log4j.status.StatusLogger;

public class DefaultShutdownCallbackRegistry implements ShutdownCallbackRegistry, LifeCycle2, Runnable {
   protected static final Logger LOGGER = StatusLogger.getLogger();
   private final AtomicReference<LifeCycle.State> state = new AtomicReference<>(LifeCycle.State.INITIALIZED);
   private final ThreadFactory threadFactory;
   private final Collection<Reference<Cancellable>> hooks = new CopyOnWriteArrayList<>();
   private Reference<Thread> shutdownHookRef;

   public DefaultShutdownCallbackRegistry() {
      this(Executors.defaultThreadFactory());
   }

   protected DefaultShutdownCallbackRegistry(final ThreadFactory threadFactory) {
      this.threadFactory = threadFactory;
   }

   @Override
   public void run() {
      if (this.state.compareAndSet(LifeCycle.State.STARTED, LifeCycle.State.STOPPING)) {
         for (Reference<Cancellable> hookRef : this.hooks) {
            Cancellable hook = hookRef.get();
            if (hook != null) {
               try {
                  hook.run();
               } catch (Throwable var7) {
                  Throwable t1 = var7;

                  try {
                     LOGGER.error(SHUTDOWN_HOOK_MARKER, "Caught exception executing shutdown hook {}", hook, t1);
                  } catch (Throwable var6) {
                     System.err.println("Caught exception " + var6.getClass() + " logging exception " + var7.getClass());
                     var7.printStackTrace();
                  }
               }
            }
         }

         this.state.set(LifeCycle.State.STOPPED);
      }
   }

   @Override
   public Cancellable addShutdownCallback(final Runnable callback) {
      if (this.isStarted()) {
         Cancellable receipt = new DefaultShutdownCallbackRegistry.RegisteredCancellable(callback, this.hooks);
         this.hooks.add(new SoftReference<>(receipt));
         return receipt;
      } else {
         throw new IllegalStateException("Cannot add new shutdown hook as this is not started. Current state: " + this.state.get().name());
      }
   }

   @Override
   public void initialize() {
   }

   @Override
   public void start() {
      if (this.state.compareAndSet(LifeCycle.State.INITIALIZED, LifeCycle.State.STARTING)) {
         try {
            this.addShutdownHook(this.threadFactory.newThread(this));
            this.state.set(LifeCycle.State.STARTED);
         } catch (IllegalStateException var2) {
            this.state.set(LifeCycle.State.STOPPED);
            throw var2;
         } catch (Exception var3) {
            LOGGER.catching(var3);
            this.state.set(LifeCycle.State.STOPPED);
         }
      }
   }

   private void addShutdownHook(final Thread thread) {
      this.shutdownHookRef = new WeakReference<>(thread);
      Runtime.getRuntime().addShutdownHook(thread);
   }

   @Override
   public void stop() {
      this.stop(0L, AbstractLifeCycle.DEFAULT_STOP_TIMEUNIT);
   }

   @Override
   public boolean stop(final long timeout, final TimeUnit timeUnit) {
      if (this.state.compareAndSet(LifeCycle.State.STARTED, LifeCycle.State.STOPPING)) {
         try {
            this.removeShutdownHook();
         } finally {
            this.state.set(LifeCycle.State.STOPPED);
         }
      }

      return true;
   }

   private void removeShutdownHook() {
      Thread shutdownThread = this.shutdownHookRef.get();
      if (shutdownThread != null) {
         Runtime.getRuntime().removeShutdownHook(shutdownThread);
         this.shutdownHookRef.enqueue();
      }
   }

   @Override
   public LifeCycle.State getState() {
      return this.state.get();
   }

   @Override
   public boolean isStarted() {
      return this.state.get() == LifeCycle.State.STARTED;
   }

   @Override
   public boolean isStopped() {
      return this.state.get() == LifeCycle.State.STOPPED;
   }

   private static class RegisteredCancellable implements Cancellable {
      private Runnable callback;
      private Collection<Reference<Cancellable>> registered;

      RegisteredCancellable(final Runnable callback, final Collection<Reference<Cancellable>> registered) {
         this.callback = callback;
         this.registered = registered;
      }

      @Override
      public void cancel() {
         this.callback = null;
         Collection<Reference<Cancellable>> references = this.registered;
         if (references != null) {
            this.registered = null;
            references.removeIf(ref -> {
               Cancellable value = ref.get();
               return value == null || value == this;
            });
         }
      }

      @Override
      public void run() {
         Runnable runnableHook = this.callback;
         if (runnableHook != null) {
            runnableHook.run();
            this.callback = null;
         }
      }

      @Override
      public String toString() {
         return String.valueOf(this.callback);
      }
   }
}
