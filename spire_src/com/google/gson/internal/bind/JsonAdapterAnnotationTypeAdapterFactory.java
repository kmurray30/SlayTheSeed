package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.reflect.TypeToken;

public final class JsonAdapterAnnotationTypeAdapterFactory implements TypeAdapterFactory {
   private final ConstructorConstructor constructorConstructor;

   public JsonAdapterAnnotationTypeAdapterFactory(ConstructorConstructor constructorConstructor) {
      this.constructorConstructor = constructorConstructor;
   }

   @Override
   public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> targetType) {
      JsonAdapter annotation = targetType.getRawType().getAnnotation(JsonAdapter.class);
      return (TypeAdapter<T>)(annotation == null ? null : getTypeAdapter(this.constructorConstructor, gson, targetType, annotation));
   }

   static TypeAdapter<?> getTypeAdapter(ConstructorConstructor constructorConstructor, Gson gson, TypeToken<?> fieldType, JsonAdapter annotation) {
      Class<?> value = annotation.value();
      if (TypeAdapter.class.isAssignableFrom(value)) {
         return constructorConstructor.get(TypeToken.get((Class<TypeAdapter<?>>)value)).construct();
      } else if (TypeAdapterFactory.class.isAssignableFrom(value)) {
         return constructorConstructor.get(TypeToken.get((Class<TypeAdapterFactory>)value)).construct().create(gson, fieldType);
      } else {
         throw new IllegalArgumentException("@JsonAdapter value must be TypeAdapter or TypeAdapterFactory reference.");
      }
   }
}
