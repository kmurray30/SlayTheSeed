package org.apache.logging.log4j.spi;

import java.util.Map;

public interface ObjectThreadContextMap extends CleanableThreadContextMap {
   <V> V getValue(String key);

   <V> void putValue(String key, V value);

   <V> void putAllValues(Map<String, V> values);
}
