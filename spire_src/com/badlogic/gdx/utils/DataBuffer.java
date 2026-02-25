package com.badlogic.gdx.utils;

public class DataBuffer extends DataOutput {
   private final StreamUtils.OptimizedByteArrayOutputStream outStream = (StreamUtils.OptimizedByteArrayOutputStream)this.out;

   public DataBuffer() {
      this(32);
   }

   public DataBuffer(int initialSize) {
      super(new StreamUtils.OptimizedByteArrayOutputStream(initialSize));
   }

   public byte[] getBuffer() {
      return this.outStream.getBuffer();
   }

   public byte[] toArray() {
      return this.outStream.toByteArray();
   }
}
