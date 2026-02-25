package org.apache.logging.log4j.util;

public interface TriConsumer<K, V, S> {
   void accept(K k, V v, S s);
}
