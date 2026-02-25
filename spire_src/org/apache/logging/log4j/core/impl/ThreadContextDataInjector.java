package org.apache.logging.log4j.core.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.ContextDataInjector;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.util.ContextDataProvider;
import org.apache.logging.log4j.spi.ReadOnlyThreadContextMap;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.LoaderUtil;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.StringMap;

public class ThreadContextDataInjector {
   private static final Logger LOGGER = StatusLogger.getLogger();
   public static Collection<ContextDataProvider> contextDataProviders = new ConcurrentLinkedDeque<>();
   private static volatile List<ContextDataProvider> serviceProviders = null;
   private static final Lock providerLock = new ReentrantLock();

   public static void initServiceProviders() {
      if (serviceProviders == null) {
         providerLock.lock();

         try {
            if (serviceProviders == null) {
               serviceProviders = getServiceProviders();
            }
         } finally {
            providerLock.unlock();
         }
      }
   }

   private static List<ContextDataProvider> getServiceProviders() {
      List<ContextDataProvider> providers = new ArrayList<>();

      for (ClassLoader classLoader : LoaderUtil.getClassLoaders()) {
         try {
            for (ContextDataProvider provider : ServiceLoader.load(ContextDataProvider.class, classLoader)) {
               if (providers.stream().noneMatch(p -> p.getClass().isAssignableFrom(provider.getClass()))) {
                  providers.add(provider);
               }
            }
         } catch (Throwable var7) {
            LOGGER.debug("Unable to access Context Data Providers {}", var7.getMessage());
         }
      }

      return providers;
   }

   public static void copyProperties(final List<Property> properties, final StringMap result) {
      if (properties != null) {
         for (int i = 0; i < properties.size(); i++) {
            Property prop = properties.get(i);
            result.putValue(prop.getName(), prop.getValue());
         }
      }
   }

   private static List<ContextDataProvider> getProviders() {
      initServiceProviders();
      List<ContextDataProvider> providers = new ArrayList<>(contextDataProviders);
      if (serviceProviders != null) {
         providers.addAll(serviceProviders);
      }

      return providers;
   }

   public static class ForCopyOnWriteThreadContextMap implements ContextDataInjector {
      private final List<ContextDataProvider> providers = ThreadContextDataInjector.getProviders();

      @Override
      public StringMap injectContextData(final List<Property> props, final StringMap ignore) {
         if (this.providers.size() != 1 || props != null && !props.isEmpty()) {
            int count = props == null ? 0 : props.size();
            StringMap[] maps = new StringMap[this.providers.size()];

            for (int i = 0; i < this.providers.size(); i++) {
               maps[i] = this.providers.get(i).supplyStringMap();
               count += maps[i].size();
            }

            StringMap result = ContextDataFactory.createContextData(count);
            ThreadContextDataInjector.copyProperties(props, result);

            for (StringMap map : maps) {
               result.putAll(map);
            }

            return result;
         } else {
            return this.providers.get(0).supplyStringMap();
         }
      }

      @Override
      public ReadOnlyStringMap rawContextData() {
         return ThreadContext.getThreadContextMap().getReadOnlyContextData();
      }
   }

   public static class ForDefaultThreadContextMap implements ContextDataInjector {
      private final List<ContextDataProvider> providers = ThreadContextDataInjector.getProviders();

      @Override
      public StringMap injectContextData(final List<Property> props, final StringMap contextData) {
         Map<String, String> copy;
         if (this.providers.size() == 1) {
            copy = this.providers.get(0).supplyContextData();
         } else {
            copy = new HashMap<>();

            for (ContextDataProvider provider : this.providers) {
               copy.putAll(provider.supplyContextData());
            }
         }

         if (props != null && !props.isEmpty()) {
            StringMap result = new JdkMapAdapterStringMap(new HashMap<>(copy));

            for (int i = 0; i < props.size(); i++) {
               Property prop = props.get(i);
               if (!copy.containsKey(prop.getName())) {
                  result.putValue(prop.getName(), prop.getValue());
               }
            }

            result.freeze();
            return result;
         } else {
            return (StringMap)(copy.isEmpty() ? ContextDataFactory.emptyFrozenContextData() : frozenStringMap(copy));
         }
      }

      private static JdkMapAdapterStringMap frozenStringMap(final Map<String, String> copy) {
         JdkMapAdapterStringMap result = new JdkMapAdapterStringMap(copy);
         result.freeze();
         return result;
      }

      @Override
      public ReadOnlyStringMap rawContextData() {
         ReadOnlyThreadContextMap map = ThreadContext.getThreadContextMap();
         if (map instanceof ReadOnlyStringMap) {
            return (ReadOnlyStringMap)map;
         } else {
            Map<String, String> copy = ThreadContext.getImmutableContext();
            return (ReadOnlyStringMap)(copy.isEmpty() ? ContextDataFactory.emptyFrozenContextData() : new JdkMapAdapterStringMap(copy));
         }
      }
   }

   public static class ForGarbageFreeThreadContextMap implements ContextDataInjector {
      private final List<ContextDataProvider> providers = ThreadContextDataInjector.getProviders();

      @Override
      public StringMap injectContextData(final List<Property> props, final StringMap reusable) {
         ThreadContextDataInjector.copyProperties(props, reusable);

         for (int i = 0; i < this.providers.size(); i++) {
            reusable.putAll(this.providers.get(i).supplyStringMap());
         }

         return reusable;
      }

      @Override
      public ReadOnlyStringMap rawContextData() {
         return ThreadContext.getThreadContextMap().getReadOnlyContextData();
      }
   }
}
