package org.apache.logging.log4j.spi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.logging.log4j.message.MessageFactory;

public class LoggerRegistry<T extends ExtendedLogger> {
   private static final String DEFAULT_FACTORY_KEY = AbstractLogger.DEFAULT_MESSAGE_FACTORY_CLASS.getName();
   private final LoggerRegistry.MapFactory<T> factory;
   private final Map<String, Map<String, T>> map;

   public LoggerRegistry() {
      this(new LoggerRegistry.ConcurrentMapFactory<>());
   }

   public LoggerRegistry(final LoggerRegistry.MapFactory<T> factory) {
      this.factory = Objects.requireNonNull(factory, "factory");
      this.map = factory.createOuterMap();
   }

   private static String factoryClassKey(final Class<? extends MessageFactory> messageFactoryClass) {
      return messageFactoryClass == null ? DEFAULT_FACTORY_KEY : messageFactoryClass.getName();
   }

   private static String factoryKey(final MessageFactory messageFactory) {
      return messageFactory == null ? DEFAULT_FACTORY_KEY : messageFactory.getClass().getName();
   }

   public T getLogger(final String name) {
      return this.getOrCreateInnerMap(DEFAULT_FACTORY_KEY).get(name);
   }

   public T getLogger(final String name, final MessageFactory messageFactory) {
      return this.getOrCreateInnerMap(factoryKey(messageFactory)).get(name);
   }

   public Collection<T> getLoggers() {
      return this.getLoggers(new ArrayList<>());
   }

   public Collection<T> getLoggers(final Collection<T> destination) {
      for (Map<String, T> inner : this.map.values()) {
         destination.addAll(inner.values());
      }

      return destination;
   }

   private Map<String, T> getOrCreateInnerMap(final String factoryName) {
      Map<String, T> inner = this.map.get(factoryName);
      if (inner == null) {
         inner = this.factory.createInnerMap();
         this.map.put(factoryName, inner);
      }

      return inner;
   }

   public boolean hasLogger(final String name) {
      return this.getOrCreateInnerMap(DEFAULT_FACTORY_KEY).containsKey(name);
   }

   public boolean hasLogger(final String name, final MessageFactory messageFactory) {
      return this.getOrCreateInnerMap(factoryKey(messageFactory)).containsKey(name);
   }

   public boolean hasLogger(final String name, final Class<? extends MessageFactory> messageFactoryClass) {
      return this.getOrCreateInnerMap(factoryClassKey(messageFactoryClass)).containsKey(name);
   }

   public void putIfAbsent(final String name, final MessageFactory messageFactory, final T logger) {
      this.factory.putIfAbsent(this.getOrCreateInnerMap(factoryKey(messageFactory)), name, logger);
   }

   public static class ConcurrentMapFactory<T extends ExtendedLogger> implements LoggerRegistry.MapFactory<T> {
      @Override
      public Map<String, T> createInnerMap() {
         return new ConcurrentHashMap<>();
      }

      @Override
      public Map<String, Map<String, T>> createOuterMap() {
         return new ConcurrentHashMap<>();
      }

      @Override
      public void putIfAbsent(final Map<String, T> innerMap, final String name, final T logger) {
         ((ConcurrentMap)innerMap).putIfAbsent(name, logger);
      }
   }

   public interface MapFactory<T extends ExtendedLogger> {
      Map<String, T> createInnerMap();

      Map<String, Map<String, T>> createOuterMap();

      void putIfAbsent(Map<String, T> innerMap, String name, T logger);
   }

   public static class WeakMapFactory<T extends ExtendedLogger> implements LoggerRegistry.MapFactory<T> {
      @Override
      public Map<String, T> createInnerMap() {
         return new WeakHashMap<>();
      }

      @Override
      public Map<String, Map<String, T>> createOuterMap() {
         return new WeakHashMap<>();
      }

      @Override
      public void putIfAbsent(final Map<String, T> innerMap, final String name, final T logger) {
         innerMap.put(name, logger);
      }
   }
}
