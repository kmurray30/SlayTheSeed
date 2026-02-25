package org.apache.logging.log4j.util;

public interface StringMap extends ReadOnlyStringMap {
   void clear();

   @Override
   boolean equals(final Object obj);

   void freeze();

   @Override
   int hashCode();

   boolean isFrozen();

   void putAll(final ReadOnlyStringMap source);

   void putValue(final String key, final Object value);

   void remove(final String key);
}
