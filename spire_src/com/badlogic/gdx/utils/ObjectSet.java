package com.badlogic.gdx.utils;

import com.badlogic.gdx.math.MathUtils;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class ObjectSet<T> implements Iterable<T> {
   private static final int PRIME1 = -1105259343;
   private static final int PRIME2 = -1262997959;
   private static final int PRIME3 = -825114047;
   public int size;
   T[] keyTable;
   int capacity;
   int stashSize;
   private float loadFactor;
   private int hashShift;
   private int mask;
   private int threshold;
   private int stashCapacity;
   private int pushIterations;
   private ObjectSet.ObjectSetIterator iterator1;
   private ObjectSet.ObjectSetIterator iterator2;

   public ObjectSet() {
      this(51, 0.8F);
   }

   public ObjectSet(int initialCapacity) {
      this(initialCapacity, 0.8F);
   }

   public ObjectSet(int initialCapacity, float loadFactor) {
      if (initialCapacity < 0) {
         throw new IllegalArgumentException("initialCapacity must be >= 0: " + initialCapacity);
      } else {
         initialCapacity = MathUtils.nextPowerOfTwo((int)Math.ceil(initialCapacity / loadFactor));
         if (initialCapacity > 1073741824) {
            throw new IllegalArgumentException("initialCapacity is too large: " + initialCapacity);
         } else {
            this.capacity = initialCapacity;
            if (loadFactor <= 0.0F) {
               throw new IllegalArgumentException("loadFactor must be > 0: " + loadFactor);
            } else {
               this.loadFactor = loadFactor;
               this.threshold = (int)(this.capacity * loadFactor);
               this.mask = this.capacity - 1;
               this.hashShift = 31 - Integer.numberOfTrailingZeros(this.capacity);
               this.stashCapacity = Math.max(3, (int)Math.ceil(Math.log(this.capacity)) * 2);
               this.pushIterations = Math.max(Math.min(this.capacity, 8), (int)Math.sqrt(this.capacity) / 8);
               this.keyTable = (T[])(new Object[this.capacity + this.stashCapacity]);
            }
         }
      }
   }

   public ObjectSet(ObjectSet set) {
      this((int)Math.floor(set.capacity * set.loadFactor), set.loadFactor);
      this.stashSize = set.stashSize;
      System.arraycopy(set.keyTable, 0, this.keyTable, 0, set.keyTable.length);
      this.size = set.size;
   }

   public boolean add(T key) {
      if (key == null) {
         throw new IllegalArgumentException("key cannot be null.");
      } else {
         T[] keyTable = this.keyTable;
         int hashCode = key.hashCode();
         int index1 = hashCode & this.mask;
         T key1 = keyTable[index1];
         if (key.equals(key1)) {
            return false;
         } else {
            int index2 = this.hash2(hashCode);
            T key2 = keyTable[index2];
            if (key.equals(key2)) {
               return false;
            } else {
               int index3 = this.hash3(hashCode);
               T key3 = keyTable[index3];
               if (key.equals(key3)) {
                  return false;
               } else {
                  int i = this.capacity;

                  for (int n = i + this.stashSize; i < n; i++) {
                     if (key.equals(keyTable[i])) {
                        return false;
                     }
                  }

                  if (key1 == null) {
                     keyTable[index1] = key;
                     if (this.size++ >= this.threshold) {
                        this.resize(this.capacity << 1);
                     }

                     return true;
                  } else if (key2 == null) {
                     keyTable[index2] = key;
                     if (this.size++ >= this.threshold) {
                        this.resize(this.capacity << 1);
                     }

                     return true;
                  } else if (key3 == null) {
                     keyTable[index3] = key;
                     if (this.size++ >= this.threshold) {
                        this.resize(this.capacity << 1);
                     }

                     return true;
                  } else {
                     this.push(key, index1, key1, index2, key2, index3, key3);
                     return true;
                  }
               }
            }
         }
      }
   }

   public void addAll(Array<? extends T> array) {
      this.addAll(array, 0, array.size);
   }

   public void addAll(Array<? extends T> array, int offset, int length) {
      if (offset + length > array.size) {
         throw new IllegalArgumentException("offset + length must be <= size: " + offset + " + " + length + " <= " + array.size);
      } else {
         this.addAll((T[])array.items, offset, length);
      }
   }

   public void addAll(T... array) {
      this.addAll(array, 0, array.length);
   }

   public void addAll(T[] array, int offset, int length) {
      this.ensureCapacity(length);
      int i = offset;

      for (int n = offset + length; i < n; i++) {
         this.add(array[i]);
      }
   }

   public void addAll(ObjectSet<T> set) {
      this.ensureCapacity(set.size);

      for (T key : set) {
         this.add(key);
      }
   }

   private void addResize(T key) {
      int hashCode = key.hashCode();
      int index1 = hashCode & this.mask;
      T key1 = this.keyTable[index1];
      if (key1 == null) {
         this.keyTable[index1] = key;
         if (this.size++ >= this.threshold) {
            this.resize(this.capacity << 1);
         }
      } else {
         int index2 = this.hash2(hashCode);
         T key2 = this.keyTable[index2];
         if (key2 == null) {
            this.keyTable[index2] = key;
            if (this.size++ >= this.threshold) {
               this.resize(this.capacity << 1);
            }
         } else {
            int index3 = this.hash3(hashCode);
            T key3 = this.keyTable[index3];
            if (key3 == null) {
               this.keyTable[index3] = key;
               if (this.size++ >= this.threshold) {
                  this.resize(this.capacity << 1);
               }
            } else {
               this.push(key, index1, key1, index2, key2, index3, key3);
            }
         }
      }
   }

   private void push(T insertKey, int index1, T key1, int index2, T key2, int index3, T key3) {
      T[] keyTable = this.keyTable;
      int mask = this.mask;
      int i = 0;
      int pushIterations = this.pushIterations;

      while (true) {
         T evictedKey;
         switch (MathUtils.random(2)) {
            case 0:
               evictedKey = key1;
               keyTable[index1] = insertKey;
               break;
            case 1:
               evictedKey = key2;
               keyTable[index2] = insertKey;
               break;
            default:
               evictedKey = key3;
               keyTable[index3] = insertKey;
         }

         int hashCode = evictedKey.hashCode();
         index1 = hashCode & mask;
         key1 = keyTable[index1];
         if (key1 == null) {
            keyTable[index1] = evictedKey;
            if (this.size++ >= this.threshold) {
               this.resize(this.capacity << 1);
            }

            return;
         }

         index2 = this.hash2(hashCode);
         key2 = keyTable[index2];
         if (key2 == null) {
            keyTable[index2] = evictedKey;
            if (this.size++ >= this.threshold) {
               this.resize(this.capacity << 1);
            }

            return;
         }

         index3 = this.hash3(hashCode);
         key3 = keyTable[index3];
         if (key3 == null) {
            keyTable[index3] = evictedKey;
            if (this.size++ >= this.threshold) {
               this.resize(this.capacity << 1);
            }

            return;
         }

         if (++i == pushIterations) {
            this.addStash(evictedKey);
            return;
         }

         insertKey = evictedKey;
      }
   }

   private void addStash(T key) {
      if (this.stashSize == this.stashCapacity) {
         this.resize(this.capacity << 1);
         this.add(key);
      } else {
         int index = this.capacity + this.stashSize;
         this.keyTable[index] = key;
         this.stashSize++;
         this.size++;
      }
   }

   public boolean remove(T key) {
      int hashCode = key.hashCode();
      int index = hashCode & this.mask;
      if (key.equals(this.keyTable[index])) {
         this.keyTable[index] = null;
         this.size--;
         return true;
      } else {
         index = this.hash2(hashCode);
         if (key.equals(this.keyTable[index])) {
            this.keyTable[index] = null;
            this.size--;
            return true;
         } else {
            index = this.hash3(hashCode);
            if (key.equals(this.keyTable[index])) {
               this.keyTable[index] = null;
               this.size--;
               return true;
            } else {
               return this.removeStash(key);
            }
         }
      }
   }

   boolean removeStash(T key) {
      T[] keyTable = this.keyTable;
      int i = this.capacity;

      for (int n = i + this.stashSize; i < n; i++) {
         if (key.equals(keyTable[i])) {
            this.removeStashIndex(i);
            this.size--;
            return true;
         }
      }

      return false;
   }

   void removeStashIndex(int index) {
      this.stashSize--;
      int lastIndex = this.capacity + this.stashSize;
      if (index < lastIndex) {
         this.keyTable[index] = this.keyTable[lastIndex];
      }
   }

   public void shrink(int maximumCapacity) {
      if (maximumCapacity < 0) {
         throw new IllegalArgumentException("maximumCapacity must be >= 0: " + maximumCapacity);
      } else {
         if (this.size > maximumCapacity) {
            maximumCapacity = this.size;
         }

         if (this.capacity > maximumCapacity) {
            maximumCapacity = MathUtils.nextPowerOfTwo(maximumCapacity);
            this.resize(maximumCapacity);
         }
      }
   }

   public void clear(int maximumCapacity) {
      if (this.capacity <= maximumCapacity) {
         this.clear();
      } else {
         this.size = 0;
         this.resize(maximumCapacity);
      }
   }

   public void clear() {
      if (this.size != 0) {
         T[] keyTable = this.keyTable;
         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            keyTable[i] = null;
         }

         this.size = 0;
         this.stashSize = 0;
      }
   }

   public boolean contains(T key) {
      int hashCode = key.hashCode();
      int index = hashCode & this.mask;
      if (!key.equals(this.keyTable[index])) {
         index = this.hash2(hashCode);
         if (!key.equals(this.keyTable[index])) {
            index = this.hash3(hashCode);
            if (!key.equals(this.keyTable[index])) {
               return this.containsKeyStash(key);
            }
         }
      }

      return true;
   }

   private boolean containsKeyStash(T key) {
      T[] keyTable = this.keyTable;
      int i = this.capacity;

      for (int n = i + this.stashSize; i < n; i++) {
         if (key.equals(keyTable[i])) {
            return true;
         }
      }

      return false;
   }

   public T first() {
      T[] keyTable = this.keyTable;
      int i = 0;

      for (int n = this.capacity + this.stashSize; i < n; i++) {
         if (keyTable[i] != null) {
            return keyTable[i];
         }
      }

      throw new IllegalStateException("ObjectSet is empty.");
   }

   public void ensureCapacity(int additionalCapacity) {
      int sizeNeeded = this.size + additionalCapacity;
      if (sizeNeeded >= this.threshold) {
         this.resize(MathUtils.nextPowerOfTwo((int)Math.ceil(sizeNeeded / this.loadFactor)));
      }
   }

   private void resize(int newSize) {
      int oldEndIndex = this.capacity + this.stashSize;
      this.capacity = newSize;
      this.threshold = (int)(newSize * this.loadFactor);
      this.mask = newSize - 1;
      this.hashShift = 31 - Integer.numberOfTrailingZeros(newSize);
      this.stashCapacity = Math.max(3, (int)Math.ceil(Math.log(newSize)) * 2);
      this.pushIterations = Math.max(Math.min(newSize, 8), (int)Math.sqrt(newSize) / 8);
      T[] oldKeyTable = this.keyTable;
      this.keyTable = (T[])(new Object[newSize + this.stashCapacity]);
      int oldSize = this.size;
      this.size = 0;
      this.stashSize = 0;
      if (oldSize > 0) {
         for (int i = 0; i < oldEndIndex; i++) {
            T key = oldKeyTable[i];
            if (key != null) {
               this.addResize(key);
            }
         }
      }
   }

   private int hash2(int h) {
      h *= -1262997959;
      return (h ^ h >>> this.hashShift) & this.mask;
   }

   private int hash3(int h) {
      h *= -825114047;
      return (h ^ h >>> this.hashShift) & this.mask;
   }

   @Override
   public int hashCode() {
      int h = 0;
      int i = 0;

      for (int n = this.capacity + this.stashSize; i < n; i++) {
         if (this.keyTable[i] != null) {
            h += this.keyTable[i].hashCode();
         }
      }

      return h;
   }

   @Override
   public boolean equals(Object obj) {
      if (!(obj instanceof ObjectSet)) {
         return false;
      } else {
         ObjectSet other = (ObjectSet)obj;
         if (other.size != this.size) {
            return false;
         } else {
            int i = 0;

            for (int n = this.capacity + this.stashSize; i < n; i++) {
               if (this.keyTable[i] != null && !other.contains(this.keyTable[i])) {
                  return false;
               }
            }

            return true;
         }
      }
   }

   @Override
   public String toString() {
      return '{' + this.toString(", ") + '}';
   }

   public String toString(String separator) {
      if (this.size == 0) {
         return "";
      } else {
         StringBuilder buffer = new StringBuilder(32);
         T[] keyTable = this.keyTable;
         int i = keyTable.length;

         while (i-- > 0) {
            T key = keyTable[i];
            if (key != null) {
               buffer.append(key);
               break;
            }
         }

         while (i-- > 0) {
            T key = keyTable[i];
            if (key != null) {
               buffer.append(separator);
               buffer.append(key);
            }
         }

         return buffer.toString();
      }
   }

   public ObjectSet.ObjectSetIterator<T> iterator() {
      if (this.iterator1 == null) {
         this.iterator1 = new ObjectSet.ObjectSetIterator<>(this);
         this.iterator2 = new ObjectSet.ObjectSetIterator<>(this);
      }

      if (!this.iterator1.valid) {
         this.iterator1.reset();
         this.iterator1.valid = true;
         this.iterator2.valid = false;
         return this.iterator1;
      } else {
         this.iterator2.reset();
         this.iterator2.valid = true;
         this.iterator1.valid = false;
         return this.iterator2;
      }
   }

   public static <T> ObjectSet<T> with(T... array) {
      ObjectSet set = new ObjectSet();
      set.addAll(array);
      return set;
   }

   public static class ObjectSetIterator<K> implements Iterable<K>, Iterator<K> {
      public boolean hasNext;
      final ObjectSet<K> set;
      int nextIndex;
      int currentIndex;
      boolean valid = true;

      public ObjectSetIterator(ObjectSet<K> set) {
         this.set = set;
         this.reset();
      }

      public void reset() {
         this.currentIndex = -1;
         this.nextIndex = -1;
         this.findNextIndex();
      }

      void findNextIndex() {
         this.hasNext = false;
         K[] keyTable = this.set.keyTable;
         int n = this.set.capacity + this.set.stashSize;

         while (++this.nextIndex < n) {
            if (keyTable[this.nextIndex] != null) {
               this.hasNext = true;
               break;
            }
         }
      }

      @Override
      public void remove() {
         if (this.currentIndex < 0) {
            throw new IllegalStateException("next must be called before remove.");
         } else {
            if (this.currentIndex >= this.set.capacity) {
               this.set.removeStashIndex(this.currentIndex);
               this.nextIndex = this.currentIndex - 1;
               this.findNextIndex();
            } else {
               this.set.keyTable[this.currentIndex] = null;
            }

            this.currentIndex = -1;
            this.set.size--;
         }
      }

      @Override
      public boolean hasNext() {
         if (!this.valid) {
            throw new GdxRuntimeException("#iterator() cannot be used nested.");
         } else {
            return this.hasNext;
         }
      }

      @Override
      public K next() {
         if (!this.hasNext) {
            throw new NoSuchElementException();
         } else if (!this.valid) {
            throw new GdxRuntimeException("#iterator() cannot be used nested.");
         } else {
            K key = this.set.keyTable[this.nextIndex];
            this.currentIndex = this.nextIndex;
            this.findNextIndex();
            return key;
         }
      }

      public ObjectSet.ObjectSetIterator<K> iterator() {
         return this;
      }

      public Array<K> toArray(Array<K> array) {
         while (this.hasNext) {
            array.add(this.next());
         }

         return array;
      }

      public Array<K> toArray() {
         return this.toArray(new Array(true, this.set.size));
      }
   }
}
