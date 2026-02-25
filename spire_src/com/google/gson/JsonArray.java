package com.google.gson;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class JsonArray extends JsonElement implements Iterable<JsonElement> {
   private final List<JsonElement> elements = new ArrayList<>();

   JsonArray deepCopy() {
      JsonArray result = new JsonArray();

      for (JsonElement element : this.elements) {
         result.add(element.deepCopy());
      }

      return result;
   }

   public void add(Boolean bool) {
      this.elements.add((JsonElement)(bool == null ? JsonNull.INSTANCE : new JsonPrimitive(bool)));
   }

   public void add(Character character) {
      this.elements.add((JsonElement)(character == null ? JsonNull.INSTANCE : new JsonPrimitive(character)));
   }

   public void add(Number number) {
      this.elements.add((JsonElement)(number == null ? JsonNull.INSTANCE : new JsonPrimitive(number)));
   }

   public void add(String string) {
      this.elements.add((JsonElement)(string == null ? JsonNull.INSTANCE : new JsonPrimitive(string)));
   }

   public void add(JsonElement element) {
      if (element == null) {
         element = JsonNull.INSTANCE;
      }

      this.elements.add(element);
   }

   public void addAll(JsonArray array) {
      this.elements.addAll(array.elements);
   }

   public JsonElement set(int index, JsonElement element) {
      return this.elements.set(index, element);
   }

   public boolean remove(JsonElement element) {
      return this.elements.remove(element);
   }

   public JsonElement remove(int index) {
      return this.elements.remove(index);
   }

   public boolean contains(JsonElement element) {
      return this.elements.contains(element);
   }

   public int size() {
      return this.elements.size();
   }

   @Override
   public Iterator<JsonElement> iterator() {
      return this.elements.iterator();
   }

   public JsonElement get(int i) {
      return this.elements.get(i);
   }

   @Override
   public Number getAsNumber() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsNumber();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public String getAsString() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsString();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public double getAsDouble() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsDouble();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public BigDecimal getAsBigDecimal() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsBigDecimal();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public BigInteger getAsBigInteger() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsBigInteger();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public float getAsFloat() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsFloat();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public long getAsLong() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsLong();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public int getAsInt() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsInt();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public byte getAsByte() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsByte();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public char getAsCharacter() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsCharacter();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public short getAsShort() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsShort();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public boolean getAsBoolean() {
      if (this.elements.size() == 1) {
         return this.elements.get(0).getAsBoolean();
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public boolean equals(Object o) {
      return o == this || o instanceof JsonArray && ((JsonArray)o).elements.equals(this.elements);
   }

   @Override
   public int hashCode() {
      return this.elements.hashCode();
   }
}
