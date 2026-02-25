package org.apache.logging.log4j;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.logging.log4j.util.PerformanceSensitive;
import org.apache.logging.log4j.util.StringBuilderFormattable;

public final class MarkerManager {
   private static final ConcurrentMap<String, Marker> MARKERS = new ConcurrentHashMap<>();

   private MarkerManager() {
   }

   public static void clear() {
      MARKERS.clear();
   }

   public static boolean exists(final String key) {
      return MARKERS.containsKey(key);
   }

   public static Marker getMarker(final String name) {
      Marker result = MARKERS.get(name);
      if (result == null) {
         MARKERS.putIfAbsent(name, new MarkerManager.Log4jMarker(name));
         result = MARKERS.get(name);
      }

      return result;
   }

   @Deprecated
   public static Marker getMarker(final String name, final String parent) {
      Marker parentMarker = MARKERS.get(parent);
      if (parentMarker == null) {
         throw new IllegalArgumentException("Parent Marker " + parent + " has not been defined");
      } else {
         return getMarker(name, parentMarker);
      }
   }

   @Deprecated
   public static Marker getMarker(final String name, final Marker parent) {
      return getMarker(name).addParents(parent);
   }

   private static void requireNonNull(final Object obj, final String message) {
      if (obj == null) {
         throw new IllegalArgumentException(message);
      }
   }

   public static class Log4jMarker implements Marker, StringBuilderFormattable {
      private static final long serialVersionUID = 100L;
      private final String name;
      private volatile Marker[] parents;

      private Log4jMarker() {
         this.name = null;
         this.parents = null;
      }

      public Log4jMarker(final String name) {
         MarkerManager.requireNonNull(name, "Marker name cannot be null.");
         this.name = name;
         this.parents = null;
      }

      @Override
      public synchronized Marker addParents(final Marker... parentMarkers) {
         MarkerManager.requireNonNull(parentMarkers, "A parent marker must be specified");
         Marker[] localParents = this.parents;
         int count = 0;
         int size = parentMarkers.length;
         if (localParents != null) {
            for (Marker parent : parentMarkers) {
               if (!contains(parent, localParents) && !parent.isInstanceOf(this)) {
                  count++;
               }
            }

            if (count == 0) {
               return this;
            }

            size = localParents.length + count;
         }

         Marker[] markers = new Marker[size];
         if (localParents != null) {
            System.arraycopy(localParents, 0, markers, 0, localParents.length);
         }

         int index = localParents == null ? 0 : localParents.length;

         for (Marker parentx : parentMarkers) {
            if (localParents == null || !contains(parentx, localParents) && !parentx.isInstanceOf(this)) {
               markers[index++] = parentx;
            }
         }

         this.parents = markers;
         return this;
      }

      @Override
      public synchronized boolean remove(final Marker parent) {
         MarkerManager.requireNonNull(parent, "A parent marker must be specified");
         Marker[] localParents = this.parents;
         if (localParents == null) {
            return false;
         } else {
            int localParentsLength = localParents.length;
            if (localParentsLength == 1) {
               if (localParents[0].equals(parent)) {
                  this.parents = null;
                  return true;
               } else {
                  return false;
               }
            } else {
               int index = 0;
               Marker[] markers = new Marker[localParentsLength - 1];

               for (int i = 0; i < localParentsLength; i++) {
                  Marker marker = localParents[i];
                  if (!marker.equals(parent)) {
                     if (index == localParentsLength - 1) {
                        return false;
                     }

                     markers[index++] = marker;
                  }
               }

               this.parents = markers;
               return true;
            }
         }
      }

      @Override
      public Marker setParents(final Marker... markers) {
         if (markers != null && markers.length != 0) {
            Marker[] array = new Marker[markers.length];
            System.arraycopy(markers, 0, array, 0, markers.length);
            this.parents = array;
         } else {
            this.parents = null;
         }

         return this;
      }

      @Override
      public String getName() {
         return this.name;
      }

      @Override
      public Marker[] getParents() {
         Marker[] parentsSnapshot = this.parents;
         return parentsSnapshot == null ? null : Arrays.copyOf(parentsSnapshot, parentsSnapshot.length);
      }

