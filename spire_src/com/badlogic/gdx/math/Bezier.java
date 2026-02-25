package com.badlogic.gdx.math;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class Bezier<T extends Vector<T>> implements Path<T> {
   public Array<T> points = new Array<>();
   private T tmp;
   private T tmp2;
   private T tmp3;

   public static <T extends Vector<T>> T linear(T out, float t, T p0, T p1, T tmp) {
      return out.set(p0).scl(1.0F - t).add(tmp.set(p1).scl(t));
   }

   public static <T extends Vector<T>> T linear_derivative(T out, float t, T p0, T p1, T tmp) {
      return out.set(p1).sub(p0);
   }

   public static <T extends Vector<T>> T quadratic(T out, float t, T p0, T p1, T p2, T tmp) {
      float dt = 1.0F - t;
      return out.set(p0).scl(dt * dt).add(tmp.set(p1).scl(2.0F * dt * t)).add(tmp.set(p2).scl(t * t));
   }

   public static <T extends Vector<T>> T quadratic_derivative(T out, float t, T p0, T p1, T p2, T tmp) {
      float dt = 1.0F - t;
      return out.set(p1).sub(p0).scl(2.0F).scl(1.0F - t).add(tmp.set(p2).sub(p1).scl(t).scl(2.0F));
   }

   public static <T extends Vector<T>> T cubic(T out, float t, T p0, T p1, T p2, T p3, T tmp) {
      float dt = 1.0F - t;
      float dt2 = dt * dt;
      float t2 = t * t;
      return out.set(p0).scl(dt2 * dt).add(tmp.set(p1).scl(3.0F * dt2 * t)).add(tmp.set(p2).scl(3.0F * dt * t2)).add(tmp.set(p3).scl(t2 * t));
   }

   public static <T extends Vector<T>> T cubic_derivative(T out, float t, T p0, T p1, T p2, T p3, T tmp) {
      float dt = 1.0F - t;
      float dt2 = dt * dt;
      float t2 = t * t;
      return out.set(p1).sub(p0).scl(dt2 * 3.0F).add(tmp.set(p2).sub(p1).scl(dt * t * 6.0F)).add(tmp.set(p3).sub(p2).scl(t2 * 3.0F));
   }

   public Bezier() {
   }

   public Bezier(T... points) {
      this.set(points);
   }

   public Bezier(T[] points, int offset, int length) {
      this.set(points, offset, length);
   }

   public Bezier(Array<T> points, int offset, int length) {
      this.set(points, offset, length);
   }

   public Bezier set(T... points) {
      return this.set(points, 0, points.length);
   }

   public Bezier set(T[] points, int offset, int length) {
      if (length >= 2 && length <= 4) {
         if (this.tmp == null) {
            this.tmp = points[0].cpy();
         }

         if (this.tmp2 == null) {
            this.tmp2 = points[0].cpy();
         }

         if (this.tmp3 == null) {
            this.tmp3 = points[0].cpy();
         }

         this.points.clear();
         this.points.addAll(points, offset, length);
         return this;
      } else {
         throw new GdxRuntimeException("Only first, second and third degree Bezier curves are supported.");
      }
   }

   public Bezier set(Array<T> points, int offset, int length) {
      if (length >= 2 && length <= 4) {
         if (this.tmp == null) {
            this.tmp = points.get(0).cpy();
         }

         this.points.clear();
         this.points.addAll(points, offset, length);
         return this;
      } else {
         throw new GdxRuntimeException("Only first, second and third degree Bezier curves are supported.");
      }
   }

   public T valueAt(T out, float t) {
      int n = this.points.size;
      if (n == 2) {
         linear(out, t, this.points.get(0), this.points.get(1), this.tmp);
      } else if (n == 3) {
         quadratic(out, t, this.points.get(0), this.points.get(1), this.points.get(2), this.tmp);
      } else if (n == 4) {
         cubic(out, t, this.points.get(0), this.points.get(1), this.points.get(2), this.points.get(3), this.tmp);
      }

      return out;
   }

   public T derivativeAt(T out, float t) {
      int n = this.points.size;
      if (n == 2) {
         linear_derivative(out, t, this.points.get(0), this.points.get(1), this.tmp);
      } else if (n == 3) {
         quadratic_derivative(out, t, this.points.get(0), this.points.get(1), this.points.get(2), this.tmp);
      } else if (n == 4) {
         cubic_derivative(out, t, this.points.get(0), this.points.get(1), this.points.get(2), this.points.get(3), this.tmp);
      }

      return out;
   }

   public float approximate(T v) {
      T p1 = this.points.get(0);
      T p2 = this.points.get(this.points.size - 1);
      float l1Sqr = p1.dst2(p2);
      float l2Sqr = v.dst2(p2);
      float l3Sqr = v.dst2(p1);
      float l1 = (float)Math.sqrt(l1Sqr);
      float s = (l2Sqr + l1Sqr - l3Sqr) / (2.0F * l1);
      return MathUtils.clamp((l1 - s) / l1, 0.0F, 1.0F);
   }

   public float locate(T v) {
      return this.approximate(v);
   }

   @Override
   public float approxLength(int samples) {
      float tempLength = 0.0F;

      for (int i = 0; i < samples; i++) {
         this.tmp2.set(this.tmp3);
         this.valueAt(this.tmp3, i / (samples - 1.0F));
         if (i > 0) {
            tempLength += this.tmp2.dst(this.tmp3);
         }
      }

      return tempLength;
   }
}
