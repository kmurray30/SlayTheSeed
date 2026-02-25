package com.badlogic.gdx.graphics.g3d.particles;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.reflect.ArrayReflection;

public class ParallelArray {
   Array<ParallelArray.Channel> arrays = new Array<>(false, 2, ParallelArray.Channel.class);
   public int capacity;
   public int size;

   public ParallelArray(int capacity) {
      this.capacity = capacity;
      this.size = 0;
   }

   public <T extends ParallelArray.Channel> T addChannel(ParallelArray.ChannelDescriptor channelDescriptor) {
      return this.addChannel(channelDescriptor, null);
   }

   public <T extends ParallelArray.Channel> T addChannel(ParallelArray.ChannelDescriptor channelDescriptor, ParallelArray.ChannelInitializer<T> initializer) {
      T channel = this.getChannel(channelDescriptor);
      if (channel == null) {
         channel = this.allocateChannel(channelDescriptor);
         if (initializer != null) {
            initializer.init(channel);
         }

         this.arrays.add(channel);
      }

      return channel;
   }

   private <T extends ParallelArray.Channel> T allocateChannel(ParallelArray.ChannelDescriptor channelDescriptor) {
      if (channelDescriptor.type == float.class) {
         return (T)(new ParallelArray.FloatChannel(channelDescriptor.id, channelDescriptor.count, this.capacity));
      } else {
         return (T)(channelDescriptor.type == int.class
            ? new ParallelArray.IntChannel(channelDescriptor.id, channelDescriptor.count, this.capacity)
            : new ParallelArray.ObjectChannel(channelDescriptor.id, channelDescriptor.count, this.capacity, channelDescriptor.type));
      }
   }

   public <T> void removeArray(int id) {
      this.arrays.removeIndex(this.findIndex(id));
   }

   private int findIndex(int id) {
      for (int i = 0; i < this.arrays.size; i++) {
         ParallelArray.Channel array = this.arrays.items[i];
         if (array.id == id) {
            return i;
         }
      }

      return -1;
   }

   public void addElement(Object... values) {
      if (this.size == this.capacity) {
         throw new GdxRuntimeException("Capacity reached, cannot add other elements");
      } else {
         int k = 0;

         for (ParallelArray.Channel strideArray : this.arrays) {
            strideArray.add(k, values);
            k += strideArray.strideSize;
         }

         this.size++;
      }
   }

   public void removeElement(int index) {
      int last = this.size - 1;

      for (ParallelArray.Channel strideArray : this.arrays) {
         strideArray.swap(index, last);
      }

      this.size = last;
   }

   public <T extends ParallelArray.Channel> T getChannel(ParallelArray.ChannelDescriptor descriptor) {
      for (ParallelArray.Channel array : this.arrays) {
         if (array.id == descriptor.id) {
            return (T)array;
         }
      }

      return null;
   }

   public void clear() {
      this.arrays.clear();
      this.size = 0;
   }

   public void setCapacity(int requiredCapacity) {
      if (this.capacity != requiredCapacity) {
         for (ParallelArray.Channel channel : this.arrays) {
            channel.setCapacity(requiredCapacity);
         }

         this.capacity = requiredCapacity;
      }
   }

   public abstract class Channel {
      public int id;
      public Object data;
      public int strideSize;

      public Channel(int id, Object data, int strideSize) {
         this.id = id;
         this.strideSize = strideSize;
         this.data = data;
      }

      public abstract void add(int var1, Object... var2);

      public abstract void swap(int var1, int var2);

      protected abstract void setCapacity(int var1);
   }

   public static class ChannelDescriptor {
      public int id;
      public Class<?> type;
      public int count;

      public ChannelDescriptor(int id, Class<?> type, int count) {
         this.id = id;
         this.type = type;
         this.count = count;
      }
   }

   public interface ChannelInitializer<T extends ParallelArray.Channel> {
      void init(T var1);
   }

   public class FloatChannel extends ParallelArray.Channel {
      public float[] data = (float[])this.data;

      public FloatChannel(int id, int strideSize, int size) {
         super(id, new float[size * strideSize], strideSize);
      }

      @Override
      public void add(int index, Object... objects) {
         int i = this.strideSize * ParallelArray.this.size;
         int c = i + this.strideSize;

         for (int k = 0; i < c; k++) {
            this.data[i] = (Float)objects[k];
            i++;
         }
      }

      @Override
      public void swap(int i, int k) {
         i = this.strideSize * i;
         k = this.strideSize * k;

         for (int c = i + this.strideSize; i < c; k++) {
            float t = this.data[i];
            this.data[i] = this.data[k];
            this.data[k] = t;
            i++;
         }
      }

      @Override
      public void setCapacity(int requiredCapacity) {
         float[] newData = new float[this.strideSize * requiredCapacity];
         System.arraycopy(this.data, 0, newData, 0, Math.min(this.data.length, newData.length));
         super.data = this.data = newData;
      }
   }

   public class IntChannel extends ParallelArray.Channel {
      public int[] data = (int[])this.data;

      public IntChannel(int id, int strideSize, int size) {
         super(id, new int[size * strideSize], strideSize);
      }

      @Override
      public void add(int index, Object... objects) {
         int i = this.strideSize * ParallelArray.this.size;
         int c = i + this.strideSize;

         for (int k = 0; i < c; k++) {
            this.data[i] = (Integer)objects[k];
            i++;
         }
      }

      @Override
      public void swap(int i, int k) {
         i = this.strideSize * i;
         k = this.strideSize * k;

         for (int c = i + this.strideSize; i < c; k++) {
            int t = this.data[i];
            this.data[i] = this.data[k];
            this.data[k] = t;
            i++;
         }
      }

      @Override
      public void setCapacity(int requiredCapacity) {
         int[] newData = new int[this.strideSize * requiredCapacity];
         System.arraycopy(this.data, 0, newData, 0, Math.min(this.data.length, newData.length));
         super.data = this.data = newData;
      }
   }

   public class ObjectChannel<T> extends ParallelArray.Channel {
      Class<T> componentType;
      public T[] data;

      public ObjectChannel(int id, int strideSize, int size, Class<T> type) {
         super(id, ArrayReflection.newInstance(type, size * strideSize), strideSize);
         this.componentType = type;
         this.data = (T[])((Object[])super.data);
      }

      @Override
      public void add(int index, Object... objects) {
         int i = this.strideSize * ParallelArray.this.size;
         int c = i + this.strideSize;

         for (int k = 0; i < c; k++) {
            this.data[i] = (T)objects[k];
            i++;
         }
      }

      @Override
      public void swap(int i, int k) {
         i = this.strideSize * i;
         k = this.strideSize * k;

         for (int c = i + this.strideSize; i < c; k++) {
            T t = this.data[i];
            this.data[i] = this.data[k];
            this.data[k] = t;
            i++;
         }
      }

      @Override
      public void setCapacity(int requiredCapacity) {
         T[] newData = (T[])((Object[])ArrayReflection.newInstance(this.componentType, this.strideSize * requiredCapacity));
         System.arraycopy(this.data, 0, newData, 0, Math.min(this.data.length, newData.length));
         super.data = this.data = newData;
      }
   }
}
