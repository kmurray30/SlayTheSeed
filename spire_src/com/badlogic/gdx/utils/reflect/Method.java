package com.badlogic.gdx.utils.reflect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

public final class Method {
   private final java.lang.reflect.Method method;

   Method(java.lang.reflect.Method method) {
      this.method = method;
   }

   public String getName() {
      return this.method.getName();
   }

   public Class getReturnType() {
      return this.method.getReturnType();
   }

   public Class[] getParameterTypes() {
      return this.method.getParameterTypes();
   }

   public Class getDeclaringClass() {
      return this.method.getDeclaringClass();
   }

   public boolean isAccessible() {
      return this.method.isAccessible();
   }

   public void setAccessible(boolean accessible) {
      this.method.setAccessible(accessible);
   }

   public boolean isAbstract() {
      return Modifier.isAbstract(this.method.getModifiers());
   }

   public boolean isDefaultAccess() {
      return !this.isPrivate() && !this.isProtected() && !this.isPublic();
   }

   public boolean isFinal() {
      return Modifier.isFinal(this.method.getModifiers());
   }

   public boolean isPrivate() {
      return Modifier.isPrivate(this.method.getModifiers());
   }

   public boolean isProtected() {
      return Modifier.isProtected(this.method.getModifiers());
   }

   public boolean isPublic() {
      return Modifier.isPublic(this.method.getModifiers());
   }

   public boolean isNative() {
      return Modifier.isNative(this.method.getModifiers());
   }

   public boolean isStatic() {
      return Modifier.isStatic(this.method.getModifiers());
   }

   public boolean isVarArgs() {
      return this.method.isVarArgs();
   }

   public Object invoke(Object obj, Object... args) throws ReflectionException {
      try {
         return this.method.invoke(obj, args);
      } catch (IllegalArgumentException var4) {
         throw new ReflectionException("Illegal argument(s) supplied to method: " + this.getName(), var4);
      } catch (IllegalAccessException var5) {
         throw new ReflectionException("Illegal access to method: " + this.getName(), var5);
      } catch (InvocationTargetException var6) {
         throw new ReflectionException("Exception occurred in method: " + this.getName(), var6);
      }
   }

   public boolean isAnnotationPresent(Class<? extends java.lang.annotation.Annotation> annotationType) {
      return this.method.isAnnotationPresent(annotationType);
   }

   public Annotation[] getDeclaredAnnotations() {
      java.lang.annotation.Annotation[] annotations = this.method.getDeclaredAnnotations();
      Annotation[] result = new Annotation[annotations.length];

      for (int i = 0; i < annotations.length; i++) {
         result[i] = new Annotation(annotations[i]);
      }

      return result;
   }

   public Annotation getDeclaredAnnotation(Class<? extends java.lang.annotation.Annotation> annotationType) {
      java.lang.annotation.Annotation[] annotations = this.method.getDeclaredAnnotations();
      if (annotations == null) {
         return null;
      } else {
         for (java.lang.annotation.Annotation annotation : annotations) {
            if (annotation.annotationType().equals(annotationType)) {
               return new Annotation(annotation);
            }
         }

         return null;
      }
   }
}
