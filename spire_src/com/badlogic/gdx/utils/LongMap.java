package com.badlogic.gdx.utils;

import com.badlogic.gdx.math.MathUtils;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class LongMap<V> implements Iterable<LongMap.Entry<V>> {
   private static final int PRIME1 = -1105259343;
   private static final int PRIME2 = -1262997959;
   private static final int PRIME3 = -825114047;
   private static final int EMPTY = 0;
   public int size;
   long[] keyTable;
   V[] valueTable;
   int capacity;
   int stashSize;
   V zeroValue;
   boolean hasZeroValue;
   private float loadFactor;
   private int hashShift;
   private int mask;
   private int threshold;
   private int stashCapacity;
   private int pushIterations;
   private LongMap.Entries entries1;
   private LongMap.Entries entries2;
   private LongMap.Values values1;
   private LongMap.Values values2;
   private LongMap.Keys keys1;
   private LongMap.Keys keys2;

   public LongMap() {
      this(51, 0.8F);
   }

   public LongMap(int initialCapacity) {
      this(initialCapacity, 0.8F);
   }

   public LongMap(int initialCapacity, float loadFactor) {
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
               this.hashShift = 63 - Long.numberOfTrailingZeros(this.capacity);
               this.stashCapacity = Math.max(3, (int)Math.ceil(Math.log(this.capacity)) * 2);
               this.pushIterations = Math.max(Math.min(this.capacity, 8), (int)Math.sqrt(this.capacity) / 8);
               this.keyTable = new long[this.capacity + this.stashCapacity];
               this.valueTable = (V[])(new Object[this.keyTable.length]);
            }
         }
      }
   }

   public LongMap(LongMap<? extends V> map) {
      this((int)Math.floor(map.capacity * map.loadFactor), map.loadFactor);
      this.stashSize = map.stashSize;
      System.arraycopy(map.keyTable, 0, this.keyTable, 0, map.keyTable.length);
      System.arraycopy(map.valueTable, 0, this.valueTable, 0, map.valueTable.length);
      this.size = map.size;
      this.zeroValue = (V)map.zeroValue;
      this.hasZeroValue = map.hasZeroValue;
   }

   public V put(long key, V value) {
      if (key == 0L) {
         V oldValue = this.zeroValue;
         this.zeroValue = value;
         if (!this.hasZeroValue) {
            this.hasZeroValue = true;
            this.size++;
         }

         return oldValue;
      } else {
         long[] keyTable = this.keyTable;
         int index1 = (int)(key & this.mask);
         long key1 = keyTable[index1];
         if (key1 == key) {
            V oldValue = this.valueTable[index1];
            this.valueTable[index1] = value;
            return oldValue;
         } else {
            int index2 = this.hash2(key);
            long key2 = keyTable[index2];
            if (key2 == key) {
               V oldValue = this.valueTable[index2];
               this.valueTable[index2] = value;
               return oldValue;
            } else {
               int index3 = this.hash3(key);
               long key3 = keyTable[index3];
               if (key3 == key) {
                  V oldValue = this.valueTable[index3];
                  this.valueTable[index3] = value;
                  return oldValue;
               } else {
                  int i = this.capacity;

                  for (int n = i + this.stashSize; i < n; i++) {
                     if (keyTable[i] == key) {
                        V oldValue = this.valueTable[i];
                        this.valueTable[i] = value;
                        return oldValue;
                     }
                  }

                  if (key1 == 0L) {
                     keyTable[index1] = key;
                     this.valueTable[index1] = value;
                     if (this.size++ >= this.threshold) {
                        this.resize(this.capacity << 1);
                     }

                     return null;
                  } else if (key2 == 0L) {
                     keyTable[index2] = key;
                     this.valueTable[index2] = value;
                     if (this.size++ >= this.threshold) {
                        this.resize(this.capacity << 1);
                     }

                     return null;
                  } else if (key3 == 0L) {
                     keyTable[index3] = key;
                     this.valueTable[index3] = value;
                     if (this.size++ >= this.threshold) {
                        this.resize(this.capacity << 1);
                     }

                     return null;
                  } else {
                     this.push(key, value, index1, key1, index2, key2, index3, key3);
                     return null;
                  }
               }
            }
         }
      }
   }

   public void putAll(LongMap<V> map) {
      for (LongMap.Entry<V> entry : map.entries()) {
         this.put(entry.key, entry.value);
      }
   }

   private void putResize(long key, V value) {
      if (key == 0L) {
         this.zeroValue = value;
         this.hasZeroValue = true;
      } else {
         int index1 = (int)(key & this.mask);
         long key1 = this.keyTable[index1];
         if (key1 == 0L) {
            this.keyTable[index1] = key;
            this.valueTable[index1] = value;
            if (this.size++ >= this.threshold) {
               this.resize(this.capacity << 1);
            }
         } else {
            int index2 = this.hash2(key);
            long key2 = this.keyTable[index2];
            if (key2 == 0L) {
               this.keyTable[index2] = key;
               this.valueTable[index2] = value;
               if (this.size++ >= this.threshold) {
                  this.resize(this.capacity << 1);
               }
            } else {
               int index3 = this.hash3(key);
               long key3 = this.keyTable[index3];
               if (key3 == 0L) {
                  this.keyTable[index3] = key;
                  this.valueTable[index3] = value;
                  if (this.size++ >= this.threshold) {
                     this.resize(this.capacity << 1);
                  }
               } else {
                  this.push(key, value, index1, key1, index2, key2, index3, key3);
               }
            }
         }
      }
   }

   private void push(long insertKey, V insertValue, int index1, long key1, int index2, long key2, int index3, long key3) {
      long[] keyTable = this.keyTable;
      V[] valueTable = this.valueTable;
      int mask = this.mask;
      int i = 0;
      int pushIterations = this.pushIterations;

      while (true) {
         long evictedKey;
         V evictedValue;
         switch (MathUtils.random(2)) {
            case 0:
               evictedKey = key1;
               evictedValue = valueTable[index1];
               keyTable[index1] = insertKey;
               valueTable[index1] = insertValue;
               break;
            case 1:
               evictedKey = key2;
               evictedValue = valueTable[index2];
               keyTable[index2] = insertKey;
               valueTable[index2] = insertValue;
               break;
            default:
               evictedKey = key3;
               evictedValue = valueTable[index3];
               keyTable[index3] = insertKey;
               valueTable[index3] = insertValue;
         }

         index1 = (int)(evictedKey & mask);
         key1 = keyTable[index1];
         if (key1 == 0L) {
            keyTable[index1] = evictedKey;
            valueTable[index1] = evictedValue;
            if (this.size++ >= this.threshold) {
               this.resize(this.capacity << 1);
            }

            return;
         }

         index2 = this.hash2(evictedKey);
         key2 = keyTable[index2];
         if (key2 == 0L) {
            keyTable[index2] = evictedKey;
            valueTable[index2] = evictedValue;
            if (this.size++ >= this.threshold) {
               this.resize(this.capacity << 1);
            }

            return;
         }

         index3 = this.hash3(evictedKey);
         key3 = keyTable[index3];
         if (key3 == 0L) {
            keyTable[index3] = evictedKey;
            valueTable[index3] = evictedValue;
            if (this.size++ >= this.threshold) {
               this.resize(this.capacity << 1);
            }

            return;
         }

         if (++i == pushIterations) {
            this.putStash(evictedKey, evictedValue);
            return;
         }

         insertKey = evictedKey;
         insertValue = evictedValue;
      }
   }

   private void putStash(long key, V value) {
      if (this.stashSize == this.stashCapacity) {
         this.resize(this.capacity << 1);
         this.put(key, value);
      } else {
         int index = this.capacity + this.stashSize;
         this.keyTable[index] = key;
         this.valueTable[index] = value;
         this.stashSize++;
         this.size++;
      }
   }

   public V get(long key) {
      if (key == 0L) {
         return !this.hasZeroValue ? null : this.zeroValue;
      } else {
         int index = (int)(key & this.mask);
         if (this.keyTable[index] != key) {
            index = this.hash2(key);
            if (this.keyTable[index] != key) {
               index = this.hash3(key);
               if (this.keyTable[index] != key) {
                  return this.getStash(key, null);
               }
            }
         }

         return this.valueTable[index];
      }
   }

   public V get(long key, V defaultValue) {
      if (key == 0L) {
         return !this.hasZeroValue ? defaultValue : this.zeroValue;
      } else {
         int index = (int)(key & this.mask);
         if (this.keyTable[index] != key) {
            index = this.hash2(key);
            if (this.keyTable[index] != key) {
               index = this.hash3(key);
               if (this.keyTable[index] != key) {
                  return this.getStash(key, defaultValue);
               }
            }
         }

         return this.valueTable[index];
      }
   }

   private V getStash(long key, V defaultValue) {
      long[] keyTable = this.keyTable;
      int i = this.capacity;

      for (int n = i + this.stashSize; i < n; i++) {
         if (keyTable[i] == key) {
            return this.valueTable[i];
         }
      }

      return defaultValue;
   }

   public V remove(long key) {
      if (key == 0L) {
         if (!this.hasZeroValue) {
            return null;
         } else {
            V oldValue = this.zeroValue;
            this.zeroValue = null;
            this.hasZeroValue = false;
            this.size--;
            return oldValue;
         }
      } else {
         int index = (int)(key & this.mask);
         if (this.keyTable[index] == key) {
            this.keyTable[index] = 0L;
            V oldValue = this.valueTable[index];
            this.valueTable[index] = null;
            this.size--;
            return oldValue;
         } else {
            index = this.hash2(key);
            if (this.keyTable[index] == key) {
               this.keyTable[index] = 0L;
               V oldValue = this.valueTable[index];
               this.valueTable[index] = null;
               this.size--;
               return oldValue;
            } else {
               index = this.hash3(key);
               if (this.keyTable[index] == key) {
                  this.keyTable[index] = 0L;
                  V oldValue = this.valueTable[index];
                  this.valueTable[index] = null;
                  this.size--;
                  return oldValue;
               } else {
                  return this.removeStash(key);
               }
            }
         }
      }
   }

   V removeStash(long key) {
      long[] keyTable = this.keyTable;
      int i = this.capacity;

      for (int n = i + this.stashSize; i < n; i++) {
         if (keyTable[i] == key) {
            V oldValue = this.valueTable[i];
            this.removeStashIndex(i);
            this.size--;
            return oldValue;
         }
      }

      return null;
   }

   void removeStashIndex(int index) {
      this.stashSize--;
      int lastIndex = this.capacity + this.stashSize;
      if (index < lastIndex) {
         this.keyTable[index] = this.keyTable[lastIndex];
         this.valueTable[index] = this.valueTable[lastIndex];
         this.valueTable[lastIndex] = null;
      } else {
         this.valueTable[index] = null;
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
         this.zeroValue = null;
         this.hasZeroValue = false;
         this.size = 0;
         this.resize(maximumCapacity);
      }
   }

   public void clear() {
      if (this.size != 0) {
         long[] keyTable = this.keyTable;
         V[] valueTable = this.valueTable;

         for (int i = this.capacity + this.stashSize; i-- > 0; valueTable[i] = null) {
            keyTable[i] = 0L;
         }

         this.size = 0;
         this.stashSize = 0;
         this.zeroValue = null;
         this.hasZeroValue = false;
      }
   }

   public boolean containsValue(Object value, boolean identity) {
      V[] valueTable = this.valueTable;
      if (value == null) {
         if (this.hasZeroValue && this.zeroValue == null) {
            return true;
         }

         long[] keyTable = this.keyTable;
         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (keyTable[i] != 0L && valueTable[i] == null) {
               return true;
            }
         }
      } else if (identity) {
         if (value == this.zeroValue) {
            return true;
         }

         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (valueTable[i] == value) {
               return true;
            }
         }
      } else {
         if (this.hasZeroValue && value.equals(this.zeroValue)) {
            return true;
         }

         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (value.equals(valueTable[i])) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean containsKey(long key) {
      if (key == 0L) {
         return this.hasZeroValue;
      } else {
         int index = (int)(key & this.mask);
         if (this.keyTable[index] != key) {
            index = this.hash2(key);
            if (this.keyTable[index] != key) {
               index = this.hash3(key);
               if (this.keyTable[index] != key) {
                  return this.containsKeyStash(key);
               }
            }
         }

         return true;
      }
   }

   private boolean containsKeyStash(long key) {
      long[] keyTable = this.keyTable;
      int i = this.capacity;

      for (int n = i + this.stashSize; i < n; i++) {
         if (keyTable[i] == key) {
            return true;
         }
      }

      return false;
   }

   public long findKey(Object value, boolean identity, long notFound) {
      V[] valueTable = this.valueTable;
      if (value == null) {
         if (this.hasZeroValue && this.zeroValue == null) {
            return 0L;
         }

         long[] keyTable = this.keyTable;
         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (keyTable[i] != 0L && valueTable[i] == null) {
               return keyTable[i];
            }
         }
      } else if (identity) {
         if (value == this.zeroValue) {
            return 0L;
         }

         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (valueTable[i] == value) {
               return this.keyTable[i];
            }
         }
      } else {
         if (this.hasZeroValue && value.equals(this.zeroValue)) {
            return 0L;
         }

         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (value.equals(valueTable[i])) {
               return this.keyTable[i];
            }
         }
      }

      return notFound;
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
      this.hashShift = 63 - Long.numberOfTrailingZeros(newSize);
      this.stashCapacity = Math.max(3, (int)Math.ceil(Math.log(newSize)) * 2);
      this.pushIterations = Math.max(Math.min(newSize, 8), (int)Math.sqrt(newSize) / 8);
      long[] oldKeyTable = this.keyTable;
      V[] oldValueTable = this.valueTable;
      this.keyTable = new long[newSize + this.stashCapacity];
      this.valueTable = (V[])(new Object[newSize + this.stashCapacity]);
      int oldSize = this.size;
      this.size = this.hasZeroValue ? 1 : 0;
      this.stashSize = 0;
      if (oldSize > 0) {
         for (int i = 0; i < oldEndIndex; i++) {
            long key = oldKeyTable[i];
            if (key != 0L) {
               this.putResize(key, oldValueTable[i]);
            }
         }
      }
   }

   private int hash2(long h) {
      h *= -1262997959L;
      return (int)((h ^ h >>> this.hashShift) & this.mask);
   }

   private int hash3(long h) {
      h *= -825114047L;
      return (int)((h ^ h >>> this.hashShift) & this.mask);
   }

   @Override
   public int hashCode() {
      int h = 0;
      if (this.hasZeroValue && this.zeroValue != null) {
         h += this.zeroValue.hashCode();
      }

      long[] keyTable = this.keyTable;
      V[] valueTable = this.valueTable;
      int i = 0;

      for (int n = this.capacity + this.stashSize; i < n; i++) {
         long key = keyTable[i];
         if (key != 0L) {
            h += (int)(key ^ key >>> 32) * 31;
            V value = valueTable[i];
            if (value != null) {
               h += value.hashCode();
            }
         }
      }

      return h;
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == this) {
         return true;
      } else if (!(obj instanceof LongMap)) {
         return false;
      } else {
         LongMap<V> other = (LongMap<V>)obj;
         if (other.size != this.size) {
            return false;
         } else if (other.hasZeroValue != this.hasZeroValue) {
            return false;
         } else {
            if (this.hasZeroValue) {
               if (other.zeroValue == null) {
                  if (this.zeroValue != null) {
                     return false;
                  }
               } else if (!other.zeroValue.equals(this.zeroValue)) {
                  return false;
               }
            }

            long[] keyTable = this.keyTable;
            V[] valueTable = this.valueTable;
            int i = 0;

            for (int n = this.capacity + this.stashSize; i < n; i++) {
               long key = keyTable[i];
               if (key != 0L) {
                  V value = valueTable[i];
                  if (value == null) {
                     if (!other.containsKey(key) || other.get(key) != null) {
                        return false;
                     }
                  } else if (!value.equals(other.get(key))) {
                     return false;
                  }
               }
            }

            return true;
         }
      }
   }

   @Override
   public String toString() {
      if (this.size == 0) {
         return "[]";
      } else {
         StringBuilder buffer = new StringBuilder(32);
         buffer.append('[');
         long[] keyTable = this.keyTable;
         V[] valueTable = this.valueTable;
         int i = keyTable.length;

         while (i-- > 0) {
            long key = keyTable[i];
            if (key != 0L) {
               buffer.append(key);
               buffer.append('=');
               buffer.append(valueTable[i]);
               break;
            }
         }

         while (i-- > 0) {
            long key = keyTable[i];
            if (key != 0L) {
               buffer.append(", ");
               buffer.append(key);
               buffer.append('=');
               buffer.append(valueTable[i]);
            }
         }

         buffer.append(']');
         return buffer.toString();
      }
   }

   @Override
   public Iterator<LongMap.Entry<V>> iterator() {
      return this.entries();
   }

   public LongMap.Entries<V> entries() {
      if (this.entries1 == null) {
         this.entries1 = new LongMap.Entries(this);
         this.entries2 = new LongMap.Entries(this);
      }

      if (!this.entries1.valid) {
         this.entries1.reset();
         this.entries1.valid = true;
         this.entries2.valid = false;
         return this.entries1;
      } else {
         this.entries2.reset();
         this.entries2.valid = true;
         this.entries1.valid = false;
         return this.entries2;
      }
   }

   public LongMap.Values<V> values() {
      if (this.values1 == null) {
         this.values1 = new LongMap.Values<>(this);
         this.values2 = new LongMap.Values<>(this);
      }

      if (!this.values1.valid) {
         this.values1.reset();
         this.values1.valid = true;
         this.values2.valid = false;
         return this.values1;
      } else {
         this.values2.reset();
         this.values2.valid = true;
         this.values1.valid = false;
         return this.values2;
      }
   }

   public LongMap.Keys keys() {
      if (this.keys1 == null) {
         this.keys1 = new LongMap.Keys(this);
         this.keys2 = new LongMap.Keys(this);
      }

      if (!this.keys1.valid) {
         this.keys1.reset();
         this.keys1.valid = true;
         this.keys2.valid = false;
         return this.keys1;
      } else {
         this.keys2.reset();
         this.keys2.valid = true;
         this.keys1.valid = false;
         return this.keys2;
      }
   }

   public static class Entries<V> extends LongMap.MapIterator<V> implements Iterable<LongMap.Entry<V>>, Iterator<LongMap.Entry<V>> {
      private LongMap.Entry<V> entry = new LongMap.Entry<>();

      public Entries(LongMap map) {
         super(map);
      }

      public LongMap.Entry<V> next() {
         if (!this.hasNext) {
            throw new NoSuchElementException();
         } else if (!this.valid) {
            throw new GdxRuntimeException("#iterator() cannot be used nested.");
         } else {
            long[] keyTable = this.map.keyTable;
            if (this.nextIndex == -1) {
               this.entry.key = 0L;
               this.entry.value = this.map.zeroValue;
            } else {
               this.entry.key = keyTable[this.nextIndex];
               this.entry.value = this.map.valueTable[this.nextIndex];
            }

            this.currentIndex = this.nextIndex;
            this.findNextIndex();
            return this.entry;
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
      public Iterator<LongMap.Entry<V>> iterator() {
         return this;
      }

      @Override
      public void remove() {
         super.remove();
      }
   }

   public static class Entry<V> {
      public long key;
      public V value;

      @Override
      public String toString() {
         return this.key + "=" + this.value;
      }
   }

   public static class Keys extends LongMap.MapIterator {
      public Keys(LongMap map) {
         super(map);
      }

      public long next() {
         if (!this.hasNext) {
            throw new NoSuchElementException();
         } else if (!this.valid) {
            throw new GdxRuntimeException("#iterator() cannot be used nested.");
         } else {
            long key = this.nextIndex == -1 ? 0L : this.map.keyTable[this.nextIndex];
            this.currentIndex = this.nextIndex;
            this.findNextIndex();
            return key;
         }
      }

      public LongArray toArray() {
         LongArray array = new LongArray(true, this.map.size);

         while (this.hasNext) {
            array.add(this.next());
         }

         return array;
      }
   }

   private static class MapIterator<V> {
      static final int INDEX_ILLEGAL = -2;
      static final int INDEX_ZERO = -1;
      public boolean hasNext;
      final LongMap<V> map;
      int nextIndex;
      int currentIndex;
      boolean valid = true;

      public MapIterator(LongMap<V> map) {
         this.map = map;
         this.reset();
      }

      public void reset() {
         this.currentIndex = -2;
         this.nextIndex = -1;
         if (this.map.hasZeroValue) {
            this.hasNext = true;
         } else {
            this.findNextIndex();
         }
      }

      void findNextIndex() {
         this.hasNext = false;
         long[] keyTable = this.map.keyTable;
         int n = this.map.capacity + this.map.stashSize;

         while (++this.nextIndex < n) {
            if (keyTable[this.nextIndex] != 0L) {
               this.hasNext = true;
               break;
            }
         }
      }

      public void remove() {
         if (this.currentIndex == -1 && this.map.hasZeroValue) {
            this.map.zeroValue = null;
            this.map.hasZeroValue = false;
         } else {
            if (this.currentIndex < 0) {
               throw new IllegalStateException("next must be called before remove.");
            }

            if (this.currentIndex >= this.map.capacity) {
               this.map.removeStashIndex(this.currentIndex);
               this.nextIndex = this.currentIndex - 1;
               this.findNextIndex();
            } else {
               this.map.keyTable[this.currentIndex] = 0L;
               this.map.valueTable[this.currentIndex] = null;
            }
         }

         this.currentIndex = -2;
         this.map.size--;
      }
   }

   public static class Values<V> extends LongMap.MapIterator<V> implements Iterable<V>, Iterator<V> {
      public Values(LongMap<V> map) {
         super(map);
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
      public V next() {
         if (!this.hasNext) {
            throw new NoSuchElementException();
         } else if (!this.valid) {
            throw new GdxRuntimeException("#iterator() cannot be used nested.");
         } else {
            V value;
            if (this.nextIndex == -1) {
               value = this.map.zeroValue;
            } else {
               value = this.map.valueTable[this.nextIndex];
            }

            this.currentIndex = this.nextIndex;
            this.findNextIndex();
            return value;
         }
      }

      @Override
      public Iterator<V> iterator() {
         return this;
      }

      public Array<V> toArray() {
         Array array = new Array(true, this.map.size);

         while (this.hasNext) {
            array.add(this.next());
         }

         return array;
      }

      @Override
      public void remove() {
         super.remove();
      }
   }
}
