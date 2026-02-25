package org.apache.logging.log4j.core.appender.db;

import java.io.Flushable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.ManagerFactory;

public abstract class AbstractDatabaseManager extends AbstractManager implements Flushable {
   private final ArrayList<LogEvent> buffer;
   private final int bufferSize;
   private final Layout<? extends Serializable> layout;
   private boolean running;

   protected static <M extends AbstractDatabaseManager, T extends AbstractDatabaseManager.AbstractFactoryData> M getManager(
      final String name, final T data, final ManagerFactory<M, T> factory
   ) {
      return AbstractManager.getManager(name, factory, data);
   }

   protected AbstractDatabaseManager(final String name, final int bufferSize) {
      this(name, bufferSize, null);
   }

   protected AbstractDatabaseManager(final String name, final int bufferSize, final Layout<? extends Serializable> layout) {
      super(null, name);
      this.bufferSize = bufferSize;
      this.buffer = new ArrayList<>(bufferSize + 1);
      this.layout = layout;
   }

   protected void buffer(final LogEvent event) {
      this.buffer.add(event.toImmutable());
      if (this.buffer.size() >= this.bufferSize || event.isEndOfBatch()) {
         this.flush();
      }
   }

   protected abstract boolean commitAndClose();

   protected abstract void connectAndStart();

   @Override
   public final synchronized void flush() {
      if (this.isRunning() && this.isBuffered()) {
         this.connectAndStart();

         try {
            for (LogEvent event : this.buffer) {
               this.writeInternal(event, this.layout != null ? this.layout.toSerializable(event) : null);
            }
         } finally {
            this.commitAndClose();
            this.buffer.clear();
         }
      }
   }

   protected boolean isBuffered() {
      return this.bufferSize > 0;
   }

   public final boolean isRunning() {
      return this.running;
   }

   @Override
   public final boolean releaseSub(final long timeout, final TimeUnit timeUnit) {
      return this.shutdown();
   }

   public final synchronized boolean shutdown() {
      boolean closed = true;
      this.flush();
      if (this.isRunning()) {
         try {
            closed &= this.shutdownInternal();
         } catch (Exception var6) {
            this.logWarn("Caught exception while performing database shutdown operations", var6);
            closed = false;
         } finally {
            this.running = false;
         }
      }

      return closed;
   }

   protected abstract boolean shutdownInternal() throws Exception;

   public final synchronized void startup() {
      if (!this.isRunning()) {
         try {
            this.startupInternal();
            this.running = true;
         } catch (Exception var2) {
            this.logError("Could not perform database startup operations", var2);
         }
      }
   }

   protected abstract void startupInternal() throws Exception;

   @Override
   public final String toString() {
      return this.getName();
   }

   @Deprecated
   public final synchronized void write(final LogEvent event) {
      this.write(event, null);
   }

   public final synchronized void write(final LogEvent event, final Serializable serializable) {
      if (this.isBuffered()) {
         this.buffer(event);
      } else {
         this.writeThrough(event, serializable);
      }
   }

   @Deprecated
   protected void writeInternal(final LogEvent event) {
      this.writeInternal(event, null);
   }

   protected abstract void writeInternal(LogEvent event, Serializable serializable);

   protected void writeThrough(final LogEvent event, final Serializable serializable) {
      this.connectAndStart();

      try {
         this.writeInternal(event, serializable);
      } finally {
         this.commitAndClose();
      }
   }

   protected abstract static class AbstractFactoryData {
      private final int bufferSize;
      private final Layout<? extends Serializable> layout;

      protected AbstractFactoryData(final int bufferSize, final Layout<? extends Serializable> layout) {
         this.bufferSize = bufferSize;
         this.layout = layout;
      }

      public int getBufferSize() {
         return this.bufferSize;
      }

      public Layout<? extends Serializable> getLayout() {
         return this.layout;
      }
   }
}
