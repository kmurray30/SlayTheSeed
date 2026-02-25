package com.badlogic.gdx.utils;

public class Pools {
   private static final ObjectMap<Class, Pool> typePools = new ObjectMap<>();

   public static <T> Pool<T> get(Class<T> type, int max) {
      Pool pool = typePools.get(type);
      if (pool == null) {
         pool = new ReflectionPool<>(type, 4, max);
         typePools.put(type, pool);
      }

      return pool;
   }

   public static <T> Pool<T> get(Class<T> type) {
      return get(type, 100);
   }

   public static <T> void set(Class<T> type, Pool<T> pool) {
      typePools.put(type, pool);
   }

   public static <T> T obtain(Class<T> type) {
      return get(type).obtain();
   }

   public static void free(Object object) {
      if (object == null) {
         throw new IllegalArgumentException("Object cannot be null.");
      } else {
         Pool pool = typePools.get(object.getClass());
         if (pool != null) {
            pool.free(object);
         }
      }
   }

   public static void freeAll(Array objects) {
      freeAll(objects, false);
   }

   public static void freeAll(Array objects, boolean samePool) {
      if (objects == null) {
         throw new IllegalArgumentException("Objects cannot be null.");
      } else {
         Pool pool = null;
         int i = 0;

         for (int n = objects.size; i < n; i++) {
            Object object = objects.get(i);
            if (object != null) {
               if (pool == null) {
                  pool = typePools.get(object.getClass());
                  if (pool == null) {
                     continue;
                  }
               }

               pool.free(object);
               if (!samePool) {
                  pool = null;
               }
            }
         }
      }
   }

   private Pools() {
   }
}
