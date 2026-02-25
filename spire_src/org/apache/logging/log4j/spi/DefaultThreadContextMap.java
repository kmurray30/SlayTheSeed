package org.apache.logging.log4j.spi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import org.apache.logging.log4j.util.BiConsumer;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.TriConsumer;

public class DefaultThreadContextMap implements ThreadContextMap, ReadOnlyStringMap {
   private static final long serialVersionUID = 8218007901108944053L;
   public static final String INHERITABLE_MAP = "isThreadContextMapInheritable";
   private final boolean useMap;
   private final ThreadLocal<Map<String, String>> localMap;
   private static boolean inheritableMap;

   static ThreadLocal<Map<String, String>> createThreadLocalMap(final boolean isMapEnabled) {
      return (ThreadLocal<Map<String, String>>)(inheritableMap ? new InheritableThreadLocal<Map<String, String>>() {
         protected Map<String, String> childValue(final Map<String, String> parentValue) {
            return parentValue != null && isMapEnabled ? Collections.unmodifiableMap(new HashMap<>(parentValue)) : null;
         }
      } : new ThreadLocal<>());
   }

   static void init() {
      inheritableMap = PropertiesUtil.getProperties().getBooleanProperty("isThreadContextMapInheritable");
   }

   public DefaultThreadContextMap() {
      this(true);
   }

   public DefaultThreadContextMap(final boolean useMap) {
      this.useMap = useMap;
      this.localMap = createThreadLocalMap(useMap);
   }

   @Override
   public void put(final String key, final String value) {
      if (this.useMap) {
         Map<String, String> map = this.localMap.get();
         Map<String, String> var4 = map == null ? new HashMap(1) : new HashMap<>(map);
         var4.put(key, value);
         this.localMap.set(Collections.unmodifiableMap(var4));
      }
   }

   public void putAll(final Map<String, String> m) {
      if (this.useMap) {
         Map<String, String> map = this.localMap.get();
         Map<String, String> var5 = map == null ? new HashMap(m.size()) : new HashMap<>(map);

         for (Entry<String, String> e : m.entrySet()) {
            var5.put(e.getKey(), e.getValue());
         }

         this.localMap.set(Collections.unmodifiableMap(var5));
      }
   }

   @Override
   public String get(final String key) {
      Map<String, String> map = this.localMap.get();
      return map == null ? null : map.get(key);
   }

   @Override
   public void remove(final String key) {
      Map<String, String> map = this.localMap.get();
      if (map != null) {
         Map<String, String> copy = new HashMap<>(map);
         copy.remove(key);
         this.localMap.set(Collections.unmodifiableMap(copy));
      }
   }

   public void removeAll(final Iterable<String> keys) {
      Map<String, String> map = this.localMap.get();
      if (map != null) {
         Map<String, String> copy = new HashMap<>(map);

         for (String key : keys) {
            copy.remove(key);
         }

         this.localMap.set(Collections.unmodifiableMap(copy));
      }
   }

   @Override
   public void clear() {
      this.localMap.remove();
   }

   @Override
   public Map<String, String> toMap() {
      return this.getCopy();
   }

   @Override
   public boolean containsKey(final String key) {
      Map<String, String> map = this.localMap.get();
      return map != null && map.containsKey(key);
   }

   @Override
   public <V> void forEach(final BiConsumer<String, ? super V> action) {
      Map<String, String> map = this.localMap.get();
      if (map != null) {
         for (Entry<String, String> entry : map.entrySet()) {
            V value = (V)entry.getValue();
            action.accept(entry.getKey(), value);
         }
      }
   }

   @Override
   public <V, S> void forEach(final TriConsumer<String, ? super V, S> action, final S state) {
      Map<String, String> map = this.localMap.get();
      if (map != null) {
         for (Entry<String, String> entry : map.entrySet()) {
            V value = (V)entry.getValue();
            action.accept(entry.getKey(), value, state);
         }
      }
   }

   @Override
   public <V> V getValue(final String key) {
      Map<String, String> map = this.localMap.get();
      return (V)(map == null ? null : (String)map.get(key));
   }

   @Override
   public Map<String, String> getCopy() {
      Map<String, String> map = this.localMap.get();
      return map == null ? new HashMap<>() : new HashMap<>(map);
   }

   @Override
   public Map<String, String> getImmutableMapOrNull() {
      return this.localMap.get();
   }

   @Override
   public boolean isEmpty() {
      Map<String, String> map = this.localMap.get();
      return map == null || map.isEmpty();
   }

   @Override
   public int size() {
      Map<String, String> map = this.localMap.get();
      return map == null ? 0 : map.size();
   }

   @Override
   public String toString() {
      Map<String, String> map = this.localMap.get();
      return map == null ? "{}" : map.toString();
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = 1;
      Map<String, String> map = this.localMap.get();
      result = 31 * result + (map == null ? 0 : map.hashCode());
      return 31 * result + Boolean.valueOf(this.useMap).hashCode();
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else {
         if (obj instanceof DefaultThreadContextMap) {
            DefaultThreadContextMap other = (DefaultThreadContextMap)obj;
            if (this.useMap != other.useMap) {
               return false;
            }
         }

         if (!(obj instanceof ThreadContextMap)) {
            return false;
         } else {
            ThreadContextMap other = (ThreadContextMap)obj;
            Map<String, String> map = this.localMap.get();
            Map<String, String> otherMap = other.getImmutableMapOrNull();
            return Objects.equals(map, otherMap);
         }
      }
   }

   static {
      init();
   }
}
