package com.sun.jna;

public abstract class PointerType implements NativeMapped {
   private Pointer pointer;

   protected PointerType() {
      this.pointer = Pointer.NULL;
   }

   protected PointerType(Pointer p) {
      this.pointer = p;
   }

   @Override
   public Class<?> nativeType() {
      return Pointer.class;
   }

   @Override
   public Object toNative() {
      return this.getPointer();
   }

   public Pointer getPointer() {
      return this.pointer;
   }

   public void setPointer(Pointer p) {
      this.pointer = p;
   }

   @Override
   public Object fromNative(Object nativeValue, FromNativeContext context) {
      if (nativeValue == null) {
         return null;
      } else {
         try {
            PointerType pt = (PointerType)this.getClass().newInstance();
            pt.pointer = (Pointer)nativeValue;
            return pt;
         } catch (InstantiationException var4) {
            throw new IllegalArgumentException("Can't instantiate " + this.getClass());
         } catch (IllegalAccessException var5) {
            throw new IllegalArgumentException("Not allowed to instantiate " + this.getClass());
         }
      }
   }

   @Override
   public int hashCode() {
      return this.pointer != null ? this.pointer.hashCode() : 0;
   }

   @Override
   public boolean equals(Object o) {
      if (o == this) {
         return true;
      } else if (o instanceof PointerType) {
         Pointer p = ((PointerType)o).getPointer();
         return this.pointer == null ? p == null : this.pointer.equals(p);
      } else {
         return false;
      }
   }

   @Override
   public String toString() {
      return this.pointer == null ? "NULL" : this.pointer.toString() + " (" + super.toString() + ")";
   }
}
