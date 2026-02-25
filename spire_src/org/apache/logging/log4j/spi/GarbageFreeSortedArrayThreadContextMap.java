package org.apache.logging.log4j.spi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;

class GarbageFreeSortedArrayThreadContextMap implements ReadOnlyThreadContextMap, ObjectThreadContextMap {
   public static final String INHERITABLE_MAP = "isThreadContextMapInheritable";
   protected static final int DEFAULT_INITIAL_CAPACITY = 16;
   protected static final String PROPERTY_NAME_INITIAL_CAPACITY = "log4j2.ThreadContext.initial.capacity";
   protected final ThreadLocal<StringMap> localMap = this.createThreadLocalMap();
   private static volatile int initialCapacity;
   private static volatile boolean inheritableMap;

   static void init() {
      PropertiesUtil properties = PropertiesUtil.getProperties();
      initialCapacity = properties.getIntegerProperty("log4j2.ThreadContext.initial.capacity", 16);
      inheritableMap = properties.getBooleanProperty("isThreadContextMapInheritable");
   }

   public GarbageFreeSortedArrayThreadContextMap() {
   }

   private ThreadLocal<StringMap> createThreadLocalMap() {
      return (ThreadLocal<StringMap>)(inheritableMap ? new InheritableThreadLocal<StringMap>() {
         protected StringMap childValue(final StringMap parentValue) {
            return parentValue != null ? GarbageFreeSortedArrayThreadContextMap.this.createStringMap(parentValue) : null;
         }
      } : new ThreadLocal<>());
   }

   protected StringMap createStringMap() {
      return new SortedArrayStringMap(initialCapacity);
   }

   protected StringMap createStringMap(final ReadOnlyStringMap original) {
      return new SortedArrayStringMap(original);
   }

   private StringMap getThreadLocalMap() {
      StringMap map = this.localMap.get();
      if (map == null) {
         map = this.createStringMap();
         this.localMap.set(map);
      }

      return map;
   }

   @Override
   public void put(final String key, final String value) {
      this.getThreadLocalMap().putValue(key, value);
   }

   @Override
   public void putValue(final String key, final Object value) {
      this.getThreadLocalMap().putValue(key, value);
   }

   @Override
   public void putAll(final Map<String, String> values) {
      if (values != null && !values.isEmpty()) {
         StringMap map = this.getThreadLocalMap();

         for (Entry<String, String> entry : values.entrySet()) {
            map.putValue(entry.getKey(), entry.getValue());
         }
      }
   }

   @Override
   public <V> void putAllValues(final Map<String, V> values) {
      if (values != null && !values.isEmpty()) {
         StringMap map = this.getThreadLocalMap();

         for (Entry<String, V> entry : values.entrySet()) {
            map.putValue(entry.getKey(), entry.getValue());
         }
      }
   }

   @Override
   public String get(final String key) {
      return this.getValue(key);
   }

   @Override
   public <V> V getValue(final String key) {
      StringMap map = this.localMap.get();
      return map == null ? null : map.getValue(key);
   }

   @Override
   public void remove(final String key) {
      StringMap map = this.localMap.get();
      if (map != null) {
         map.remove(key);
      }
   }

   @Override
   public void removeAll(final Iterable<String> keys) {
      StringMap map = this.localMap.get();
      if (map != null) {
         for (String key : keys) {
            map.remove(key);
         }
      }
   }

   @Override
   public void clear() {
      StringMap map = this.localMap.get();
      if (map != null) {
         map.clear();
      }
   }

   @Override
   public boolean containsKey(final String key) {
      StringMap map = this.localMap.get();
      return map != null && map.containsKey(key);
   }

   @Override
   public Map<String, String> getCopy() {
      StringMap map = this.localMap.get();
      return (Map<String, String>)(map == null ? new HashMap<>() : map.toMap());
   }

   @Override
   public StringMap getReadOnlyContextData() {
      StringMap map = this.localMap.get();
      if (map == null) {
         map = this.createStringMap();
         this.localMap.set(map);
      }

      return map;
   }

   @Override
   public Map<String, String> getImmutableMapOrNull() {
      StringMap map = this.localMap.get();
      return map == null ? null : Collections.unmodifiableMap(map.toMap());
   }

   @Override
   public boolean isEmpty() {
      StringMap map = this.localMap.get();
      return map == null || map.isEmpty();
   }

   @Override
   public String toString() {
      StringMap map = this.localMap.get();
      return map == null ? "{}" : map.toString();
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = 1;
      StringMap map = this.localMap.get();
      return 31 * result + (map == null ? 0 : map.hashCode());
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (!(obj instanceof ThreadContextMap)) {
         return false;
      } else {
         ThreadContextMap other = (ThreadContextMap)obj;
         Map<String, String> map = this.getImmutableMapOrNull();
         Map<String, String> otherMap = other.getImmutableMapOrNull();
         return Objects.equals(map, otherMap);
      }
   }

   static {
      init();
   }
}
