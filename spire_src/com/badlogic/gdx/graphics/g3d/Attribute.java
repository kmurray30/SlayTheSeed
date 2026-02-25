package com.badlogic.gdx.graphics.g3d;

import com.badlogic.gdx.utils.Array;

public abstract class Attribute implements Comparable<Attribute> {
   private static final Array<String> types = new Array<>();
   public final long type;
   private final int typeBit;

   public static final long getAttributeType(String alias) {
      for (int i = 0; i < types.size; i++) {
         if (types.get(i).compareTo(alias) == 0) {
            return 1L << i;
         }
      }

      return 0L;
   }

   public static final String getAttributeAlias(long type) {
      int idx = -1;

      while (type != 0L) {
         idx++;
         if (idx >= 63 || (type >> idx & 1L) != 0L) {
            break;
         }
      }

      return idx >= 0 && idx < types.size ? types.get(idx) : null;
   }

   protected static final long register(String alias) {
      long result = getAttributeType(alias);
      if (result > 0L) {
         return result;
      } else {
         types.add(alias);
         return 1L << types.size - 1;
      }
   }

   protected Attribute(long type) {
      this.type = type;
      this.typeBit = Long.numberOfTrailingZeros(type);
   }

   public abstract Attribute copy();

   protected boolean equals(Attribute other) {
      return other.hashCode() == this.hashCode();
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == null) {
         return false;
      } else if (obj == this) {
         return true;
      } else if (!(obj instanceof Attribute)) {
         return false;
      } else {
         Attribute other = (Attribute)obj;
         return this.type != other.type ? false : this.equals(other);
      }
   }

   @Override
   public String toString() {
      return getAttributeAlias(this.type);
   }

   @Override
   public int hashCode() {
      return 7489 * this.typeBit;
   }
}
