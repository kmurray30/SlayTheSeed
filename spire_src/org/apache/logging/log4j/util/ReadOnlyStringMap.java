package org.apache.logging.log4j.util;

import java.io.Serializable;
import java.util.Map;

public interface ReadOnlyStringMap extends Serializable {
   Map<String, String> toMap();

   boolean containsKey(String key);

   <V> void forEach(final BiConsumer<String, ? super V> action);

   <V, S> void forEach(final TriConsumer<String, ? super V, S> action, final S state);

   <V> V getValue(final String key);

   boolean isEmpty();

   int size();
}
