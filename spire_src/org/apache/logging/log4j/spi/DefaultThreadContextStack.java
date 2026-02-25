package org.apache.logging.log4j.spi;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.apache.logging.log4j.util.StringBuilders;
import org.apache.logging.log4j.util.Strings;

public class DefaultThreadContextStack implements ThreadContextStack, StringBuilderFormattable {
   private static final long serialVersionUID = 5050501L;
   private static final ThreadLocal<MutableThreadContextStack> STACK = new ThreadLocal<>();
   private final boolean useStack;

   public DefaultThreadContextStack(final boolean useStack) {
      this.useStack = useStack;
   }

   private MutableThreadContextStack getNonNullStackCopy() {
      MutableThreadContextStack values = STACK.get();
      return (MutableThreadContextStack)(values == null ? new MutableThreadContextStack() : values.copy());
   }

   public boolean add(final String s) {
      if (!this.useStack) {
         return false;
      } else {
         MutableThreadContextStack copy = this.getNonNullStackCopy();
         copy.add(s);
         copy.freeze();
         STACK.set(copy);
         return true;
      }
   }

   @Override
   public boolean addAll(final Collection<? extends String> strings) {
      if (this.useStack && !strings.isEmpty()) {
         MutableThreadContextStack copy = this.getNonNullStackCopy();
         copy.addAll(strings);
         copy.freeze();
         STACK.set(copy);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public List<String> asList() {
      MutableThreadContextStack values = STACK.get();
      return values == null ? Collections.emptyList() : values.asList();
   }

   @Override
   public void clear() {
      STACK.remove();
   }

   @Override
   public boolean contains(final Object o) {
      MutableThreadContextStack values = STACK.get();
      return values != null && values.contains(o);
   }

   @Override
   public boolean containsAll(final Collection<?> objects) {
      if (objects.isEmpty()) {
         return true;
      } else {
         MutableThreadContextStack values = STACK.get();
         return values != null && values.containsAll(objects);
      }
   }

   public ThreadContextStack copy() {
      MutableThreadContextStack values = null;
      return (ThreadContextStack)(this.useStack && (values = STACK.get()) != null ? values.copy() : new MutableThreadContextStack());
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else {
         if (obj instanceof DefaultThreadContextStack) {
            DefaultThreadContextStack other = (DefaultThreadContextStack)obj;
            if (this.useStack != other.useStack) {
               return false;
            }
         }

         if (!(obj instanceof ThreadContextStack)) {
            return false;
         } else {
            ThreadContextStack other = (ThreadContextStack)obj;
            MutableThreadContextStack values = STACK.get();
            return values == null ? false : values.equals(other);
         }
      }
   }

   @Override
   public int getDepth() {
      MutableThreadContextStack values = STACK.get();
      return values == null ? 0 : values.getDepth();
   }

   @Override
   public int hashCode() {
      MutableThreadContextStack values = STACK.get();
      int prime = 31;
      int result = 1;
      return 31 * result + (values == null ? 0 : values.hashCode());
   }

   @Override
   public boolean isEmpty() {
      MutableThreadContextStack values = STACK.get();
      return values == null || values.isEmpty();
   }

   @Override
   public Iterator<String> iterator() {
      MutableThreadContextStack values = STACK.get();
      if (values == null) {
         List<String> empty = Collections.emptyList();
         return empty.iterator();
      } else {
         return values.iterator();
      }
   }

   @Override
   public String peek() {
      MutableThreadContextStack values = STACK.get();
      return values != null && !values.isEmpty() ? values.peek() : "";
   }

   @Override
   public String pop() {
      if (!this.useStack) {
         return "";
      } else {
         MutableThreadContextStack values = STACK.get();
         if (values != null && !values.isEmpty()) {
            MutableThreadContextStack copy = (MutableThreadContextStack)values.copy();
            String result = copy.pop();
            copy.freeze();
            STACK.set(copy);
            return result;
         } else {
            return "";
         }
      }
   }

   @Override
   public void push(final String message) {
      if (this.useStack) {
         this.add(message);
      }
   }

   @Override
   public boolean remove(final Object o) {
      if (!this.useStack) {
         return false;
      } else {
         MutableThreadContextStack values = STACK.get();
         if (values != null && !values.isEmpty()) {
            MutableThreadContextStack copy = (MutableThreadContextStack)values.copy();
            boolean result = copy.remove(o);
            copy.freeze();
            STACK.set(copy);
            return result;
         } else {
            return false;
         }
      }
   }

   @Override
   public boolean removeAll(final Collection<?> objects) {
      if (this.useStack && !objects.isEmpty()) {
         MutableThreadContextStack values = STACK.get();
         if (values != null && !values.isEmpty()) {
            MutableThreadContextStack copy = (MutableThreadContextStack)values.copy();
            boolean result = copy.removeAll(objects);
            copy.freeze();
            STACK.set(copy);
            return result;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean retainAll(final Collection<?> objects) {
      if (this.useStack && !objects.isEmpty()) {
         MutableThreadContextStack values = STACK.get();
         if (values != null && !values.isEmpty()) {
            MutableThreadContextStack copy = (MutableThreadContextStack)values.copy();
            boolean result = copy.retainAll(objects);
            copy.freeze();
            STACK.set(copy);
            return result;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public int size() {
      MutableThreadContextStack values = STACK.get();
      return values == null ? 0 : values.size();
   }

   @Override
   public Object[] toArray() {
      MutableThreadContextStack result = STACK.get();
      return (Object[])(result == null ? Strings.EMPTY_ARRAY : result.toArray(new Object[result.size()]));
   }

   @Override
   public <T> T[] toArray(final T[] ts) {
      MutableThreadContextStack result = STACK.get();
      if (result == null) {
         if (ts.length > 0) {
            ts[0] = null;
         }

         return ts;
      } else {
         return (T[])result.toArray(ts);
      }
   }

   @Override
   public String toString() {
      MutableThreadContextStack values = STACK.get();
      return values == null ? "[]" : values.toString();
   }

   @Override
   public void formatTo(final StringBuilder buffer) {
      MutableThreadContextStack values = STACK.get();
      if (values == null) {
         buffer.append("[]");
      } else {
         StringBuilders.appendValue(buffer, values);
      }
   }

   @Override
   public void trim(final int depth) {
      if (depth < 0) {
         throw new IllegalArgumentException("Maximum stack depth cannot be negative");
      } else {
         MutableThreadContextStack values = STACK.get();
         if (values != null) {
            MutableThreadContextStack copy = (MutableThreadContextStack)values.copy();
            copy.trim(depth);
            copy.freeze();
            STACK.set(copy);
         }
      }
   }

   @Override
   public ThreadContext.ContextStack getImmutableStackOrNull() {
      return STACK.get();
   }
}
