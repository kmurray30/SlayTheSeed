package org.apache.logging.log4j.status;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.ParameterizedNoReferenceMessageFactory;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.Strings;

public final class StatusLogger extends AbstractLogger {
   public static final String MAX_STATUS_ENTRIES = "log4j2.status.entries";
   public static final String DEFAULT_STATUS_LISTENER_LEVEL = "log4j2.StatusLogger.level";
   public static final String STATUS_DATE_FORMAT = "log4j2.StatusLogger.DateFormat";
   private static final long serialVersionUID = 2L;
   private static final String NOT_AVAIL = "?";
   private static final PropertiesUtil PROPS = new PropertiesUtil("log4j2.StatusLogger.properties");
   private static final int MAX_ENTRIES = PROPS.getIntegerProperty("log4j2.status.entries", 200);
   private static final String DEFAULT_STATUS_LEVEL = PROPS.getStringProperty("log4j2.StatusLogger.level");
   private static final StatusLogger STATUS_LOGGER = new StatusLogger(StatusLogger.class.getName(), ParameterizedNoReferenceMessageFactory.INSTANCE);
   private final SimpleLogger logger;
   private final Collection<StatusListener> listeners = new CopyOnWriteArrayList<>();
   private final ReadWriteLock listenersLock = new ReentrantReadWriteLock();
   private final Queue<StatusData> messages = new StatusLogger.BoundedQueue<>(MAX_ENTRIES);
   private final Lock msgLock = new ReentrantLock();
   private int listenersLevel;

   private StatusLogger(final String name, final MessageFactory messageFactory) {
      super(name, messageFactory);
      String dateFormat = PROPS.getStringProperty("log4j2.StatusLogger.DateFormat", "");
      boolean showDateTime = !Strings.isEmpty(dateFormat);
      this.logger = new SimpleLogger("StatusLogger", Level.ERROR, false, true, showDateTime, false, dateFormat, messageFactory, PROPS, System.err);
      this.listenersLevel = Level.toLevel(DEFAULT_STATUS_LEVEL, Level.WARN).intLevel();
      if (this.isDebugPropertyEnabled()) {
         this.logger.setLevel(Level.TRACE);
      }
   }

   private boolean isDebugPropertyEnabled() {
      return PropertiesUtil.getProperties().getBooleanProperty("log4j2.debug", false, true);
   }

   public static StatusLogger getLogger() {
      return STATUS_LOGGER;
   }

   public void setLevel(final Level level) {
      this.logger.setLevel(level);
   }

   public void registerListener(final StatusListener listener) {
      this.listenersLock.writeLock().lock();

      try {
         this.listeners.add(listener);
         Level lvl = listener.getStatusLevel();
         if (this.listenersLevel < lvl.intLevel()) {
            this.listenersLevel = lvl.intLevel();
         }
      } finally {
         this.listenersLock.writeLock().unlock();
      }
   }

   public void removeListener(final StatusListener listener) {
      closeSilently(listener);
      this.listenersLock.writeLock().lock();

      try {
         this.listeners.remove(listener);
         int lowest = Level.toLevel(DEFAULT_STATUS_LEVEL, Level.WARN).intLevel();

         for (StatusListener statusListener : this.listeners) {
            int level = statusListener.getStatusLevel().intLevel();
            if (lowest < level) {
               lowest = level;
            }
         }

         this.listenersLevel = lowest;
      } finally {
         this.listenersLock.writeLock().unlock();
      }
   }

   public void updateListenerLevel(final Level status) {
      if (status.intLevel() > this.listenersLevel) {
         this.listenersLevel = status.intLevel();
      }
   }

   public Iterable<StatusListener> getListeners() {
      return this.listeners;
   }

   public void reset() {
      this.listenersLock.writeLock().lock();

      try {
         for (StatusListener listener : this.listeners) {
            closeSilently(listener);
         }
      } finally {
         this.listeners.clear();
         this.listenersLock.writeLock().unlock();
         this.clear();
      }
   }

   private static void closeSilently(final Closeable resource) {
      try {
         resource.close();
      } catch (IOException var2) {
      }
   }

   public List<StatusData> getStatusData() {
      this.msgLock.lock();

      ArrayList var1;
      try {
         var1 = new ArrayList<>(this.messages);
      } finally {
         this.msgLock.unlock();
      }

      return var1;
   }

   public void clear() {
      this.msgLock.lock();

      try {
         this.messages.clear();
      } finally {
         this.msgLock.unlock();
      }
   }

   @Override
   public Level getLevel() {
      return this.logger.getLevel();
   }

   @Override
   public void logMessage(final String fqcn, final Level level, final Marker marker, final Message msg, final Throwable t) {
      StackTraceElement element = null;
      if (fqcn != null) {
         element = this.getStackTraceElement(fqcn, Thread.currentThread().getStackTrace());
      }

      StatusData data = new StatusData(element, level, msg, t, null);
      this.msgLock.lock();

      try {
         this.messages.add(data);
      } finally {
         this.msgLock.unlock();
      }

      if (!this.isDebugPropertyEnabled() && this.listeners.size() > 0) {
         for (StatusListener listener : this.listeners) {
            if (data.getLevel().isMoreSpecificThan(listener.getStatusLevel())) {
               listener.log(data);
            }
         }
      } else {
         this.logger.logMessage(fqcn, level, marker, msg, t);
      }
   }

   private StackTraceElement getStackTraceElement(final String fqcn, final StackTraceElement[] stackTrace) {
      if (fqcn == null) {
         return null;
      } else {
         boolean next = false;

         for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (next && !fqcn.equals(className)) {
               return element;
            }

            if (fqcn.equals(className)) {
               next = true;
            } else if ("?".equals(className)) {
               break;
            }
         }

         return null;
      }
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker, final String message, final Throwable t) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker, final String message) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker, final String message, final Object... params) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0, final Object p1) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0, final Object p1, final Object p2) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(
      final Level level, final Marker marker, final String message, final Object p0, final Object p1, final Object p2, final Object p3, final Object p4
   ) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5
   ) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6
   ) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7
   ) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8
   ) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(
      final Level level,
      final Marker marker,
      final String message,
      final Object p0,
      final Object p1,
      final Object p2,
      final Object p3,
      final Object p4,
      final Object p5,
      final Object p6,
      final Object p7,
      final Object p8,
      final Object p9
   ) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker, final CharSequence message, final Throwable t) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker, final Object message, final Throwable t) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker, final Message message, final Throwable t) {
      return this.isEnabled(level, marker);
   }

   @Override
   public boolean isEnabled(final Level level, final Marker marker) {
      if (this.isDebugPropertyEnabled()) {
         return true;
      } else {
         return this.listeners.size() > 0 ? this.listenersLevel >= level.intLevel() : this.logger.isEnabled(level, marker);
      }
   }

   private class BoundedQueue<E> extends ConcurrentLinkedQueue<E> {
      private static final long serialVersionUID = -3945953719763255337L;
      private final int size;

      BoundedQueue(final int size) {
         this.size = size;
      }

      @Override
      public boolean add(final E object) {
         super.add(object);

         while (StatusLogger.this.messages.size() > this.size) {
            StatusLogger.this.messages.poll();
         }

         return this.size > 0;
      }
   }
}
