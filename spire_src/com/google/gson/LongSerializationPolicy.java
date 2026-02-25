package com.google.gson;

public enum LongSerializationPolicy {
   DEFAULT {
      @Override
      public JsonElement serialize(Long value) {
         return new JsonPrimitive((Number)value);
      }
   },
   STRING {
      @Override
      public JsonElement serialize(Long value) {
         return new JsonPrimitive(String.valueOf(value));
      }
   };

   private LongSerializationPolicy() {
   }

   public abstract JsonElement serialize(Long var1);
}
