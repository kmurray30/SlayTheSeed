package com.badlogic.gdx.utils;

import com.badlogic.gdx.math.MathUtils;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class IdentityMap<K, V> implements Iterable<IdentityMap.Entry<K, V>> {
   private static final int PRIME1 = -1105259343;
   private static final int PRIME2 = -1262997959;
   private static final int PRIME3 = -825114047;
   public int size;
   K[] keyTable;
   V[] valueTable;
   int capacity;
   int stashSize;
   private float loadFactor;
   private int hashShift;
   private int mask;
   private int threshold;
   private int stashCapacity;
   private int pushIterations;
   private IdentityMap.Entries entries1;
   private IdentityMap.Entries entries2;
   private IdentityMap.Values values1;
   private IdentityMap.Values values2;
   private IdentityMap.Keys keys1;
   private IdentityMap.Keys keys2;

   public IdentityMap() {
      this(51, 0.8F);
   }

   public IdentityMap(int initialCapacity) {
      this(initialCapacity, 0.8F);
   }

   public IdentityMap(int initialCapacity, float loadFactor) {
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
               this.keyTable = (K[])(new Object[this.capacity + this.stashCapacity]);
               this.valueTable = (V[])(new Object[this.keyTable.length]);
            }
         }
      }
   }

   public IdentityMap(IdentityMap map) {
      this((int)Math.floor(map.capacity * map.loadFactor), map.loadFactor);
      this.stashSize = map.stashSize;
      System.arraycopy(map.keyTable, 0, this.keyTable, 0, map.keyTable.length);
      System.arraycopy(map.valueTable, 0, this.valueTable, 0, map.valueTable.length);
      this.size = map.size;
   }

   public V put(K key, V value) {
      if (key == null) {
         throw new IllegalArgumentException("key cannot be null.");
      } else {
         K[] keyTable = this.keyTable;
         int hashCode = System.identityHashCode(key);
         int index1 = hashCode & this.mask;
         K key1 = keyTable[index1];
         if (key1 == key) {
            V oldValue = this.valueTable[index1];
            this.valueTable[index1] = value;
            return oldValue;
         } else {
            int index2 = this.hash2(hashCode);
            K key2 = keyTable[index2];
            if (key2 == key) {
               V oldValue = this.valueTable[index2];
               this.valueTable[index2] = value;
               return oldValue;
            } else {
               int index3 = this.hash3(hashCode);
               K key3 = keyTable[index3];
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

                  if (key1 == null) {
                     keyTable[index1] = key;
                     this.valueTable[index1] = value;
                     if (this.size++ >= this.threshold) {
                        this.resize(this.capacity << 1);
                     }

                     return null;
                  } else if (key2 == null) {
                     keyTable[index2] = key;
                     this.valueTable[index2] = value;
                     if (this.size++ >= this.threshold) {
                        this.resize(this.capacity << 1);
                     }

                     return null;
                  } else if (key3 == null) {
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

   private void putResize(K key, V value) {
      int hashCode = System.identityHashCode(key);
      int index1 = hashCode & this.mask;
      K key1 = this.keyTable[index1];
      if (key1 == null) {
         this.keyTable[index1] = key;
         this.valueTable[index1] = value;
         if (this.size++ >= this.threshold) {
            this.resize(this.capacity << 1);
         }
      } else {
         int index2 = this.hash2(hashCode);
         K key2 = this.keyTable[index2];
         if (key2 == null) {
            this.keyTable[index2] = key;
            this.valueTable[index2] = value;
            if (this.size++ >= this.threshold) {
               this.resize(this.capacity << 1);
            }
         } else {
            int index3 = this.hash3(hashCode);
            K key3 = this.keyTable[index3];
            if (key3 == null) {
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

   private void push(K insertKey, V insertValue, int index1, K key1, int index2, K key2, int index3, K key3) {
      K[] keyTable = this.keyTable;
      V[] valueTable = this.valueTable;
      int mask = this.mask;
      int i = 0;
      int pushIterations = this.pushIterations;

      while (true) {
         K evictedKey;
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

         int hashCode = System.identityHashCode(evictedKey);
         index1 = hashCode & mask;
         key1 = keyTable[index1];
         if (key1 == null) {
            keyTable[index1] = evictedKey;
            valueTable[index1] = evictedValue;
            if (this.size++ >= this.threshold) {
               this.resize(this.capacity << 1);
            }

            return;
         }

         index2 = this.hash2(hashCode);
         key2 = keyTable[index2];
         if (key2 == null) {
            keyTable[index2] = evictedKey;
            valueTable[index2] = evictedValue;
            if (this.size++ >= this.threshold) {
               this.resize(this.capacity << 1);
            }

            return;
         }

         index3 = this.hash3(hashCode);
         key3 = keyTable[index3];
         if (key3 == null) {
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

   private void putStash(K key, V value) {
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

   public V get(K key) {
      int hashCode = System.identityHashCode(key);
      int index = hashCode & this.mask;
      if (key != this.keyTable[index]) {
         index = this.hash2(hashCode);
         if (key != this.keyTable[index]) {
            index = this.hash3(hashCode);
            if (key != this.keyTable[index]) {
               return this.getStash(key, null);
            }
         }
      }

      return this.valueTable[index];
   }

   public V get(K key, V defaultValue) {
      int hashCode = System.identityHashCode(key);
      int index = hashCode & this.mask;
      if (key != this.keyTable[index]) {
         index = this.hash2(hashCode);
         if (key != this.keyTable[index]) {
            index = this.hash3(hashCode);
            if (key != this.keyTable[index]) {
               return this.getStash(key, defaultValue);
            }
         }
      }

      return this.valueTable[index];
   }

   private V getStash(K key, V defaultValue) {
      K[] keyTable = this.keyTable;
      int i = this.capacity;

      for (int n = i + this.stashSize; i < n; i++) {
         if (keyTable[i] == key) {
            return this.valueTable[i];
         }
      }

      return defaultValue;
   }

   public V remove(K key) {
      int hashCode = System.identityHashCode(key);
      int index = hashCode & this.mask;
      if (this.keyTable[index] == key) {
         this.keyTable[index] = null;
         V oldValue = this.valueTable[index];
         this.valueTable[index] = null;
         this.size--;
         return oldValue;
      } else {
         index = this.hash2(hashCode);
         if (this.keyTable[index] == key) {
            this.keyTable[index] = null;
            V oldValue = this.valueTable[index];
            this.valueTable[index] = null;
            this.size--;
            return oldValue;
         } else {
            index = this.hash3(hashCode);
            if (this.keyTable[index] == key) {
               this.keyTable[index] = null;
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

   V removeStash(K key) {
      K[] keyTable = this.keyTable;
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
         this.size = 0;
         this.resize(maximumCapacity);
      }
   }

   public void clear() {
      if (this.size != 0) {
         K[] keyTable = this.keyTable;
         V[] valueTable = this.valueTable;

         for (int i = this.capacity + this.stashSize; i-- > 0; valueTable[i] = null) {
            keyTable[i] = null;
         }

         this.size = 0;
         this.stashSize = 0;
      }
   }

   public boolean containsValue(Object value, boolean identity) {
      V[] valueTable = this.valueTable;
      if (value == null) {
         K[] keyTable = this.keyTable;
         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (keyTable[i] != null && valueTable[i] == null) {
               return true;
            }
         }
      } else if (identity) {
         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (valueTable[i] == value) {
               return true;
            }
         }
      } else {
         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (value.equals(valueTable[i])) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean containsKey(K key) {
      int hashCode = System.identityHashCode(key);
      int index = hashCode & this.mask;
      if (key != this.keyTable[index]) {
         index = this.hash2(hashCode);
         if (key != this.keyTable[index]) {
            index = this.hash3(hashCode);
            if (key != this.keyTable[index]) {
               return this.containsKeyStash(key);
            }
         }
      }

      return true;
   }

   private boolean containsKeyStash(K key) {
      K[] keyTable = this.keyTable;
      int i = this.capacity;

      for (int n = i + this.stashSize; i < n; i++) {
         if (keyTable[i] == key) {
            return true;
         }
      }

      return false;
   }

   public K findKey(Object value, boolean identity) {
      V[] valueTable = this.valueTable;
      if (value == null) {
         K[] keyTable = this.keyTable;
         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (keyTable[i] != null && valueTable[i] == null) {
               return keyTable[i];
            }
         }
      } else if (identity) {
         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (valueTable[i] == value) {
               return this.keyTable[i];
            }
         }
      } else {
         int i = this.capacity + this.stashSize;

         while (i-- > 0) {
            if (value.equals(valueTable[i])) {
               return this.keyTable[i];
            }
         }
      }

      return null;
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
      K[] oldKeyTable = this.keyTable;
      V[] oldValueTable = this.valueTable;
      this.keyTable = (K[])(new Object[newSize + this.stashCapacity]);
      this.valueTable = (V[])(new Object[newSize + this.stashCapacity]);
      int oldSize = this.size;
      this.size = 0;
      this.stashSize = 0;
      if (oldSize > 0) {
         for (int i = 0; i < oldEndIndex; i++) {
            K key = oldKeyTable[i];
            if (key != null) {
               this.putResize(key, oldValueTable[i]);
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
      K[] keyTable = this.keyTable;
      V[] valueTable = this.valueTable;
      int i = 0;

      for (int n = this.capacity + this.stashSize; i < n; i++) {
         K key = keyTable[i];
         if (key != null) {
            h += key.hashCode() * 31;
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
      } else if (!(obj instanceof IdentityMap)) {
         return false;
      } else {
         IdentityMap<K, V> other = (IdentityMap<K, V>)obj;
         if (other.size != this.size) {
            return false;
         } else {
            K[] keyTable = this.keyTable;
            V[] valueTable = this.valueTable;
            int i = 0;

            for (int n = this.capacity + this.stashSize; i < n; i++) {
               K key = keyTable[i];
               if (key != null) {
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
         K[] keyTable = this.keyTable;
         V[] valueTable = this.valueTable;
         int i = keyTable.length;

         while (i-- > 0) {
            K key = keyTable[i];
            if (key != null) {
               buffer.append(key);
               buffer.append('=');
               buffer.append(valueTable[i]);
               break;
            }
         }

         while (i-- > 0) {
            K key = keyTable[i];
            if (key != null) {
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
   public Iterator<IdentityMap.Entry<K, V>> iterator() {
      return this.entries();
   }

   public IdentityMap.Entries<K, V> entries() {
      if (this.entries1 == null) {
         this.entries1 = new IdentityMap.Entries<>(this);
         this.entries2 = new IdentityMap.Entries<>(this);
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

   public IdentityMap.Values<V> values() {
      if (this.values1 == null) {
         this.values1 = new IdentityMap.Values<>(this);
         this.values2 = new IdentityMap.Values<>(this);
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

   public IdentityMap.Keys<K> keys() {
      if (this.keys1 == null) {
         this.keys1 = new IdentityMap.Keys<>(this);
         this.keys2 = new IdentityMap.Keys<>(this);
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

   public static class Entries<K, V> extends IdentityMap.MapIterator<K, V, IdentityMap.Entry<K, V>> {
      private IdentityMap.Entry<K, V> entry = new IdentityMap.Entry<>();

      public Entries(IdentityMap<K, V> map) {
         super(map);
      }

      public IdentityMap.Entry<K, V> next() {
         if (!this.hasNext) {
            throw new NoSuchElementException();
         } else if (!this.valid) {
            throw new GdxRuntimeException("#iterator() cannot be used nested.");
         } else {
            K[] keyTable = this.map.keyTable;
            this.entry.key = keyTable[this.nextIndex];
            this.entry.value = this.map.valueTable[this.nextIndex];
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
      public Iterator<IdentityMap.Entry<K, V>> iterator() {
         return this;
      }
   }

   public static class Entry<K, V> {
      public K key;
      public V value;

      @Override
      public String toString() {
         return this.key + "=" + this.value;
      }
   }

   public static class Keys<K> extends IdentityMap.MapIterator<K, Object, K> {
      public Keys(IdentityMap<K, ?> map) {
         super((IdentityMap<K, Object>)map);
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
            K key = this.map.keyTable[this.nextIndex];
            this.currentIndex = this.nextIndex;
            this.findNextIndex();
            return key;
         }
      }

      @Override
      public Iterator<K> iterator() {
         return this;
      }

      public Array<K> toArray() {
         Array array = new Array(true, this.map.size);

         while (this.hasNext) {
            array.add(this.next());
         }

         return array;
      }
   }

   private abstract static class MapIterator<K, V, I> implements Iterable<I>, Iterator<I> {
      public boolean hasNext;
      final IdentityMap<K, V> map;
      int nextIndex;
      int currentIndex;
      boolean valid = true;

      public MapIterator(IdentityMap<K, V> map) {
         this.map = map;
         this.reset();
      }

      public void reset() {
         this.currentIndex = -1;
         this.nextIndex = -1;
         this.findNextIndex();
      }

      void findNextIndex() {
         this.hasNext = false;
         K[] keyTable = this.map.keyTable;
         int n = this.map.capacity + this.map.stashSize;

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
            if (this.currentIndex >= this.map.capacity) {
               this.map.removeStashIndex(this.currentIndex);
               this.nextIndex = this.currentIndex - 1;
               this.findNextIndex();
            } else {
               this.map.keyTable[this.currentIndex] = null;
               this.map.valueTable[this.currentIndex] = null;
            }

            this.currentIndex = -1;
            this.map.size--;
         }
      }
   }

   public static class Values<V> extends IdentityMap.MapIterator<Object, V, V> {
      public Values(IdentityMap<?, V> map) {
         super((IdentityMap<Object, V>)map);
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
            V value = this.map.valueTable[this.nextIndex];
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

      public void toArray(Array<V> array) {
         while (this.hasNext) {
            array.add(this.next());
         }
      }
   }
}
