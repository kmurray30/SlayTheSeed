package org.apache.logging.log4j.spi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.util.StringBuilderFormattable;

public class MutableThreadContextStack implements ThreadContextStack, StringBuilderFormattable {
   private static final long serialVersionUID = 50505011L;
   private final List<String> list;
   private boolean frozen;

   public MutableThreadContextStack() {
      this(new ArrayList<>());
   }

   public MutableThreadContextStack(final List<String> list) {
      this.list = new ArrayList<>(list);
   }

   private MutableThreadContextStack(final MutableThreadContextStack stack) {
      this.list = new ArrayList<>(stack.list);
   }

   private void checkInvariants() {
      if (this.frozen) {
         throw new UnsupportedOperationException("context stack has been frozen");
      }
   }

   @Override
   public String pop() {
      this.checkInvariants();
      if (this.list.isEmpty()) {
         return null;
      } else {
         int last = this.list.size() - 1;
         return this.list.remove(last);
      }
   }

   @Override
   public String peek() {
      if (this.list.isEmpty()) {
         return null;
      } else {
         int last = this.list.size() - 1;
         return this.list.get(last);
      }
   }

   @Override
   public void push(final String message) {
      this.checkInvariants();
      this.list.add(message);
   }

   @Override
   public int getDepth() {
      return this.list.size();
   }

   @Override
   public List<String> asList() {
      return this.list;
   }

   @Override
   public void trim(final int depth) {
      this.checkInvariants();
      if (depth < 0) {
         throw new IllegalArgumentException("Maximum stack depth cannot be negative");
      } else if (this.list != null) {
         List<String> copy = new ArrayList<>(this.list.size());
         int count = Math.min(depth, this.list.size());

         for (int i = 0; i < count; i++) {
            copy.add(this.list.get(i));
         }

         this.list.clear();
         this.list.addAll(copy);
      }
   }

   public ThreadContextStack copy() {
      return new MutableThreadContextStack(this);
   }

   @Override
   public void clear() {
      this.checkInvariants();
      this.list.clear();
   }

   @Override
   public int size() {
      return this.list.size();
   }

   @Override
   public boolean isEmpty() {
      return this.list.isEmpty();
   }

   @Override
   public boolean contains(final Object o) {
      return this.list.contains(o);
   }

   @Override
   public Iterator<String> iterator() {
      return this.list.iterator();
   }

   @Override
   public Object[] toArray() {
      return this.list.toArray();
   }

   @Override
   public <T> T[] toArray(final T[] ts) {
      return (T[])this.list.toArray(ts);
   }

   public boolean add(final String s) {
      this.checkInvariants();
      return this.list.add(s);
   }

   @Override
   public boolean remove(final Object o) {
      this.checkInvariants();
      return this.list.remove(o);
   }

   @Override
   public boolean containsAll(final Collection<?> objects) {
      return this.list.containsAll(objects);
   }

   @Override
   public boolean addAll(final Collection<? extends String> strings) {
      this.checkInvariants();
      return this.list.addAll(strings);
   }

   @Override
   public boolean removeAll(final Collection<?> objects) {
      this.checkInvariants();
      return this.list.removeAll(objects);
   }

   @Override
   public boolean retainAll(final Collection<?> objects) {
      this.checkInvariants();
      return this.list.retainAll(objects);
   }

   @Override
   public String toString() {
      return String.valueOf(this.list);
   }

   @Override
   public void formatTo(final StringBuilder buffer) {
      buffer.append('[');

      for (int i = 0; i < this.list.size(); i++) {
         if (i > 0) {
            buffer.append(',').append(' ');
         }

         buffer.append(this.list.get(i));
      }

      buffer.append(']');
   }

   @Override
   public int hashCode() {
      return 31 + Objects.hashCode(this.list);
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (!(obj instanceof ThreadContextStack)) {
         return false;
      } else {
         ThreadContextStack other = (ThreadContextStack)obj;
         List<String> otherAsList = other.asList();
         return Objects.equals(this.list, otherAsList);
      }
   }

   @Override
   public ThreadContext.ContextStack getImmutableStackOrNull() {
      return this.copy();
   }

   public void freeze() {
      this.frozen = true;
   }

   public boolean isFrozen() {
      return this.frozen;
   }
}
