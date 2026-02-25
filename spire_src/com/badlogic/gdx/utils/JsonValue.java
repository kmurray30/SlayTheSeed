package com.badlogic.gdx.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class JsonValue implements Iterable<JsonValue> {
   private JsonValue.ValueType type;
   private String stringValue;
   private double doubleValue;
   private long longValue;
   public String name;
   public JsonValue child;
   public JsonValue next;
   public JsonValue prev;
   public JsonValue parent;
   public int size;

   public JsonValue(JsonValue.ValueType type) {
      this.type = type;
   }

   public JsonValue(String value) {
      this.set(value);
   }

   public JsonValue(double value) {
      this.set(value, null);
   }

   public JsonValue(long value) {
      this.set(value, null);
   }

   public JsonValue(double value, String stringValue) {
      this.set(value, stringValue);
   }

   public JsonValue(long value, String stringValue) {
      this.set(value, stringValue);
   }

   public JsonValue(boolean value) {
      this.set(value);
   }

   public JsonValue get(int index) {
      JsonValue current;
      for (current = this.child; current != null && index > 0; current = current.next) {
         index--;
      }

      return current;
   }

   public JsonValue get(String name) {
      JsonValue current = this.child;

      while (current != null && !current.name.equalsIgnoreCase(name)) {
         current = current.next;
      }

      return current;
   }

   public boolean has(String name) {
      return this.get(name) != null;
   }

   public JsonValue require(int index) {
      JsonValue current;
      for (current = this.child; current != null && index > 0; current = current.next) {
         index--;
      }

      if (current == null) {
         throw new IllegalArgumentException("Child not found with index: " + index);
      } else {
         return current;
      }
   }

   public JsonValue require(String name) {
      JsonValue current = this.child;

      while (current != null && !current.name.equalsIgnoreCase(name)) {
         current = current.next;
      }

      if (current == null) {
         throw new IllegalArgumentException("Child not found with name: " + name);
      } else {
         return current;
      }
   }

   public JsonValue remove(int index) {
      JsonValue child = this.get(index);
      if (child == null) {
         return null;
      } else {
         if (child.prev == null) {
            this.child = child.next;
            if (this.child != null) {
               this.child.prev = null;
            }
         } else {
            child.prev.next = child.next;
            if (child.next != null) {
               child.next.prev = child.prev;
            }
         }

         this.size--;
         return child;
      }
   }

   public JsonValue remove(String name) {
      JsonValue child = this.get(name);
      if (child == null) {
         return null;
      } else {
         if (child.prev == null) {
            this.child = child.next;
            if (this.child != null) {
               this.child.prev = null;
            }
         } else {
            child.prev.next = child.next;
            if (child.next != null) {
               child.next.prev = child.prev;
            }
         }

         this.size--;
         return child;
      }
   }

   @Deprecated
   public int size() {
      return this.size;
   }

   public String asString() {
      switch (this.type) {
         case stringValue:
            return this.stringValue;
         case doubleValue:
            return this.stringValue != null ? this.stringValue : Double.toString(this.doubleValue);
         case longValue:
            return this.stringValue != null ? this.stringValue : Long.toString(this.longValue);
         case booleanValue:
            return this.longValue != 0L ? "true" : "false";
         case nullValue:
            return null;
         default:
            throw new IllegalStateException("Value cannot be converted to string: " + this.type);
      }
   }

   public float asFloat() {
      switch (this.type) {
         case stringValue:
            return Float.parseFloat(this.stringValue);
         case doubleValue:
            return (float)this.doubleValue;
         case longValue:
            return (float)this.longValue;
         case booleanValue:
            return this.longValue != 0L ? 1.0F : 0.0F;
         default:
            throw new IllegalStateException("Value cannot be converted to float: " + this.type);
      }
   }

   public double asDouble() {
      switch (this.type) {
         case stringValue:
            return Double.parseDouble(this.stringValue);
         case doubleValue:
            return this.doubleValue;
         case longValue:
            return this.longValue;
         case booleanValue:
            return this.longValue != 0L ? 1.0 : 0.0;
         default:
            throw new IllegalStateException("Value cannot be converted to double: " + this.type);
      }
   }

   public long asLong() {
      switch (this.type) {
         case stringValue:
            return Long.parseLong(this.stringValue);
         case doubleValue:
            return (long)this.doubleValue;
         case longValue:
            return this.longValue;
         case booleanValue:
            return this.longValue != 0L ? 1L : 0L;
         default:
            throw new IllegalStateException("Value cannot be converted to long: " + this.type);
      }
   }

   public int asInt() {
      switch (this.type) {
         case stringValue:
            return Integer.parseInt(this.stringValue);
         case doubleValue:
            return (int)this.doubleValue;
         case longValue:
            return (int)this.longValue;
         case booleanValue:
            return this.longValue != 0L ? 1 : 0;
         default:
            throw new IllegalStateException("Value cannot be converted to int: " + this.type);
      }
   }

   public boolean asBoolean() {
      switch (this.type) {
         case stringValue:
            return this.stringValue.equalsIgnoreCase("true");
         case doubleValue:
            return this.doubleValue != 0.0;
         case longValue:
            return this.longValue != 0L;
         case booleanValue:
            return this.longValue != 0L;
         default:
            throw new IllegalStateException("Value cannot be converted to boolean: " + this.type);
      }
   }

   public byte asByte() {
      switch (this.type) {
         case stringValue:
            return Byte.parseByte(this.stringValue);
         case doubleValue:
            return (byte)this.doubleValue;
         case longValue:
            return (byte)this.longValue;
         case booleanValue:
            return (byte)(this.longValue != 0L ? 1 : 0);
         default:
            throw new IllegalStateException("Value cannot be converted to byte: " + this.type);
      }
   }

   public short asShort() {
      switch (this.type) {
         case stringValue:
            return Short.parseShort(this.stringValue);
         case doubleValue:
            return (short)this.doubleValue;
         case longValue:
            return (short)this.longValue;
         case booleanValue:
            return (short)(this.longValue != 0L ? 1 : 0);
         default:
            throw new IllegalStateException("Value cannot be converted to short: " + this.type);
      }
   }

   public char asChar() {
      switch (this.type) {
         case stringValue:
            return this.stringValue.length() == 0 ? '\u0000' : this.stringValue.charAt(0);
         case doubleValue:
            return (char)this.doubleValue;
         case longValue:
            return (char)this.longValue;
         case booleanValue:
            return (char)(this.longValue != 0L ? '\u0001' : '\u0000');
         default:
            throw new IllegalStateException("Value cannot be converted to char: " + this.type);
      }
   }

   public String[] asStringArray() {
      if (this.type != JsonValue.ValueType.array) {
         throw new IllegalStateException("Value is not an array: " + this.type);
      } else {
         String[] array = new String[this.size];
         int i = 0;

         for (JsonValue value = this.child; value != null; i++) {
            String v;
            switch (value.type) {
               case stringValue:
                  v = value.stringValue;
                  break;
               case doubleValue:
                  v = this.stringValue != null ? this.stringValue : Double.toString(value.doubleValue);
                  break;
               case longValue:
                  v = this.stringValue != null ? this.stringValue : Long.toString(value.longValue);
                  break;
               case booleanValue:
                  v = value.longValue != 0L ? "true" : "false";
                  break;
               case nullValue:
                  v = null;
                  break;
               default:
                  throw new IllegalStateException("Value cannot be converted to string: " + value.type);
            }

            array[i] = v;
            value = value.next;
         }

         return array;
      }
   }

   public float[] asFloatArray() {
      if (this.type != JsonValue.ValueType.array) {
         throw new IllegalStateException("Value is not an array: " + this.type);
      } else {
         float[] array = new float[this.size];
         int i = 0;

         for (JsonValue value = this.child; value != null; i++) {
            float v;
            switch (value.type) {
               case stringValue:
                  v = Float.parseFloat(value.stringValue);
                  break;
               case doubleValue:
                  v = (float)value.doubleValue;
                  break;
               case longValue:
                  v = (float)value.longValue;
                  break;
               case booleanValue:
                  v = value.longValue != 0L ? 1.0F : 0.0F;
                  break;
               default:
                  throw new IllegalStateException("Value cannot be converted to float: " + value.type);
            }

            array[i] = v;
            value = value.next;
         }

         return array;
      }
   }

   public double[] asDoubleArray() {
      if (this.type != JsonValue.ValueType.array) {
         throw new IllegalStateException("Value is not an array: " + this.type);
      } else {
         double[] array = new double[this.size];
         int i = 0;

         for (JsonValue value = this.child; value != null; i++) {
            double v;
            switch (value.type) {
               case stringValue:
                  v = Double.parseDouble(value.stringValue);
                  break;
               case doubleValue:
                  v = value.doubleValue;
                  break;
               case longValue:
                  v = value.longValue;
                  break;
               case booleanValue:
                  v = value.longValue != 0L ? 1.0 : 0.0;
                  break;
               default:
                  throw new IllegalStateException("Value cannot be converted to double: " + value.type);
            }

            array[i] = v;
            value = value.next;
         }

         return array;
      }
   }

   public long[] asLongArray() {
      if (this.type != JsonValue.ValueType.array) {
         throw new IllegalStateException("Value is not an array: " + this.type);
      } else {
         long[] array = new long[this.size];
         int i = 0;

         for (JsonValue value = this.child; value != null; i++) {
            long v;
            switch (value.type) {
               case stringValue:
                  v = Long.parseLong(value.stringValue);
                  break;
               case doubleValue:
                  v = (long)value.doubleValue;
                  break;
               case longValue:
                  v = value.longValue;
                  break;
               case booleanValue:
                  v = value.longValue != 0L ? 1L : 0L;
                  break;
               default:
                  throw new IllegalStateException("Value cannot be converted to long: " + value.type);
            }

            array[i] = v;
            value = value.next;
         }

         return array;
      }
   }

   public int[] asIntArray() {
      if (this.type != JsonValue.ValueType.array) {
         throw new IllegalStateException("Value is not an array: " + this.type);
      } else {
         int[] array = new int[this.size];
         int i = 0;

         for (JsonValue value = this.child; value != null; i++) {
            int v;
            switch (value.type) {
               case stringValue:
                  v = Integer.parseInt(value.stringValue);
                  break;
               case doubleValue:
                  v = (int)value.doubleValue;
                  break;
               case longValue:
                  v = (int)value.longValue;
                  break;
               case booleanValue:
                  v = value.longValue != 0L ? 1 : 0;
                  break;
               default:
                  throw new IllegalStateException("Value cannot be converted to int: " + value.type);
            }

            array[i] = v;
            value = value.next;
         }

         return array;
      }
   }

   public boolean[] asBooleanArray() {
      if (this.type != JsonValue.ValueType.array) {
         throw new IllegalStateException("Value is not an array: " + this.type);
      } else {
         boolean[] array = new boolean[this.size];
         int i = 0;

         for (JsonValue value = this.child; value != null; i++) {
            boolean v;
            switch (value.type) {
               case stringValue:
                  v = Boolean.parseBoolean(value.stringValue);
                  break;
               case doubleValue:
                  v = value.doubleValue == 0.0;
                  break;
               case longValue:
                  v = value.longValue == 0L;
                  break;
               case booleanValue:
                  v = value.longValue != 0L;
                  break;
               default:
                  throw new IllegalStateException("Value cannot be converted to boolean: " + value.type);
            }

            array[i] = v;
            value = value.next;
         }

         return array;
      }
   }

   public byte[] asByteArray() {
      if (this.type != JsonValue.ValueType.array) {
         throw new IllegalStateException("Value is not an array: " + this.type);
      } else {
         byte[] array = new byte[this.size];
         int i = 0;

         for (JsonValue value = this.child; value != null; i++) {
            byte v;
            switch (value.type) {
               case stringValue:
                  v = Byte.parseByte(value.stringValue);
                  break;
               case doubleValue:
                  v = (byte)value.doubleValue;
                  break;
               case longValue:
                  v = (byte)value.longValue;
                  break;
               case booleanValue:
                  v = (byte)(value.longValue != 0L ? 1 : 0);
                  break;
               default:
                  throw new IllegalStateException("Value cannot be converted to byte: " + value.type);
            }

            array[i] = v;
            value = value.next;
         }

         return array;
      }
   }

   public short[] asShortArray() {
      if (this.type != JsonValue.ValueType.array) {
         throw new IllegalStateException("Value is not an array: " + this.type);
      } else {
         short[] array = new short[this.size];
         int i = 0;

         for (JsonValue value = this.child; value != null; i++) {
            short v;
            switch (value.type) {
               case stringValue:
                  v = Short.parseShort(value.stringValue);
                  break;
               case doubleValue:
                  v = (short)value.doubleValue;
                  break;
               case longValue:
                  v = (short)value.longValue;
                  break;
               case booleanValue:
                  v = (short)(value.longValue != 0L ? 1 : 0);
                  break;
               default:
                  throw new IllegalStateException("Value cannot be converted to short: " + value.type);
            }

            array[i] = v;
            value = value.next;
         }

         return array;
      }
   }

   public char[] asCharArray() {
      if (this.type != JsonValue.ValueType.array) {
         throw new IllegalStateException("Value is not an array: " + this.type);
      } else {
         char[] array = new char[this.size];
         int i = 0;

         for (JsonValue value = this.child; value != null; i++) {
            char v;
            switch (value.type) {
               case stringValue:
                  v = value.stringValue.length() == 0 ? 0 : value.stringValue.charAt(0);
                  break;
               case doubleValue:
                  v = (char)value.doubleValue;
                  break;
               case longValue:
                  v = (char)value.longValue;
                  break;
               case booleanValue:
                  v = (char)(value.longValue != 0L ? 1 : 0);
                  break;
               default:
                  throw new IllegalStateException("Value cannot be converted to char: " + value.type);
            }

            array[i] = v;
            value = value.next;
         }

         return array;
      }
   }

   public boolean hasChild(String name) {
      return this.getChild(name) != null;
   }

   public JsonValue getChild(String name) {
      JsonValue child = this.get(name);
      return child == null ? null : child.child;
   }

   public String getString(String name, String defaultValue) {
      JsonValue child = this.get(name);
      return child != null && child.isValue() && !child.isNull() ? child.asString() : defaultValue;
   }

   public float getFloat(String name, float defaultValue) {
      JsonValue child = this.get(name);
      return child != null && child.isValue() ? child.asFloat() : defaultValue;
   }

   public double getDouble(String name, double defaultValue) {
      JsonValue child = this.get(name);
      return child != null && child.isValue() ? child.asDouble() : defaultValue;
   }

   public long getLong(String name, long defaultValue) {
      JsonValue child = this.get(name);
      return child != null && child.isValue() ? child.asLong() : defaultValue;
   }

   public int getInt(String name, int defaultValue) {
      JsonValue child = this.get(name);
      return child != null && child.isValue() ? child.asInt() : defaultValue;
   }

   public boolean getBoolean(String name, boolean defaultValue) {
      JsonValue child = this.get(name);
      return child != null && child.isValue() ? child.asBoolean() : defaultValue;
   }

   public byte getByte(String name, byte defaultValue) {
      JsonValue child = this.get(name);
      return child != null && child.isValue() ? child.asByte() : defaultValue;
   }

   public short getShort(String name, short defaultValue) {
      JsonValue child = this.get(name);
      return child != null && child.isValue() ? child.asShort() : defaultValue;
   }

   public char getChar(String name, char defaultValue) {
      JsonValue child = this.get(name);
      return child != null && child.isValue() ? child.asChar() : defaultValue;
   }

   public String getString(String name) {
      JsonValue child = this.get(name);
      if (child == null) {
         throw new IllegalArgumentException("Named value not found: " + name);
      } else {
         return child.asString();
      }
   }

   public float getFloat(String name) {
      JsonValue child = this.get(name);
      if (child == null) {
         throw new IllegalArgumentException("Named value not found: " + name);
      } else {
         return child.asFloat();
      }
   }

   public double getDouble(String name) {
      JsonValue child = this.get(name);
      if (child == null) {
         throw new IllegalArgumentException("Named value not found: " + name);
      } else {
         return child.asDouble();
      }
   }

   public long getLong(String name) {
      JsonValue child = this.get(name);
      if (child == null) {
         throw new IllegalArgumentException("Named value not found: " + name);
      } else {
         return child.asLong();
      }
   }

   public int getInt(String name) {
      JsonValue child = this.get(name);
      if (child == null) {
         throw new IllegalArgumentException("Named value not found: " + name);
      } else {
         return child.asInt();
      }
   }

   public boolean getBoolean(String name) {
      JsonValue child = this.get(name);
      if (child == null) {
         throw new IllegalArgumentException("Named value not found: " + name);
      } else {
         return child.asBoolean();
      }
   }

   public byte getByte(String name) {
      JsonValue child = this.get(name);
      if (child == null) {
         throw new IllegalArgumentException("Named value not found: " + name);
      } else {
         return child.asByte();
      }
   }

   public short getShort(String name) {
      JsonValue child = this.get(name);
      if (child == null) {
         throw new IllegalArgumentException("Named value not found: " + name);
      } else {
         return child.asShort();
      }
   }

   public char getChar(String name) {
      JsonValue child = this.get(name);
      if (child == null) {
         throw new IllegalArgumentException("Named value not found: " + name);
      } else {
         return child.asChar();
      }
   }

   public String getString(int index) {
      JsonValue child = this.get(index);
      if (child == null) {
         throw new IllegalArgumentException("Indexed value not found: " + this.name);
      } else {
         return child.asString();
      }
   }

   public float getFloat(int index) {
      JsonValue child = this.get(index);
      if (child == null) {
         throw new IllegalArgumentException("Indexed value not found: " + this.name);
      } else {
         return child.asFloat();
      }
   }

   public double getDouble(int index) {
      JsonValue child = this.get(index);
      if (child == null) {
         throw new IllegalArgumentException("Indexed value not found: " + this.name);
      } else {
         return child.asDouble();
      }
   }

   public long getLong(int index) {
      JsonValue child = this.get(index);
      if (child == null) {
         throw new IllegalArgumentException("Indexed value not found: " + this.name);
      } else {
         return child.asLong();
      }
   }

   public int getInt(int index) {
      JsonValue child = this.get(index);
      if (child == null) {
         throw new IllegalArgumentException("Indexed value not found: " + this.name);
      } else {
         return child.asInt();
      }
   }

   public boolean getBoolean(int index) {
      JsonValue child = this.get(index);
      if (child == null) {
         throw new IllegalArgumentException("Indexed value not found: " + this.name);
      } else {
         return child.asBoolean();
      }
   }

   public byte getByte(int index) {
      JsonValue child = this.get(index);
      if (child == null) {
         throw new IllegalArgumentException("Indexed value not found: " + this.name);
      } else {
         return child.asByte();
      }
   }

   public short getShort(int index) {
      JsonValue child = this.get(index);
      if (child == null) {
         throw new IllegalArgumentException("Indexed value not found: " + this.name);
      } else {
         return child.asShort();
      }
   }

   public char getChar(int index) {
      JsonValue child = this.get(index);
      if (child == null) {
         throw new IllegalArgumentException("Indexed value not found: " + this.name);
      } else {
         return child.asChar();
      }
   }

   public JsonValue.ValueType type() {
      return this.type;
   }

   public void setType(JsonValue.ValueType type) {
      if (type == null) {
         throw new IllegalArgumentException("type cannot be null.");
      } else {
         this.type = type;
      }
   }

   public boolean isArray() {
      return this.type == JsonValue.ValueType.array;
   }

   public boolean isObject() {
      return this.type == JsonValue.ValueType.object;
   }

   public boolean isString() {
      return this.type == JsonValue.ValueType.stringValue;
   }

   public boolean isNumber() {
      return this.type == JsonValue.ValueType.doubleValue || this.type == JsonValue.ValueType.longValue;
   }

   public boolean isDouble() {
      return this.type == JsonValue.ValueType.doubleValue;
   }

   public boolean isLong() {
      return this.type == JsonValue.ValueType.longValue;
   }

   public boolean isBoolean() {
      return this.type == JsonValue.ValueType.booleanValue;
   }

   public boolean isNull() {
      return this.type == JsonValue.ValueType.nullValue;
   }

   public boolean isValue() {
      switch (this.type) {
         case stringValue:
         case doubleValue:
         case longValue:
         case booleanValue:
         case nullValue:
            return true;
         default:
            return false;
      }
   }

   public String name() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public JsonValue parent() {
      return this.parent;
   }

   public JsonValue child() {
      return this.child;
   }

   public void addChild(String name, JsonValue value) {
      value.name = name;
      this.addChild(value);
   }

   public void addChild(JsonValue value) {
      value.parent = this;
      JsonValue current = this.child;
      if (current == null) {
         this.child = value;
      } else {
         while (current.next != null) {
            current = current.next;
         }

         current.next = value;
      }
   }

   public JsonValue next() {
      return this.next;
   }

   public void setNext(JsonValue next) {
      this.next = next;
   }

   public JsonValue prev() {
      return this.prev;
   }

   public void setPrev(JsonValue prev) {
      this.prev = prev;
   }

   public void set(String value) {
      this.stringValue = value;
      this.type = value == null ? JsonValue.ValueType.nullValue : JsonValue.ValueType.stringValue;
   }

   public void set(double value, String stringValue) {
      this.doubleValue = value;
      this.longValue = (long)value;
      this.stringValue = stringValue;
      this.type = JsonValue.ValueType.doubleValue;
   }

   public void set(long value, String stringValue) {
      this.longValue = value;
      this.doubleValue = value;
      this.stringValue = stringValue;
      this.type = JsonValue.ValueType.longValue;
   }

   public void set(boolean value) {
      this.longValue = value ? 1L : 0L;
      this.type = JsonValue.ValueType.booleanValue;
   }

   public String toJson(JsonWriter.OutputType outputType) {
      if (this.isValue()) {
         return this.asString();
      } else {
         StringBuilder buffer = new StringBuilder(512);
         this.json(this, buffer, outputType);
         return buffer.toString();
      }
   }

   private void json(JsonValue object, StringBuilder buffer, JsonWriter.OutputType outputType) {
      if (object.isObject()) {
         if (object.child == null) {
            buffer.append("{}");
         } else {
            int start = buffer.length();
            buffer.append('{');
            int i = 0;

            for (JsonValue child = object.child; child != null; child = child.next) {
               buffer.append(outputType.quoteName(child.name));
               buffer.append(':');
               this.json(child, buffer, outputType);
               if (child.next != null) {
                  buffer.append(',');
               }
            }

            buffer.append('}');
         }
      } else if (object.isArray()) {
         if (object.child == null) {
            buffer.append("[]");
         } else {
            int start = buffer.length();
            buffer.append('[');

            for (JsonValue childx = object.child; childx != null; childx = childx.next) {
               this.json(childx, buffer, outputType);
               if (childx.next != null) {
                  buffer.append(',');
               }
            }

            buffer.append(']');
         }
      } else if (object.isString()) {
         buffer.append(outputType.quoteValue(object.asString()));
      } else if (object.isDouble()) {
         double doubleValue = object.asDouble();
         long longValue = object.asLong();
         buffer.append(doubleValue == longValue ? longValue : doubleValue);
      } else if (object.isLong()) {
         buffer.append(object.asLong());
      } else if (object.isBoolean()) {
         buffer.append(object.asBoolean());
      } else {
         if (!object.isNull()) {
            throw new SerializationException("Unknown object type: " + object);
         }

         buffer.append("null");
      }
   }

   @Override
   public String toString() {
      if (this.isValue()) {
         return this.name == null ? this.asString() : this.name + ": " + this.asString();
      } else {
         return (this.name == null ? "" : this.name + ": ") + this.prettyPrint(JsonWriter.OutputType.minimal, 0);
      }
   }

   public String prettyPrint(JsonWriter.OutputType outputType, int singleLineColumns) {
      JsonValue.PrettyPrintSettings settings = new JsonValue.PrettyPrintSettings();
      settings.outputType = outputType;
      settings.singleLineColumns = singleLineColumns;
      return this.prettyPrint(settings);
   }

   public String prettyPrint(JsonValue.PrettyPrintSettings settings) {
      StringBuilder buffer = new StringBuilder(512);
      this.prettyPrint(this, buffer, 0, settings);
      return buffer.toString();
   }

   private void prettyPrint(JsonValue object, StringBuilder buffer, int indent, JsonValue.PrettyPrintSettings settings) {
      JsonWriter.OutputType outputType = settings.outputType;
      if (object.isObject()) {
         if (object.child == null) {
            buffer.append("{}");
         } else {
            boolean newLines = !isFlat(object);
            int start = buffer.length();

            label125:
            while (true) {
               buffer.append(newLines ? "{\n" : "{ ");
               int i = 0;

               for (JsonValue child = object.child; child != null; child = child.next) {
                  if (newLines) {
                     indent(indent, buffer);
                  }

                  buffer.append(outputType.quoteName(child.name));
                  buffer.append(": ");
                  this.prettyPrint(child, buffer, indent + 1, settings);
                  if ((!newLines || outputType != JsonWriter.OutputType.minimal) && child.next != null) {
                     buffer.append(',');
                  }

                  buffer.append((char)(newLines ? '\n' : ' '));
                  if (!newLines && buffer.length() - start > settings.singleLineColumns) {
                     buffer.setLength(start);
                     newLines = true;
                     continue label125;
                  }
               }

               if (newLines) {
                  indent(indent - 1, buffer);
               }

               buffer.append('}');
               break;
            }
         }
      } else if (object.isArray()) {
         if (object.child == null) {
            buffer.append("[]");
         } else {
            boolean newLines = !isFlat(object);
            boolean wrap = settings.wrapNumericArrays || !isNumeric(object);
            int start = buffer.length();

            label148:
            while (true) {
               buffer.append(newLines ? "[\n" : "[ ");

               for (JsonValue child = object.child; child != null; child = child.next) {
                  if (newLines) {
                     indent(indent, buffer);
                  }

                  this.prettyPrint(child, buffer, indent + 1, settings);
                  if ((!newLines || outputType != JsonWriter.OutputType.minimal) && child.next != null) {
                     buffer.append(',');
                  }

                  buffer.append((char)(newLines ? '\n' : ' '));
                  if (wrap && !newLines && buffer.length() - start > settings.singleLineColumns) {
                     buffer.setLength(start);
                     newLines = true;
                     continue label148;
                  }
               }

               if (newLines) {
                  indent(indent - 1, buffer);
               }

               buffer.append(']');
               break;
            }
         }
      } else if (object.isString()) {
         buffer.append(outputType.quoteValue(object.asString()));
      } else if (object.isDouble()) {
         double doubleValue = object.asDouble();
         long longValue = object.asLong();
         buffer.append(doubleValue == longValue ? longValue : doubleValue);
      } else if (object.isLong()) {
         buffer.append(object.asLong());
      } else if (object.isBoolean()) {
         buffer.append(object.asBoolean());
      } else {
         if (!object.isNull()) {
            throw new SerializationException("Unknown object type: " + object);
         }

         buffer.append("null");
      }
   }

   private static boolean isFlat(JsonValue object) {
      for (JsonValue child = object.child; child != null; child = child.next) {
         if (child.isObject() || child.isArray()) {
            return false;
         }
      }

      return true;
   }

   private static boolean isNumeric(JsonValue object) {
      for (JsonValue child = object.child; child != null; child = child.next) {
         if (!child.isNumber()) {
            return false;
         }
      }

      return true;
   }

   private static void indent(int count, StringBuilder buffer) {
      for (int i = 0; i < count; i++) {
         buffer.append('\t');
      }
   }

   public JsonValue.JsonIterator iterator() {
      return new JsonValue.JsonIterator();
   }

   public String trace() {
      if (this.parent == null) {
         if (this.type == JsonValue.ValueType.array) {
            return "[]";
         } else {
            return this.type == JsonValue.ValueType.object ? "{}" : "";
         }
      } else {
         String trace;
         if (this.parent.type == JsonValue.ValueType.array) {
            trace = "[]";
            int i = 0;

            for (JsonValue child = this.parent.child; child != null; i++) {
               if (child == this) {
                  trace = "[" + i + "]";
                  break;
               }

               child = child.next;
            }
         } else if (this.name.indexOf(46) != -1) {
            trace = ".\"" + this.name.replace("\"", "\\\"") + "\"";
         } else {
            trace = '.' + this.name;
         }

         return this.parent.trace() + trace;
      }
   }

   public class JsonIterator implements Iterator<JsonValue>, Iterable<JsonValue> {
      JsonValue entry = JsonValue.this.child;
      JsonValue current;

      @Override
      public boolean hasNext() {
         return this.entry != null;
      }

      public JsonValue next() {
         this.current = this.entry;
         if (this.current == null) {
            throw new NoSuchElementException();
         } else {
            this.entry = this.current.next;
            return this.current;
         }
      }

      @Override
      public void remove() {
         if (this.current.prev == null) {
            JsonValue.this.child = this.current.next;
            if (JsonValue.this.child != null) {
               JsonValue.this.child.prev = null;
            }
         } else {
            this.current.prev.next = this.current.next;
            if (this.current.next != null) {
               this.current.next.prev = this.current.prev;
            }
         }

         JsonValue.this.size--;
      }

      @Override
      public Iterator<JsonValue> iterator() {
         return this;
      }
   }

   public static class PrettyPrintSettings {
      public JsonWriter.OutputType outputType;
      public int singleLineColumns;
      public boolean wrapNumericArrays;
   }

   public static enum ValueType {
      object,
      array,
      stringValue,
      doubleValue,
      longValue,
      booleanValue,
      nullValue;
   }
}
