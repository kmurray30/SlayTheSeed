package com.sun.jna;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultTypeMapper implements TypeMapper {
   private List<DefaultTypeMapper.Entry> toNativeConverters = new ArrayList<>();
   private List<DefaultTypeMapper.Entry> fromNativeConverters = new ArrayList<>();

   private Class<?> getAltClass(Class<?> cls) {
      if (cls == Boolean.class) {
         return boolean.class;
      } else if (cls == boolean.class) {
         return Boolean.class;
      } else if (cls == Byte.class) {
         return byte.class;
      } else if (cls == byte.class) {
         return Byte.class;
      } else if (cls == Character.class) {
         return char.class;
      } else if (cls == char.class) {
         return Character.class;
      } else if (cls == Short.class) {
         return short.class;
      } else if (cls == short.class) {
         return Short.class;
      } else if (cls == Integer.class) {
         return int.class;
      } else if (cls == int.class) {
         return Integer.class;
      } else if (cls == Long.class) {
         return long.class;
      } else if (cls == long.class) {
         return Long.class;
      } else if (cls == Float.class) {
         return float.class;
      } else if (cls == float.class) {
         return Float.class;
      } else if (cls == Double.class) {
         return double.class;
      } else {
         return cls == double.class ? Double.class : null;
      }
   }

   public void addToNativeConverter(Class<?> cls, ToNativeConverter converter) {
      this.toNativeConverters.add(new DefaultTypeMapper.Entry(cls, converter));
      Class<?> alt = this.getAltClass(cls);
      if (alt != null) {
         this.toNativeConverters.add(new DefaultTypeMapper.Entry(alt, converter));
      }
   }

   public void addFromNativeConverter(Class<?> cls, FromNativeConverter converter) {
      this.fromNativeConverters.add(new DefaultTypeMapper.Entry(cls, converter));
      Class<?> alt = this.getAltClass(cls);
      if (alt != null) {
         this.fromNativeConverters.add(new DefaultTypeMapper.Entry(alt, converter));
      }
   }

   public void addTypeConverter(Class<?> cls, TypeConverter converter) {
      this.addFromNativeConverter(cls, converter);
      this.addToNativeConverter(cls, converter);
   }

   private Object lookupConverter(Class<?> javaClass, Collection<? extends DefaultTypeMapper.Entry> converters) {
      for (DefaultTypeMapper.Entry entry : converters) {
         if (entry.type.isAssignableFrom(javaClass)) {
            return entry.converter;
         }
      }

      return null;
   }

   @Override
   public FromNativeConverter getFromNativeConverter(Class<?> javaType) {
      return (FromNativeConverter)this.lookupConverter(javaType, this.fromNativeConverters);
   }

   @Override
   public ToNativeConverter getToNativeConverter(Class<?> javaType) {
      return (ToNativeConverter)this.lookupConverter(javaType, this.toNativeConverters);
   }

   private static class Entry {
      public Class<?> type;
      public Object converter;

      public Entry(Class<?> type, Object converter) {
         this.type = type;
         this.converter = converter;
      }
   }
}
