package com.badlogic.gdx.utils;

import com.badlogic.gdx.files.FileHandle;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class UBJsonReader implements BaseJsonReader {
   public boolean oldFormat = true;

   @Override
   public JsonValue parse(InputStream input) {
      DataInputStream din = null;

      JsonValue ex;
      try {
         din = new DataInputStream(input);
         ex = this.parse(din);
      } catch (IOException var7) {
         throw new SerializationException(var7);
      } finally {
         StreamUtils.closeQuietly(din);
      }

      return ex;
   }

   @Override
   public JsonValue parse(FileHandle file) {
      try {
         return this.parse(file.read(8192));
      } catch (Exception var3) {
         throw new SerializationException("Error parsing file: " + file, var3);
      }
   }

   public JsonValue parse(DataInputStream din) throws IOException {
      JsonValue var2;
      try {
         var2 = this.parse(din, din.readByte());
      } finally {
         StreamUtils.closeQuietly(din);
      }

      return var2;
   }

   protected JsonValue parse(DataInputStream din, byte type) throws IOException {
      if (type == 91) {
         return this.parseArray(din);
      } else if (type == 123) {
         return this.parseObject(din);
      } else if (type == 90) {
         return new JsonValue(JsonValue.ValueType.nullValue);
      } else if (type == 84) {
         return new JsonValue(true);
      } else if (type == 70) {
         return new JsonValue(false);
      } else if (type == 66) {
         return new JsonValue((long)this.readUChar(din));
      } else if (type == 85) {
         return new JsonValue((long)this.readUChar(din));
      } else if (type == 105) {
         return new JsonValue(this.oldFormat ? din.readShort() : din.readByte());
      } else if (type == 73) {
         return new JsonValue(this.oldFormat ? din.readInt() : din.readShort());
      } else if (type == 108) {
         return new JsonValue((long)din.readInt());
      } else if (type == 76) {
         return new JsonValue(din.readLong());
      } else if (type == 100) {
         return new JsonValue((double)din.readFloat());
      } else if (type == 68) {
         return new JsonValue(din.readDouble());
      } else if (type == 115 || type == 83) {
         return new JsonValue(this.parseString(din, type));
      } else if (type == 97 || type == 65) {
         return this.parseData(din, type);
      } else if (type == 67) {
         return new JsonValue((long)din.readChar());
      } else {
         throw new GdxRuntimeException("Unrecognized data type");
      }
   }

   protected JsonValue parseArray(DataInputStream din) throws IOException {
      JsonValue result = new JsonValue(JsonValue.ValueType.array);
      byte type = din.readByte();
      byte valueType = 0;
      if (type == 36) {
         valueType = din.readByte();
         type = din.readByte();
      }

      long size = -1L;
      if (type == 35) {
         size = this.parseSize(din, false, -1L);
         if (size < 0L) {
            throw new GdxRuntimeException("Unrecognized data type");
         }

         if (size == 0L) {
            return result;
         }

         type = valueType == 0 ? din.readByte() : valueType;
      }

      JsonValue prev = null;

      for (long c = 0L; din.available() > 0 && type != 93; type = valueType == 0 ? din.readByte() : valueType) {
         JsonValue val = this.parse(din, type);
         val.parent = result;
         if (prev != null) {
            val.prev = prev;
            prev.next = val;
            result.size++;
         } else {
            result.child = val;
            result.size = 1;
         }

         prev = val;
         if (size > 0L && ++c >= size) {
            break;
         }
      }

      return result;
   }

   protected JsonValue parseObject(DataInputStream din) throws IOException {
      JsonValue result = new JsonValue(JsonValue.ValueType.object);
      byte type = din.readByte();
      byte valueType = 0;
      if (type == 36) {
         valueType = din.readByte();
         type = din.readByte();
      }

      long size = -1L;
      if (type == 35) {
         size = this.parseSize(din, false, -1L);
         if (size < 0L) {
            throw new GdxRuntimeException("Unrecognized data type");
         }

         if (size == 0L) {
            return result;
         }

         type = din.readByte();
      }

      JsonValue prev = null;

      for (long c = 0L; din.available() > 0 && type != 125; type = din.readByte()) {
         String key = this.parseString(din, true, type);
         JsonValue child = this.parse(din, valueType == 0 ? din.readByte() : valueType);
         child.setName(key);
         child.parent = result;
         if (prev != null) {
            child.prev = prev;
            prev.next = child;
            result.size++;
         } else {
            result.child = child;
            result.size = 1;
         }

         prev = child;
         if (size > 0L && ++c >= size) {
            break;
         }
      }

      return result;
   }

   protected JsonValue parseData(DataInputStream din, byte blockType) throws IOException {
      byte dataType = din.readByte();
      long size = blockType == 65 ? this.readUInt(din) : this.readUChar(din);
      JsonValue result = new JsonValue(JsonValue.ValueType.array);
      JsonValue prev = null;

      for (long i = 0L; i < size; i++) {
         JsonValue val = this.parse(din, dataType);
         val.parent = result;
         if (prev != null) {
            prev.next = val;
            result.size++;
         } else {
            result.child = val;
            result.size = 1;
         }

         prev = val;
      }

      return result;
   }

   protected String parseString(DataInputStream din, byte type) throws IOException {
      return this.parseString(din, false, type);
   }

   protected String parseString(DataInputStream din, boolean sOptional, byte type) throws IOException {
      long size = -1L;
      if (type == 83) {
         size = this.parseSize(din, true, -1L);
      } else if (type == 115) {
         size = this.readUChar(din);
      } else if (sOptional) {
         size = this.parseSize(din, type, false, -1L);
      }

      if (size < 0L) {
         throw new GdxRuntimeException("Unrecognized data type, string expected");
      } else {
         return size > 0L ? this.readString(din, size) : "";
      }
   }

   protected long parseSize(DataInputStream din, boolean useIntOnError, long defaultValue) throws IOException {
      return this.parseSize(din, din.readByte(), useIntOnError, defaultValue);
   }

   protected long parseSize(DataInputStream din, byte type, boolean useIntOnError, long defaultValue) throws IOException {
      if (type == 105) {
         return this.readUChar(din);
      } else if (type == 73) {
         return this.readUShort(din);
      } else if (type == 108) {
         return this.readUInt(din);
      } else if (type == 76) {
         return din.readLong();
      } else if (useIntOnError) {
         long result = (long)(type & 255) << 24;
         result |= (long)(din.readByte() & 255) << 16;
         result |= (long)(din.readByte() & 255) << 8;
         return result | din.readByte() & 255;
      } else {
         return defaultValue;
      }
   }

   protected short readUChar(DataInputStream din) throws IOException {
      return (short)(din.readByte() & 255);
   }

   protected int readUShort(DataInputStream din) throws IOException {
      return din.readShort() & 65535;
   }

   protected long readUInt(DataInputStream din) throws IOException {
      return din.readInt() & -1L;
   }

   protected String readString(DataInputStream din, long size) throws IOException {
      byte[] data = new byte[(int)size];
      din.readFully(data);
      return new String(data, "UTF-8");
   }
}
