package org.apache.logging.log4j.core.util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.Objects;

public final class ReflectionUtil {
   private ReflectionUtil() {
   }

   public static <T extends AccessibleObject & Member> boolean isAccessible(final T member) {
      Objects.requireNonNull(member, "No member provided");
      return Modifier.isPublic(member.getModifiers()) && Modifier.isPublic(member.getDeclaringClass().getModifiers());
   }

   public static <T extends AccessibleObject & Member> void makeAccessible(final T member) {
      if (!isAccessible(member) && !member.isAccessible()) {
         member.setAccessible(true);
      }
   }

   public static void makeAccessible(final Field field) {
      Objects.requireNonNull(field, "No field provided");
      if ((!isAccessible(field) || Modifier.isFinal(field.getModifiers())) && !field.isAccessible()) {
         field.setAccessible(true);
      }
   }

   public static Object getFieldValue(final Field field, final Object instance) {
      makeAccessible(field);
      if (!Modifier.isStatic(field.getModifiers())) {
         Objects.requireNonNull(instance, "No instance given for non-static field");
      }

      try {
         return field.get(instance);
      } catch (IllegalAccessException var3) {
         throw new UnsupportedOperationException(var3);
      }
   }

   public static Object getStaticFieldValue(final Field field) {
      return getFieldValue(field, null);
   }

   public static void setFieldValue(final Field field, final Object instance, final Object value) {
      makeAccessible(field);
      if (!Modifier.isStatic(field.getModifiers())) {
         Objects.requireNonNull(instance, "No instance given for non-static field");
      }

      try {
         field.set(instance, value);
      } catch (IllegalAccessException var4) {
         throw new UnsupportedOperationException(var4);
      }
   }

   public static void setStaticFieldValue(final Field field, final Object value) {
      setFieldValue(field, null, value);
   }

   public static <T> Constructor<T> getDefaultConstructor(final Class<T> clazz) {
      Objects.requireNonNull(clazz, "No class provided");

      try {
         Constructor<T> constructor = clazz.getDeclaredConstructor();
         makeAccessible(constructor);
         return constructor;
      } catch (NoSuchMethodException var4) {
         try {
            Constructor<T> constructorx = clazz.getConstructor();
            makeAccessible(constructorx);
            return constructorx;
         } catch (NoSuchMethodException var3) {
            throw new IllegalStateException(var3);
         }
      }
   }

   public static <T> T instantiate(final Class<T> clazz) {
      Objects.requireNonNull(clazz, "No class provided");
      Constructor<T> constructor = getDefaultConstructor(clazz);

      try {
         return constructor.newInstance();
      } catch (InstantiationException | LinkageError var3) {
         throw new IllegalArgumentException(var3);
      } catch (IllegalAccessException var4) {
         throw new IllegalStateException(var4);
      } catch (InvocationTargetException var5) {
         Throwables.rethrow(var5.getCause());
         throw new InternalError("Unreachable");
      }
   }
}
