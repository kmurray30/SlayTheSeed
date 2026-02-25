package com.badlogic.gdx.graphics.g3d.attributes;

import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.NumberUtils;

public class DepthTestAttribute extends Attribute {
   public static final String Alias = "depthStencil";
   public static final long Type = register("depthStencil");
   protected static long Mask = Type;
   public int depthFunc;
   public float depthRangeNear;
   public float depthRangeFar;
   public boolean depthMask;

   public static final boolean is(long mask) {
      return (mask & Mask) != 0L;
   }

   public DepthTestAttribute() {
      this(515);
   }

   public DepthTestAttribute(boolean depthMask) {
      this(515, depthMask);
   }

   public DepthTestAttribute(int depthFunc) {
      this(depthFunc, true);
   }

   public DepthTestAttribute(int depthFunc, boolean depthMask) {
      this(depthFunc, 0.0F, 1.0F, depthMask);
   }

   public DepthTestAttribute(int depthFunc, float depthRangeNear, float depthRangeFar) {
      this(depthFunc, depthRangeNear, depthRangeFar, true);
   }

   public DepthTestAttribute(int depthFunc, float depthRangeNear, float depthRangeFar, boolean depthMask) {
      this(Type, depthFunc, depthRangeNear, depthRangeFar, depthMask);
   }

   public DepthTestAttribute(long type, int depthFunc, float depthRangeNear, float depthRangeFar, boolean depthMask) {
      super(type);
      if (!is(type)) {
         throw new GdxRuntimeException("Invalid type specified");
      } else {
         this.depthFunc = depthFunc;
         this.depthRangeNear = depthRangeNear;
         this.depthRangeFar = depthRangeFar;
         this.depthMask = depthMask;
      }
   }

   public DepthTestAttribute(DepthTestAttribute rhs) {
      this(rhs.type, rhs.depthFunc, rhs.depthRangeNear, rhs.depthRangeFar, rhs.depthMask);
   }

   @Override
   public Attribute copy() {
      return new DepthTestAttribute(this);
   }

   @Override
   public int hashCode() {
      int result = super.hashCode();
      result = 971 * result + this.depthFunc;
      result = 971 * result + NumberUtils.floatToRawIntBits(this.depthRangeNear);
      result = 971 * result + NumberUtils.floatToRawIntBits(this.depthRangeFar);
      return 971 * result + (this.depthMask ? 1 : 0);
   }

   public int compareTo(Attribute o) {
      if (this.type != o.type) {
         return (int)(this.type - o.type);
      } else {
         DepthTestAttribute other = (DepthTestAttribute)o;
         if (this.depthFunc != other.depthFunc) {
            return this.depthFunc - other.depthFunc;
         } else if (this.depthMask != other.depthMask) {
            return this.depthMask ? -1 : 1;
         } else if (!MathUtils.isEqual(this.depthRangeNear, other.depthRangeNear)) {
            return this.depthRangeNear < other.depthRangeNear ? -1 : 1;
         } else if (!MathUtils.isEqual(this.depthRangeFar, other.depthRangeFar)) {
            return this.depthRangeFar < other.depthRangeFar ? -1 : 1;
         } else {
            return 0;
         }
      }
   }
}