      @Override
      public boolean hasParents() {
         return this.parents != null;
      }

      @PerformanceSensitive({"allocation", "unrolled"})
      @Override
      public boolean isInstanceOf(final Marker marker) {
         MarkerManager.requireNonNull(marker, "A marker parameter is required");
         if (this == marker) {
            return true;
         } else {
            Marker[] localParents = this.parents;
            if (localParents != null) {
               int localParentsLength = localParents.length;
               if (localParentsLength == 1) {
                  return checkParent(localParents[0], marker);
               }

               if (localParentsLength == 2) {
                  return checkParent(localParents[0], marker) || checkParent(localParents[1], marker);
               }

               for (int i = 0; i < localParentsLength; i++) {
                  Marker localParent = localParents[i];
                  if (checkParent(localParent, marker)) {
                     return true;
                  }
               }
            }

            return false;
         }
      }

      @PerformanceSensitive({"allocation", "unrolled"})
      @Override
      public boolean isInstanceOf(final String markerName) {
         MarkerManager.requireNonNull(markerName, "A marker name is required");
         if (markerName.equals(this.getName())) {
            return true;
         } else {
            Marker marker = MarkerManager.MARKERS.get(markerName);
            if (marker == null) {
               return false;
            } else {
               Marker[] localParents = this.parents;
               if (localParents != null) {
                  int localParentsLength = localParents.length;
                  if (localParentsLength == 1) {
                     return checkParent(localParents[0], marker);
                  }

                  if (localParentsLength == 2) {
                     return checkParent(localParents[0], marker) || checkParent(localParents[1], marker);
                  }

                  for (int i = 0; i < localParentsLength; i++) {
                     Marker localParent = localParents[i];
                     if (checkParent(localParent, marker)) {
                        return true;
                     }
                  }
               }

               return false;
            }
         }
      }

      @PerformanceSensitive({"allocation", "unrolled"})
      private static boolean checkParent(final Marker parent, final Marker marker) {
         if (parent == marker) {
            return true;
         } else {
            Marker[] localParents = parent instanceof MarkerManager.Log4jMarker ? ((MarkerManager.Log4jMarker)parent).parents : parent.getParents();
            if (localParents != null) {
               int localParentsLength = localParents.length;
               if (localParentsLength == 1) {
                  return checkParent(localParents[0], marker);
               }

               if (localParentsLength == 2) {
                  return checkParent(localParents[0], marker) || checkParent(localParents[1], marker);
               }

               for (int i = 0; i < localParentsLength; i++) {
                  Marker localParent = localParents[i];
                  if (checkParent(localParent, marker)) {
                     return true;
                  }
               }
            }

            return false;
         }
      }

      @PerformanceSensitive("allocation")
      private static boolean contains(final Marker parent, final Marker... localParents) {
         int i = 0;

         for (int localParentsLength = localParents.length; i < localParentsLength; i++) {
            Marker marker = localParents[i];
            if (marker == parent) {
               return true;
            }
         }

         return false;
      }

      @Override
      public boolean equals(final Object o) {
         if (this == o) {
            return true;
         } else if (o != null && o instanceof Marker) {
            Marker marker = (Marker)o;
            return this.name.equals(marker.getName());
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return this.name.hashCode();
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();
         this.formatTo(sb);
         return sb.toString();
      }

      @Override
      public void formatTo(final StringBuilder sb) {
         sb.append(this.name);
         Marker[] localParents = this.parents;
         if (localParents != null) {
            addParentInfo(sb, localParents);
         }
      }

      @PerformanceSensitive("allocation")
      private static void addParentInfo(final StringBuilder sb, final Marker... parents) {
         sb.append("[ ");
         boolean first = true;
         int i = 0;

         for (int parentsLength = parents.length; i < parentsLength; i++) {
            Marker marker = parents[i];
            if (!first) {
               sb.append(", ");
            }

            first = false;
            sb.append(marker.getName());
            Marker[] p = marker instanceof MarkerManager.Log4jMarker ? ((MarkerManager.Log4jMarker)marker).parents : marker.getParents();
            if (p != null) {
               addParentInfo(sb, p);
            }
         }

         sb.append(" ]");
      }
   }
}
