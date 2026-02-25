package com.badlogic.gdx.utils;

import com.badlogic.gdx.utils.reflect.ArrayReflection;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class Queue<T> implements Iterable<T> {
   protected T[] values;
   protected int head = 0;
   protected int tail = 0;
   public int size = 0;
   private Queue.QueueIterable iterable;

   public Queue() {
      this(16);
   }

   public Queue(int initialSize) {
      this.values = (T[])(new Object[initialSize]);
   }

   public Queue(int initialSize, Class<T> type) {
      this.values = (T[])((Object[])ArrayReflection.newInstance(type, initialSize));
   }

   public void addLast(T object) {
      T[] values = this.values;
      if (this.size == values.length) {
         this.resize(values.length << 1);
         values = this.values;
      }

      values[this.tail++] = object;
      if (this.tail == values.length) {
         this.tail = 0;
      }

      this.size++;
   }

   public void addFirst(T object) {
      T[] values = this.values;
      if (this.size == values.length) {
         this.resize(values.length << 1);
         values = this.values;
      }

      int head = this.head;
      if (--head == -1) {
         head = values.length - 1;
      }

      values[head] = object;
      this.head = head;
      this.size++;
   }

   public void ensureCapacity(int additional) {
      int needed = this.size + additional;
      if (this.values.length < needed) {
         this.resize(needed);
      }
   }

   protected void resize(int newSize) {
      T[] values = this.values;
      int head = this.head;
      int tail = this.tail;
      T[] newArray = (T[])((Object[])ArrayReflection.newInstance(values.getClass().getComponentType(), newSize));
      if (head < tail) {
         System.arraycopy(values, head, newArray, 0, tail - head);
      } else if (this.size > 0) {
         int rest = values.length - head;
         System.arraycopy(values, head, newArray, 0, rest);
         System.arraycopy(values, 0, newArray, rest, tail);
      }

      this.values = newArray;
      this.head = 0;
      this.tail = this.size;
   }

   public T removeFirst() {
      if (this.size == 0) {
         throw new NoSuchElementException("Queue is empty.");
      } else {
         T[] values = this.values;
         T result = values[this.head];
         values[this.head] = null;
         this.head++;
         if (this.head == values.length) {
            this.head = 0;
         }

         this.size--;
         return result;
      }
   }

   public T removeLast() {
      if (this.size == 0) {
         throw new NoSuchElementException("Queue is empty.");
      } else {
         T[] values = this.values;
         int tail = this.tail;
         if (--tail == -1) {
            tail = values.length - 1;
         }

         T result = values[tail];
         values[tail] = null;
         this.tail = tail;
         this.size--;
         return result;
      }
   }

   public int indexOf(T value, boolean identity) {
      if (this.size == 0) {
         return -1;
      } else {
         T[] values = this.values;
         int head = this.head;
         int tail = this.tail;
         if (!identity && value != null) {
            if (head < tail) {
               for (int i = head; i < tail; i++) {
                  if (value.equals(values[i])) {
                     return i;
                  }
               }
            } else {
               int ix = head;

               for (int n = values.length; ix < n; ix++) {
                  if (value.equals(values[ix])) {
                     return ix - head;
                  }
               }

               for (int ixx = 0; ixx < tail; ixx++) {
                  if (value.equals(values[ixx])) {
                     return ixx + values.length - head;
                  }
               }
            }
         } else if (head < tail) {
            for (int ix = head; ix < tail; ix++) {
               if (values[ix] == value) {
                  return ix;
               }
            }
         } else {
            int ixxx = head;

            for (int nx = values.length; ixxx < nx; ixxx++) {
               if (values[ixxx] == value) {
                  return ixxx - head;
               }
            }

            for (int ixxxx = 0; ixxxx < tail; ixxxx++) {
               if (values[ixxxx] == value) {
                  return ixxxx + values.length - head;
               }
            }
         }

         return -1;
      }
   }

   public boolean removeValue(T value, boolean identity) {
      int index = this.indexOf(value, identity);
      if (index == -1) {
         return false;
      } else {
         this.removeIndex(index);
         return true;
      }
   }

   public T removeIndex(int index) {
      if (index < 0) {
         throw new IndexOutOfBoundsException("index can't be < 0: " + index);
      } else if (index >= this.size) {
         throw new IndexOutOfBoundsException("index can't be >= size: " + index + " >= " + this.size);
      } else {
         T[] values = this.values;
         int head = this.head;
         int tail = this.tail;
         index += head;
         T value;
         if (head < tail) {
            value = values[index];
            System.arraycopy(values, index + 1, values, index, tail - index);
            values[tail] = null;
            this.tail--;
         } else if (index >= values.length) {
            index -= values.length;
            value = values[index];
            System.arraycopy(values, index + 1, values, index, tail - index);
            this.tail--;
         } else {
            value = values[index];
            System.arraycopy(values, head, values, head + 1, index - head);
            values[head] = null;
            this.head++;
            if (this.head == values.length) {
               this.head = 0;
            }
         }

         this.size--;
         return value;
      }
   }

   public T first() {
      if (this.size == 0) {
         throw new NoSuchElementException("Queue is empty.");
      } else {
         return this.values[this.head];
      }
   }

   public T last() {
      if (this.size == 0) {
         throw new NoSuchElementException("Queue is empty.");
      } else {
         T[] values = this.values;
         int tail = this.tail;
         if (--tail == -1) {
            tail = values.length - 1;
         }

         return values[tail];
      }
   }

   public T get(int index) {
      if (index < 0) {
         throw new IndexOutOfBoundsException("index can't be < 0: " + index);
      } else if (index >= this.size) {
         throw new IndexOutOfBoundsException("index can't be >= size: " + index + " >= " + this.size);
      } else {
         T[] values = this.values;
         int i = this.head + index;
         if (i >= values.length) {
            i -= values.length;
         }

         return values[i];
      }
   }

   public void clear() {
      if (this.size != 0) {
         T[] values = this.values;
         int head = this.head;
         int tail = this.tail;
         if (head < tail) {
            for (int i = head; i < tail; i++) {
               values[i] = null;
            }
         } else {
            for (int i = head; i < values.length; i++) {
               values[i] = null;
            }

            for (int i = 0; i < tail; i++) {
               values[i] = null;
            }
         }

         this.head = 0;
         this.tail = 0;
         this.size = 0;
      }
   }

   @Override
   public Iterator<T> iterator() {
      if (this.iterable == null) {
         this.iterable = new Queue.QueueIterable<>(this);
      }

      return this.iterable.iterator();
   }

   @Override
   public String toString() {
      if (this.size == 0) {
         return "[]";
      } else {
         T[] values = this.values;
         int head = this.head;
         int tail = this.tail;
         StringBuilder sb = new StringBuilder(64);
         sb.append('[');
         sb.append(values[head]);

         for (int i = (head + 1) % values.length; i != tail; i = (i + 1) % values.length) {
            sb.append(", ").append(values[i]);
         }

         sb.append(']');
         return sb.toString();
      }
   }

   @Override
   public int hashCode() {
      int size = this.size;
      T[] values = this.values;
      int backingLength = values.length;
      int index = this.head;
      int hash = size + 1;

      for (int s = 0; s < size; s++) {
         T value = values[index];
         hash *= 31;
         if (value != null) {
            hash += value.hashCode();
         }

         if (++index == backingLength) {
            index = 0;
         }
      }

      return hash;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && o instanceof Queue) {
         Queue<?> q = (Queue<?>)o;
         int size = this.size;
         if (q.size != size) {
            return false;
         } else {
            T[] myValues = this.values;
            int myBackingLength = myValues.length;
            Object[] itsValues = q.values;
            int itsBackingLength = itsValues.length;
            int myIndex = this.head;
            int itsIndex = q.head;

            for (int s = 0; s < size; s++) {
               T myValue = myValues[myIndex];
               Object itsValue = itsValues[itsIndex];
               if (myValue == null ? itsValue != null : !myValue.equals(itsValue)) {
                  return false;
               }

               myIndex++;
               itsIndex++;
               if (myIndex == myBackingLength) {
                  myIndex = 0;
               }

               if (itsIndex == itsBackingLength) {
                  itsIndex = 0;
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public static class QueueIterable<T> implements Iterable<T> {
      private final Queue<T> queue;
      private final boolean allowRemove;
      private Queue.QueueIterator iterator1;
      private Queue.QueueIterator iterator2;

      public QueueIterable(Queue<T> queue) {
         this(queue, true);
      }

      public QueueIterable(Queue<T> queue, boolean allowRemove) {
         this.queue = queue;
         this.allowRemove = allowRemove;
      }

      @Override
      public Iterator<T> iterator() {
         if (this.iterator1 == null) {
            this.iterator1 = new Queue.QueueIterator<>(this.queue, this.allowRemove);
            this.iterator2 = new Queue.QueueIterator<>(this.queue, this.allowRemove);
         }

         if (!this.iterator1.valid) {
            this.iterator1.index = 0;
            this.iterator1.valid = true;
            this.iterator2.valid = false;
            return this.iterator1;
         } else {
            this.iterator2.index = 0;
            this.iterator2.valid = true;
            this.iterator1.valid = false;
            return this.iterator2;
         }
      }
   }

   public static class QueueIterator<T> implements Iterator<T>, Iterable<T> {
      private final Queue<T> queue;
      private final boolean allowRemove;
      int index;
      boolean valid = true;

      public QueueIterator(Queue<T> queue) {
         this(queue, true);
      }

      public QueueIterator(Queue<T> queue, boolean allowRemove) {
         this.queue = queue;
         this.allowRemove = allowRemove;
      }

      @Override
      public boolean hasNext() {
         if (!this.valid) {
            throw new GdxRuntimeException("#iterator() cannot be used nested.");
         } else {
            return this.index < this.queue.size;
         }
      }

      @Override
      public T next() {
         if (this.index >= this.queue.size) {
            throw new NoSuchElementException(String.valueOf(this.index));
         } else if (!this.valid) {
            throw new GdxRuntimeException("#iterator() cannot be used nested.");
         } else {
            return this.queue.get(this.index++);
         }
      }

      @Override
      public void remove() {
         if (!this.allowRemove) {
            throw new GdxRuntimeException("Remove not allowed.");
         } else {
            this.index--;
            this.queue.removeIndex(this.index);
         }
      }

      public void reset() {
         this.index = 0;
      }

      @Override
      public Iterator<T> iterator() {
         return this;
      }
   }
}
