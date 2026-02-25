package com.google.gson;

import com.google.gson.internal.$Gson$Preconditions;
import com.google.gson.internal.LazilyParsedNumber;
import java.math.BigDecimal;
import java.math.BigInteger;

public final class JsonPrimitive extends JsonElement {
   private static final Class<?>[] PRIMITIVE_TYPES = new Class[]{
      int.class,
      long.class,
      short.class,
      float.class,
      double.class,
      byte.class,
      boolean.class,
      char.class,
      Integer.class,
      Long.class,
      Short.class,
      Float.class,
      Double.class,
      Byte.class,
      Boolean.class,
      Character.class
   };
   private Object value;

   public JsonPrimitive(Boolean bool) {
      this.setValue(bool);
   }

   public JsonPrimitive(Number number) {
      this.setValue(number);
   }

   public JsonPrimitive(String string) {
      this.setValue(string);
   }

   public JsonPrimitive(Character c) {
      this.setValue(c);
   }

   JsonPrimitive(Object primitive) {
      this.setValue(primitive);
   }

   JsonPrimitive deepCopy() {
      return this;
   }

   void setValue(Object primitive) {
      if (primitive instanceof Character) {
         char c = (Character)primitive;
         this.value = String.valueOf(c);
      } else {
         $Gson$Preconditions.checkArgument(primitive instanceof Number || isPrimitiveOrString(primitive));
         this.value = primitive;
      }
   }

   public boolean isBoolean() {
      return this.value instanceof Boolean;
   }

   @Override
   Boolean getAsBooleanWrapper() {
      return (Boolean)this.value;
   }

   @Override
   public boolean getAsBoolean() {
      return this.isBoolean() ? this.getAsBooleanWrapper() : Boolean.parseBoolean(this.getAsString());
   }

   public boolean isNumber() {
      return this.value instanceof Number;
   }

   @Override
   public Number getAsNumber() {
      return (Number)(this.value instanceof String ? new LazilyParsedNumber((String)this.value) : (Number)this.value);
   }

   public boolean isString() {
      return this.value instanceof String;
   }

   @Override
   public String getAsString() {
      if (this.isNumber()) {
         return this.getAsNumber().toString();
      } else {
         return this.isBoolean() ? this.getAsBooleanWrapper().toString() : (String)this.value;
      }
   }

   @Override
   public double getAsDouble() {
      return this.isNumber() ? this.getAsNumber().doubleValue() : Double.parseDouble(this.getAsString());
   }

   @Override
   public BigDecimal getAsBigDecimal() {
      return this.value instanceof BigDecimal ? (BigDecimal)this.value : new BigDecimal(this.value.toString());
   }

   @Override
   public BigInteger getAsBigInteger() {
      return this.value instanceof BigInteger ? (BigInteger)this.value : new BigInteger(this.value.toString());
   }

   @Override
   public float getAsFloat() {
      return this.isNumber() ? this.getAsNumber().floatValue() : Float.parseFloat(this.getAsString());
   }

   @Override
   public long getAsLong() {
      return this.isNumber() ? this.getAsNumber().longValue() : Long.parseLong(this.getAsString());
   }

   @Override
   public short getAsShort() {
      return this.isNumber() ? this.getAsNumber().shortValue() : Short.parseShort(this.getAsString());
   }

   @Override
   public int getAsInt() {
      return this.isNumber() ? this.getAsNumber().intValue() : Integer.parseInt(this.getAsString());
   }

   @Override
   public byte getAsByte() {
      return this.isNumber() ? this.getAsNumber().byteValue() : Byte.parseByte(this.getAsString());
   }

   @Override
   public char getAsCharacter() {
      return this.getAsString().charAt(0);
   }

   private static boolean isPrimitiveOrString(Object target) {
      if (target instanceof String) {
         return true;
      } else {
         Class<?> classOfPrimitive = target.getClass();

         for (Class<?> standardPrimitive : PRIMITIVE_TYPES) {
            if (standardPrimitive.isAssignableFrom(classOfPrimitive)) {
               return true;
            }
         }

         return false;
      }
   }

   @Override
   public int hashCode() {
      if (this.value == null) {
         return 31;
      } else if (isIntegral(this)) {
         long value = this.getAsNumber().longValue();
         return (int)(value ^ value >>> 32);
      } else if (this.value instanceof Number) {
         long value = Double.doubleToLongBits(this.getAsNumber().doubleValue());
         return (int)(value ^ value >>> 32);
      } else {
         return this.value.hashCode();
      }
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         JsonPrimitive other = (JsonPrimitive)obj;
         if (this.value == null) {
            return other.value == null;
         } else if (isIntegral(this) && isIntegral(other)) {
            return this.getAsNumber().longValue() == other.getAsNumber().longValue();
         } else if (this.value instanceof Number && other.value instanceof Number) {
            double a = this.getAsNumber().doubleValue();
            double b = other.getAsNumber().doubleValue();
            return a == b || Double.isNaN(a) && Double.isNaN(b);
         } else {
            return this.value.equals(other.value);
         }
      } else {
         return false;
      }
   }

   private static boolean isIntegral(JsonPrimitive primitive) {
      if (!(primitive.value instanceof Number)) {
         return false;
      } else {
         Number number = (Number)primitive.value;
         return number instanceof BigInteger || number instanceof Long || number instanceof Integer || number instanceof Short || number instanceof Byte;
      }
   }
}
