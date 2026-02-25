package org.apache.logging.log4j;

import java.io.Serializable;

public interface Marker extends Serializable {
   Marker addParents(Marker... markers);

   @Override
   boolean equals(Object obj);

   String getName();

   Marker[] getParents();

   @Override
   int hashCode();

   boolean hasParents();

   boolean isInstanceOf(Marker m);

   boolean isInstanceOf(String name);

   boolean remove(Marker marker);

   Marker setParents(Marker... markers);
}
