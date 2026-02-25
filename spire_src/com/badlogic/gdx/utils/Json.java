package com.badlogic.gdx.utils;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.reflect.ArrayReflection;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Constructor;
import com.badlogic.gdx.utils.reflect.Field;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class Json {
   private static final boolean debug = false;
   private JsonWriter writer;
   private String typeName = "class";
   private boolean usePrototypes = true;
   private JsonWriter.OutputType outputType;
   private boolean quoteLongValues;
   private boolean ignoreUnknownFields;
   private boolean ignoreDeprecated;
   private boolean enumNames = true;
   private Json.Serializer defaultSerializer;
   private final ObjectMap<Class, OrderedMap<String, Json.FieldMetadata>> typeToFields = new ObjectMap<>();
   private final ObjectMap<String, Class> tagToClass = new ObjectMap<>();
   private final ObjectMap<Class, String> classToTag = new ObjectMap<>();
   private final ObjectMap<Class, Json.Serializer> classToSerializer = new ObjectMap<>();
   private final ObjectMap<Class, Object[]> classToDefaultValues = new ObjectMap<>();
   private final Object[] equals1 = new Object[]{null};
   private final Object[] equals2 = new Object[]{null};

   public Json() {
      this.outputType = JsonWriter.OutputType.minimal;
   }

   public Json(JsonWriter.OutputType outputType) {
      this.outputType = outputType;
   }

   public void setIgnoreUnknownFields(boolean ignoreUnknownFields) {
      this.ignoreUnknownFields = ignoreUnknownFields;
   }

   public void setIgnoreDeprecated(boolean ignoreDeprecated) {
      this.ignoreDeprecated = ignoreDeprecated;
   }

   public void setOutputType(JsonWriter.OutputType outputType) {
      this.outputType = outputType;
   }

   public void setQuoteLongValues(boolean quoteLongValues) {
      this.quoteLongValues = quoteLongValues;
   }

   public void setEnumNames(boolean enumNames) {
      this.enumNames = enumNames;
   }

   public void addClassTag(String tag, Class type) {
      this.tagToClass.put(tag, type);
      this.classToTag.put(type, tag);
   }

   public Class getClass(String tag) {
      return this.tagToClass.get(tag);
   }

   public String getTag(Class type) {
      return this.classToTag.get(type);
   }

   public void setTypeName(String typeName) {
      this.typeName = typeName;
   }

   public void setDefaultSerializer(Json.Serializer defaultSerializer) {
      this.defaultSerializer = defaultSerializer;
   }

   public <T> void setSerializer(Class<T> type, Json.Serializer<T> serializer) {
      this.classToSerializer.put(type, serializer);
   }

   public <T> Json.Serializer<T> getSerializer(Class<T> type) {
      return this.classToSerializer.get(type);
   }

   public void setUsePrototypes(boolean usePrototypes) {
      this.usePrototypes = usePrototypes;
   }

   public void setElementType(Class type, String fieldName, Class elementType) {
      ObjectMap<String, Json.FieldMetadata> fields = this.getFields(type);
      Json.FieldMetadata metadata = fields.get(fieldName);
      if (metadata == null) {
         throw new SerializationException("Field not found: " + fieldName + " (" + type.getName() + ")");
      } else {
         metadata.elementType = elementType;
      }
   }

   private OrderedMap<String, Json.FieldMetadata> getFields(Class type) {
      OrderedMap<String, Json.FieldMetadata> fields = this.typeToFields.get(type);
      if (fields != null) {
         return fields;
      } else {
         Array<Class> classHierarchy = new Array<>();

         for (Class nextClass = type; nextClass != Object.class; nextClass = nextClass.getSuperclass()) {
            classHierarchy.add(nextClass);
         }

         ArrayList<Field> allFields = new ArrayList<>();

         for (int i = classHierarchy.size - 1; i >= 0; i--) {
            Collections.addAll(allFields, ClassReflection.getDeclaredFields(classHierarchy.get(i)));
         }

         OrderedMap<String, Json.FieldMetadata> nameToField = new OrderedMap<>(allFields.size());
         int i = 0;

         for (int n = allFields.size(); i < n; i++) {
            Field field = allFields.get(i);
            if (!field.isTransient() && !field.isStatic() && !field.isSynthetic()) {
               if (!field.isAccessible()) {
                  try {
                     field.setAccessible(true);
                  } catch (AccessControlException var11) {
                     continue;
                  }
               }

               if (!this.ignoreDeprecated || !field.isAnnotationPresent(Deprecated.class)) {
                  nameToField.put(field.getName(), new Json.FieldMetadata(field));
               }
            }
         }

         this.typeToFields.put(type, nameToField);
         return nameToField;
      }
   }

   public String toJson(Object object) {
      return this.toJson(object, object == null ? null : object.getClass(), (Class)null);
   }

   public String toJson(Object object, Class knownType) {
      return this.toJson(object, knownType, (Class)null);
   }

   public String toJson(Object object, Class knownType, Class elementType) {
      StringWriter buffer = new StringWriter();
      this.toJson(object, knownType, elementType, buffer);
      return buffer.toString();
   }

   public void toJson(Object object, FileHandle file) {
      this.toJson(object, object == null ? null : object.getClass(), null, file);
   }

   public void toJson(Object object, Class knownType, FileHandle file) {
      this.toJson(object, knownType, null, file);
   }

   public void toJson(Object object, Class knownType, Class elementType, FileHandle file) {
      Writer writer = null;

      try {
         writer = file.writer(false, "UTF-8");
         this.toJson(object, knownType, elementType, writer);
      } catch (Exception var10) {
         throw new SerializationException("Error writing file: " + file, var10);
      } finally {
         StreamUtils.closeQuietly(writer);
      }
   }

   public void toJson(Object object, Writer writer) {
      this.toJson(object, object == null ? null : object.getClass(), null, writer);
   }

   public void toJson(Object object, Class knownType, Writer writer) {
      this.toJson(object, knownType, null, writer);
   }

   public void toJson(Object object, Class knownType, Class elementType, Writer writer) {
      this.setWriter(writer);

      try {
         this.writeValue(object, knownType, elementType);
      } finally {
         StreamUtils.closeQuietly(this.writer);
         this.writer = null;
      }
   }

   public void setWriter(Writer writer) {
      if (!(writer instanceof JsonWriter)) {
         writer = new JsonWriter(writer);
      }

      this.writer = (JsonWriter)writer;
      this.writer.setOutputType(this.outputType);
      this.writer.setQuoteLongValues(this.quoteLongValues);
   }

   public JsonWriter getWriter() {
      return this.writer;
   }

   public void writeFields(Object object) {
      Class type = object.getClass();
      Object[] defaultValues = this.getDefaultValues(type);
      OrderedMap<String, Json.FieldMetadata> fields = this.getFields(type);
      int i = 0;

      for (Json.FieldMetadata metadata : new OrderedMap.OrderedMapValues<>(fields)) {
         Field field = metadata.field;

         try {
            Object value = field.get(object);
            if (defaultValues != null) {
               Object defaultValue = defaultValues[i++];
               if (value == null && defaultValue == null) {
                  continue;
               }

               if (value != null && defaultValue != null) {
                  if (value.equals(defaultValue)) {
                     continue;
                  }

                  if (value.getClass().isArray() && defaultValue.getClass().isArray()) {
                     this.equals1[0] = value;
                     this.equals2[0] = defaultValue;
                     if (Arrays.deepEquals(this.equals1, this.equals2)) {
                        continue;
                     }
                  }
               }
            }

            this.writer.name(field.getName());
            this.writeValue(value, field.getType(), metadata.elementType);
         } catch (ReflectionException var11) {
            throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", var11);
         } catch (SerializationException var12) {
            var12.addTrace(field + " (" + type.getName() + ")");
            throw var12;
         } catch (Exception var13) {
            SerializationException ex = new SerializationException(var13);
            ex.addTrace(field + " (" + type.getName() + ")");
            throw ex;
         }
      }
   }

   private Object[] getDefaultValues(Class type) {
      if (!this.usePrototypes) {
         return null;
      } else if (this.classToDefaultValues.containsKey(type)) {
         return this.classToDefaultValues.get(type);
      } else {
         Object object;
         try {
            object = this.newInstance(type);
         } catch (Exception var14) {
            this.classToDefaultValues.put(type, null);
            return null;
         }

         ObjectMap<String, Json.FieldMetadata> fields = this.getFields(type);
         Object[] values = new Object[fields.size];
         this.classToDefaultValues.put(type, values);
         int i = 0;

         for (Json.FieldMetadata metadata : fields.values()) {
            Field field = metadata.field;

            try {
               values[i++] = field.get(object);
            } catch (ReflectionException var11) {
               throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", var11);
            } catch (SerializationException var12) {
               var12.addTrace(field + " (" + type.getName() + ")");
               throw var12;
            } catch (RuntimeException var13) {
               SerializationException ex = new SerializationException(var13);
               ex.addTrace(field + " (" + type.getName() + ")");
               throw ex;
            }
         }

         return values;
      }
   }

   public void writeField(Object object, String name) {
      this.writeField(object, name, name, null);
   }

   public void writeField(Object object, String name, Class elementType) {
      this.writeField(object, name, name, elementType);
   }

   public void writeField(Object object, String fieldName, String jsonName) {
      this.writeField(object, fieldName, jsonName, null);
   }

   public void writeField(Object object, String fieldName, String jsonName, Class elementType) {
      Class type = object.getClass();
      ObjectMap<String, Json.FieldMetadata> fields = this.getFields(type);
      Json.FieldMetadata metadata = fields.get(fieldName);
      if (metadata == null) {
         throw new SerializationException("Field not found: " + fieldName + " (" + type.getName() + ")");
      } else {
         Field field = metadata.field;
         if (elementType == null) {
            elementType = metadata.elementType;
         }

         try {
            this.writer.name(jsonName);
            this.writeValue(field.get(object), field.getType(), elementType);
         } catch (ReflectionException var11) {
            throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", var11);
         } catch (SerializationException var12) {
            var12.addTrace(field + " (" + type.getName() + ")");
            throw var12;
         } catch (Exception var13) {
            SerializationException ex = new SerializationException(var13);
            ex.addTrace(field + " (" + type.getName() + ")");
            throw ex;
         }
      }
   }

   public void writeValue(String name, Object value) {
      try {
         this.writer.name(name);
      } catch (IOException var4) {
         throw new SerializationException(var4);
      }

      if (value == null) {
         this.writeValue(value, null, null);
      } else {
         this.writeValue(value, value.getClass(), null);
      }
   }

   public void writeValue(String name, Object value, Class knownType) {
      try {
         this.writer.name(name);
      } catch (IOException var5) {
         throw new SerializationException(var5);
      }

      this.writeValue(value, knownType, null);
   }

   public void writeValue(String name, Object value, Class knownType, Class elementType) {
      try {
         this.writer.name(name);
      } catch (IOException var6) {
         throw new SerializationException(var6);
      }

      this.writeValue(value, knownType, elementType);
   }

   public void writeValue(Object value) {
      if (value == null) {
         this.writeValue(value, null, null);
      } else {
         this.writeValue(value, value.getClass(), null);
      }
   }

   public void writeValue(Object value, Class knownType) {
      this.writeValue(value, knownType, null);
   }

   public void writeValue(Object value, Class knownType, Class elementType) {
      try {
         if (value == null) {
            this.writer.value(null);
         } else if ((knownType == null || !knownType.isPrimitive())
            && knownType != String.class
            && knownType != Integer.class
            && knownType != Boolean.class
            && knownType != Float.class
            && knownType != Long.class
            && knownType != Double.class
            && knownType != Short.class
            && knownType != Byte.class
            && knownType != Character.class) {
            Class actualType = value.getClass();
            if (actualType.isPrimitive()
               || actualType == String.class
               || actualType == Integer.class
               || actualType == Boolean.class
               || actualType == Float.class
               || actualType == Long.class
               || actualType == Double.class
               || actualType == Short.class
               || actualType == Byte.class
               || actualType == Character.class) {
               this.writeObjectStart(actualType, null);
               this.writeValue("value", value);
               this.writeObjectEnd();
            } else if (value instanceof Json.Serializable) {
               this.writeObjectStart(actualType, knownType);
               ((Json.Serializable)value).write(this);
               this.writeObjectEnd();
            } else {
               Json.Serializer serializer = this.classToSerializer.get(actualType);
               if (serializer != null) {
                  serializer.write(this, value, knownType);
               } else if (value instanceof Array) {
                  if (knownType != null && actualType != knownType && actualType != Array.class) {
                     throw new SerializationException(
                        "Serialization of an Array other than the known type is not supported.\nKnown type: " + knownType + "\nActual type: " + actualType
                     );
                  } else {
                     this.writeArrayStart();
                     Array array = (Array)value;
                     int i = 0;

                     for (int n = array.size; i < n; i++) {
                        this.writeValue(array.get(i), elementType, null);
                     }

                     this.writeArrayEnd();
                  }
               } else if (value instanceof Queue) {
                  if (knownType != null && actualType != knownType && actualType != Queue.class) {
                     throw new SerializationException(
                        "Serialization of a Queue other than the known type is not supported.\nKnown type: " + knownType + "\nActual type: " + actualType
                     );
                  } else {
                     this.writeArrayStart();
                     Queue queue = (Queue)value;
                     int i = 0;

                     for (int n = queue.size; i < n; i++) {
                        this.writeValue(queue.get(i), elementType, null);
                     }

                     this.writeArrayEnd();
                  }
               } else if (value instanceof Collection) {
                  if (this.typeName != null && actualType != ArrayList.class && (knownType == null || knownType != actualType)) {
                     this.writeObjectStart(actualType, knownType);
                     this.writeArrayStart("items");

                     for (Object item : (Collection)value) {
                        this.writeValue(item, elementType, null);
                     }

                     this.writeArrayEnd();
                     this.writeObjectEnd();
                  } else {
                     this.writeArrayStart();

                     for (Object item : (Collection)value) {
                        this.writeValue(item, elementType, null);
                     }

                     this.writeArrayEnd();
                  }
               } else if (actualType.isArray()) {
                  if (elementType == null) {
                     elementType = actualType.getComponentType();
                  }

                  int length = ArrayReflection.getLength(value);
                  this.writeArrayStart();

                  for (int i = 0; i < length; i++) {
                     this.writeValue(ArrayReflection.get(value, i), elementType, null);
                  }

                  this.writeArrayEnd();
               } else if (value instanceof ObjectMap) {
                  if (knownType == null) {
                     knownType = ObjectMap.class;
                  }

                  this.writeObjectStart(actualType, knownType);

                  for (ObjectMap.Entry entry : ((ObjectMap)value).entries()) {
                     this.writer.name(this.convertToString(entry.key));
                     this.writeValue(entry.value, elementType, null);
                  }

                  this.writeObjectEnd();
               } else if (value instanceof ArrayMap) {
                  if (knownType == null) {
                     knownType = ArrayMap.class;
                  }

                  this.writeObjectStart(actualType, knownType);
                  ArrayMap map = (ArrayMap)value;
                  int i = 0;

                  for (int n = map.size; i < n; i++) {
                     this.writer.name(this.convertToString(map.keys[i]));
                     this.writeValue(map.values[i], elementType, null);
                  }

                  this.writeObjectEnd();
               } else if (value instanceof Map) {
                  if (knownType == null) {
                     knownType = HashMap.class;
                  }

                  this.writeObjectStart(actualType, knownType);

                  for (Entry entry : ((Map)value).entrySet()) {
                     this.writer.name(this.convertToString(entry.getKey()));
                     this.writeValue(entry.getValue(), elementType, null);
                  }

                  this.writeObjectEnd();
               } else if (!ClassReflection.isAssignableFrom(Enum.class, actualType)) {
                  this.writeObjectStart(actualType, knownType);
                  this.writeFields(value);
                  this.writeObjectEnd();
               } else {
                  if (this.typeName != null && (knownType == null || knownType != actualType)) {
                     if (actualType.getEnumConstants() == null) {
                        actualType = actualType.getSuperclass();
                     }

                     this.writeObjectStart(actualType, null);
                     this.writer.name("value");
                     this.writer.value(this.convertToString((Enum)value));
                     this.writeObjectEnd();
                  } else {
                     this.writer.value(this.convertToString((Enum)value));
                  }
               }
            }
         } else {
            this.writer.value(value);
         }
      } catch (IOException var9) {
         throw new SerializationException(var9);
      }
   }

   public void writeObjectStart(String name) {
      try {
         this.writer.name(name);
      } catch (IOException var3) {
         throw new SerializationException(var3);
      }

      this.writeObjectStart();
   }

   public void writeObjectStart(String name, Class actualType, Class knownType) {
      try {
         this.writer.name(name);
      } catch (IOException var5) {
         throw new SerializationException(var5);
      }

      this.writeObjectStart(actualType, knownType);
   }

   public void writeObjectStart() {
      try {
         this.writer.object();
      } catch (IOException var2) {
         throw new SerializationException(var2);
      }
   }

   public void writeObjectStart(Class actualType, Class knownType) {
      try {
         this.writer.object();
      } catch (IOException var4) {
         throw new SerializationException(var4);
      }

      if (knownType == null || knownType != actualType) {
         this.writeType(actualType);
      }
   }

   public void writeObjectEnd() {
      try {
         this.writer.pop();
      } catch (IOException var2) {
         throw new SerializationException(var2);
      }
   }

   public void writeArrayStart(String name) {
      try {
         this.writer.name(name);
         this.writer.array();
      } catch (IOException var3) {
         throw new SerializationException(var3);
      }
   }

   public void writeArrayStart() {
      try {
         this.writer.array();
      } catch (IOException var2) {
         throw new SerializationException(var2);
      }
   }

   public void writeArrayEnd() {
      try {
         this.writer.pop();
      } catch (IOException var2) {
         throw new SerializationException(var2);
      }
   }

   public void writeType(Class type) {
      if (this.typeName != null) {
         String className = this.getTag(type);
         if (className == null) {
            className = type.getName();
         }

         try {
            this.writer.set(this.typeName, className);
         } catch (IOException var4) {
            throw new SerializationException(var4);
         }
      }
   }

   public <T> T fromJson(Class<T> type, Reader reader) {
      return this.readValue(type, null, new JsonReader().parse(reader));
   }

   public <T> T fromJson(Class<T> type, Class elementType, Reader reader) {
      return this.readValue(type, elementType, new JsonReader().parse(reader));
   }

   public <T> T fromJson(Class<T> type, InputStream input) {
      return this.readValue(type, null, new JsonReader().parse(input));
   }

   public <T> T fromJson(Class<T> type, Class elementType, InputStream input) {
      return this.readValue(type, elementType, new JsonReader().parse(input));
   }

   public <T> T fromJson(Class<T> type, FileHandle file) {
      try {
         return this.readValue(type, null, new JsonReader().parse(file));
      } catch (Exception var4) {
         throw new SerializationException("Error reading file: " + file, var4);
      }
   }

   public <T> T fromJson(Class<T> type, Class elementType, FileHandle file) {
      try {
         return this.readValue(type, elementType, new JsonReader().parse(file));
      } catch (Exception var5) {
         throw new SerializationException("Error reading file: " + file, var5);
      }
   }

   public <T> T fromJson(Class<T> type, char[] data, int offset, int length) {
      return this.readValue(type, null, new JsonReader().parse(data, offset, length));
   }

   public <T> T fromJson(Class<T> type, Class elementType, char[] data, int offset, int length) {
      return this.readValue(type, elementType, new JsonReader().parse(data, offset, length));
   }

   public <T> T fromJson(Class<T> type, String json) {
      return this.readValue(type, null, new JsonReader().parse(json));
   }

   public <T> T fromJson(Class<T> type, Class elementType, String json) {
      return this.readValue(type, elementType, new JsonReader().parse(json));
   }

   public void readField(Object object, String name, JsonValue jsonData) {
      this.readField(object, name, name, null, jsonData);
   }

   public void readField(Object object, String name, Class elementType, JsonValue jsonData) {
      this.readField(object, name, name, elementType, jsonData);
   }

   public void readField(Object object, String fieldName, String jsonName, JsonValue jsonData) {
      this.readField(object, fieldName, jsonName, null, jsonData);
   }

   public void readField(Object object, String fieldName, String jsonName, Class elementType, JsonValue jsonMap) {
      Class type = object.getClass();
      ObjectMap<String, Json.FieldMetadata> fields = this.getFields(type);
      Json.FieldMetadata metadata = fields.get(fieldName);
      if (metadata == null) {
         throw new SerializationException("Field not found: " + fieldName + " (" + type.getName() + ")");
      } else {
         Field field = metadata.field;
         if (elementType == null) {
            elementType = metadata.elementType;
         }

         this.readField(object, field, jsonName, elementType, jsonMap);
      }
   }

   public void readField(Object object, Field field, String jsonName, Class elementType, JsonValue jsonMap) {
      JsonValue jsonValue = jsonMap.get(jsonName);
      if (jsonValue != null) {
         try {
            field.set(object, this.readValue(field.getType(), elementType, jsonValue));
         } catch (ReflectionException var9) {
            throw new SerializationException("Error accessing field: " + field.getName() + " (" + field.getDeclaringClass().getName() + ")", var9);
         } catch (SerializationException var10) {
            var10.addTrace(field.getName() + " (" + field.getDeclaringClass().getName() + ")");
            throw var10;
         } catch (RuntimeException var11) {
            SerializationException ex = new SerializationException(var11);
            ex.addTrace(jsonValue.trace());
            ex.addTrace(field.getName() + " (" + field.getDeclaringClass().getName() + ")");
            throw ex;
         }
      }
   }

   public void readFields(Object object, JsonValue jsonMap) {
      Class type = object.getClass();
      ObjectMap<String, Json.FieldMetadata> fields = this.getFields(type);

      for (JsonValue child = jsonMap.child; child != null; child = child.next) {
         Json.FieldMetadata metadata = fields.get(child.name);
         if (metadata == null) {
            if (!child.name.equals(this.typeName) && !this.ignoreUnknownFields) {
               SerializationException ex = new SerializationException("Field not found: " + child.name + " (" + type.getName() + ")");
               ex.addTrace(child.trace());
               throw ex;
            }
         } else {
            Field field = metadata.field;

            try {
               field.set(object, this.readValue(field.getType(), metadata.elementType, child));
            } catch (ReflectionException var10) {
               throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", var10);
            } catch (SerializationException var11) {
               var11.addTrace(field.getName() + " (" + type.getName() + ")");
               throw var11;
            } catch (RuntimeException var12) {
               SerializationException ex = new SerializationException(var12);
               ex.addTrace(child.trace());
               ex.addTrace(field.getName() + " (" + type.getName() + ")");
               throw ex;
            }
         }
      }
   }

   public <T> T readValue(String name, Class<T> type, JsonValue jsonMap) {
      return this.readValue(type, null, jsonMap.get(name));
   }

   public <T> T readValue(String name, Class<T> type, T defaultValue, JsonValue jsonMap) {
      JsonValue jsonValue = jsonMap.get(name);
      return jsonValue == null ? defaultValue : this.readValue(type, null, jsonValue);
   }

   public <T> T readValue(String name, Class<T> type, Class elementType, JsonValue jsonMap) {
      return this.readValue(type, elementType, jsonMap.get(name));
   }

   public <T> T readValue(String name, Class<T> type, Class elementType, T defaultValue, JsonValue jsonMap) {
      JsonValue jsonValue = jsonMap.get(name);
      return this.readValue(type, elementType, defaultValue, jsonValue);
   }

   public <T> T readValue(Class<T> type, Class elementType, T defaultValue, JsonValue jsonData) {
      return jsonData == null ? defaultValue : this.readValue(type, elementType, jsonData);
   }

   public <T> T readValue(Class<T> type, JsonValue jsonData) {
      return this.readValue(type, null, jsonData);
   }

   public <T> T readValue(Class<T> type, Class elementType, JsonValue jsonData) {
      if (jsonData == null) {
         return null;
      } else {
         if (jsonData.isObject()) {
            String className = this.typeName == null ? null : jsonData.getString(this.typeName, null);
            if (className != null) {
               type = this.getClass(className);
               if (type == null) {
                  try {
                     type = ClassReflection.forName(className);
                  } catch (ReflectionException var9) {
                     throw new SerializationException(var9);
                  }
               }
            }

            if (type == null) {
               if (this.defaultSerializer != null) {
                  return (T)this.defaultSerializer.read(this, jsonData, type);
               }

               return (T)jsonData;
            }

            if (this.typeName == null || !ClassReflection.isAssignableFrom(Collection.class, type)) {
               Json.Serializer serializer = this.classToSerializer.get(type);
               if (serializer != null) {
                  return (T)serializer.read(this, jsonData, type);
               } else if (type != String.class
                  && type != Integer.class
                  && type != Boolean.class
                  && type != Float.class
                  && type != Long.class
                  && type != Double.class
                  && type != Short.class
                  && type != Byte.class
                  && type != Character.class
                  && !ClassReflection.isAssignableFrom(Enum.class, type)) {
                  Object object = this.newInstance(type);
                  if (object instanceof Json.Serializable) {
                     ((Json.Serializable)object).read(this, jsonData);
                     return (T)object;
                  } else if (object instanceof ObjectMap) {
                     ObjectMap result = (ObjectMap)object;

                     for (JsonValue child = jsonData.child; child != null; child = child.next) {
                        result.put(child.name, this.readValue(elementType, null, child));
                     }

                     return (T)result;
                  } else if (!(object instanceof ArrayMap)) {
                     if (object instanceof Map) {
                        Map result = (Map)object;

                        for (JsonValue child = jsonData.child; child != null; child = child.next) {
                           if (!child.name.equals(this.typeName)) {
                              result.put(child.name, this.readValue(elementType, null, child));
                           }
                        }

                        return (T)result;
                     } else {
                        this.readFields(object, jsonData);
                        return (T)object;
                     }
                  } else {
                     ArrayMap result = (ArrayMap)object;

                     for (JsonValue childx = jsonData.child; childx != null; childx = childx.next) {
                        result.put(childx.name, this.readValue(elementType, null, childx));
                     }

                     return (T)result;
                  }
               } else {
                  return this.readValue("value", type, jsonData);
               }
            }

            jsonData = jsonData.get("items");
         }

         if (type != null) {
            Json.Serializer serializer = this.classToSerializer.get(type);
            if (serializer != null) {
               return (T)serializer.read(this, jsonData, type);
            }
         }

         if (jsonData.isArray()) {
            if (type == null || type == Object.class) {
               type = (Class<T>)Array.class;
            }

            if (ClassReflection.isAssignableFrom(Array.class, type)) {
               Array result = type == Array.class ? new Array() : (Array)this.newInstance(type);

               for (JsonValue childx = jsonData.child; childx != null; childx = childx.next) {
                  result.add(this.readValue(elementType, null, childx));
               }

               return (T)result;
            } else if (ClassReflection.isAssignableFrom(Queue.class, type)) {
               Queue result = type == Queue.class ? new Queue() : (Queue)this.newInstance(type);

               for (JsonValue childx = jsonData.child; childx != null; childx = childx.next) {
                  result.addLast(this.readValue(elementType, null, childx));
               }

               return (T)result;
            } else if (ClassReflection.isAssignableFrom(Collection.class, type)) {
               Collection result = (Collection)(type.isInterface() ? new ArrayList() : (Collection)this.newInstance(type));

               for (JsonValue childx = jsonData.child; childx != null; childx = childx.next) {
                  result.add(this.readValue(elementType, null, childx));
               }

               return (T)result;
            } else if (!type.isArray()) {
               throw new SerializationException("Unable to convert value to required type: " + jsonData + " (" + type.getName() + ")");
            } else {
               Class componentType = type.getComponentType();
               if (elementType == null) {
                  elementType = componentType;
               }

               Object result = ArrayReflection.newInstance(componentType, jsonData.size);
               int i = 0;

               for (JsonValue childx = jsonData.child; childx != null; childx = childx.next) {
                  ArrayReflection.set(result, i++, this.readValue(elementType, null, childx));
               }

               return (T)result;
            }
         } else {
            if (jsonData.isNumber()) {
               try {
                  if (type == null || type == float.class || type == Float.class) {
                     return (T)jsonData.asFloat();
                  }

                  if (type == int.class || type == Integer.class) {
                     return (T)jsonData.asInt();
                  }

                  if (type == long.class || type == Long.class) {
                     return (T)jsonData.asLong();
                  }

                  if (type == double.class || type == Double.class) {
                     return (T)jsonData.asDouble();
                  }

                  if (type == String.class) {
                     return (T)jsonData.asString();
                  }

                  if (type == short.class || type == Short.class) {
                     return (T)jsonData.asShort();
                  }

                  if (type == byte.class || type == Byte.class) {
                     return (T)jsonData.asByte();
                  }
               } catch (NumberFormatException var12) {
               }

               jsonData = new JsonValue(jsonData.asString());
            }

            if (jsonData.isBoolean()) {
               try {
                  if (type == null || type == boolean.class || type == Boolean.class) {
                     return (T)jsonData.asBoolean();
                  }
               } catch (NumberFormatException var11) {
               }

               jsonData = new JsonValue(jsonData.asString());
            }

            if (!jsonData.isString()) {
               return null;
            } else {
               String string = jsonData.asString();
               if (type != null && type != String.class) {
                  try {
                     if (type == int.class || type == Integer.class) {
                        return (T)Integer.valueOf(string);
                     }

                     if (type == float.class || type == Float.class) {
                        return (T)Float.valueOf(string);
                     }

                     if (type == long.class || type == Long.class) {
                        return (T)Long.valueOf(string);
                     }

                     if (type == double.class || type == Double.class) {
                        return (T)Double.valueOf(string);
                     }

                     if (type == short.class || type == Short.class) {
                        return (T)Short.valueOf(string);
                     }

                     if (type == byte.class || type == Byte.class) {
                        return (T)Byte.valueOf(string);
                     }
                  } catch (NumberFormatException var10) {
                  }

                  if (type == boolean.class || type == Boolean.class) {
                     return (T)Boolean.valueOf(string);
                  } else if (type != char.class && type != Character.class) {
                     if (ClassReflection.isAssignableFrom(Enum.class, type)) {
                        Enum[] constants = (Enum[])type.getEnumConstants();
                        int i = 0;

                        for (int n = constants.length; i < n; i++) {
                           Enum e = constants[i];
                           if (string.equals(this.convertToString(e))) {
                              return (T)e;
                           }
                        }
                     }

                     if (type == CharSequence.class) {
                        return (T)string;
                     } else {
                        throw new SerializationException("Unable to convert value to required type: " + jsonData + " (" + type.getName() + ")");
                     }
                  } else {
                     return (T)string.charAt(0);
                  }
               } else {
                  return (T)string;
               }
            }
         }
      }
   }

   private String convertToString(Enum e) {
      return this.enumNames ? e.name() : e.toString();
   }

   private String convertToString(Object object) {
      if (object instanceof Enum) {
         return this.convertToString((Enum)object);
      } else {
         return object instanceof Class ? ((Class)object).getName() : String.valueOf(object);
      }
   }

   protected Object newInstance(Class type) {
      try {
         return ClassReflection.newInstance(type);
      } catch (Exception var7) {
         Exception ex = var7;

         try {
            Constructor constructor = ClassReflection.getDeclaredConstructor(type);
            constructor.setAccessible(true);
            return constructor.newInstance();
         } catch (SecurityException var4) {
         } catch (ReflectionException var5) {
            if (ClassReflection.isAssignableFrom(Enum.class, type)) {
               if (type.getEnumConstants() == null) {
                  type = type.getSuperclass();
               }

               return type.getEnumConstants()[0];
            }

            if (type.isArray()) {
               throw new SerializationException("Encountered JSON object when expected array of type: " + type.getName(), var7);
            }

            if (ClassReflection.isMemberClass(type) && !ClassReflection.isStaticClass(type)) {
               throw new SerializationException("Class cannot be created (non-static member class): " + type.getName(), var7);
            }

            throw new SerializationException("Class cannot be created (missing no-arg constructor): " + type.getName(), var7);
         } catch (Exception var6) {
            ex = var6;
         }

         throw new SerializationException("Error constructing instance of class: " + type.getName(), ex);
      }
   }

   public String prettyPrint(Object object) {
      return this.prettyPrint(object, 0);
   }

   public String prettyPrint(String json) {
      return this.prettyPrint(json, 0);
   }

   public String prettyPrint(Object object, int singleLineColumns) {
      return this.prettyPrint(this.toJson(object), singleLineColumns);
   }

   public String prettyPrint(String json, int singleLineColumns) {
      return new JsonReader().parse(json).prettyPrint(this.outputType, singleLineColumns);
   }

   public String prettyPrint(Object object, JsonValue.PrettyPrintSettings settings) {
      return this.prettyPrint(this.toJson(object), settings);
   }

   public String prettyPrint(String json, JsonValue.PrettyPrintSettings settings) {
      return new JsonReader().parse(json).prettyPrint(settings);
   }

   private static class FieldMetadata {
      Field field;
      Class elementType;

      public FieldMetadata(Field field) {
         this.field = field;
         int index = !ClassReflection.isAssignableFrom(ObjectMap.class, field.getType()) && !ClassReflection.isAssignableFrom(Map.class, field.getType())
            ? 0
            : 1;
         this.elementType = field.getElementType(index);
      }
   }

   public abstract static class ReadOnlySerializer<T> implements Json.Serializer<T> {
      @Override
      public void write(Json json, T object, Class knownType) {
      }

      @Override
      public abstract T read(Json var1, JsonValue var2, Class var3);
   }

   public interface Serializable {
      void write(Json var1);

      void read(Json var1, JsonValue var2);
   }

   public interface Serializer<T> {
      void write(Json var1, T var2, Class var3);

      T read(Json var1, JsonValue var2, Class var3);
   }
}
