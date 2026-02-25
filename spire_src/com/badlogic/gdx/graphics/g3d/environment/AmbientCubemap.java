package com.badlogic.gdx.graphics.g3d.environment;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class AmbientCubemap {
   public final float[] data;

   private static final float clamp(float v) {
      return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
   }

   public AmbientCubemap() {
      this.data = new float[18];
   }

   public AmbientCubemap(float[] copyFrom) {
      if (copyFrom.length != 18) {
         throw new GdxRuntimeException("Incorrect array size");
      } else {
         this.data = new float[copyFrom.length];
         System.arraycopy(copyFrom, 0, this.data, 0, this.data.length);
      }
   }

   public AmbientCubemap(AmbientCubemap copyFrom) {
      this(copyFrom.data);
   }

   public AmbientCubemap set(float[] values) {
      for (int i = 0; i < this.data.length; i++) {
         this.data[i] = values[i];
      }

      return this;
   }

   public AmbientCubemap set(AmbientCubemap other) {
      return this.set(other.data);
   }

   public AmbientCubemap set(Color color) {
      return this.set(color.r, color.g, color.b);
   }

   public AmbientCubemap set(float r, float g, float b) {
      int idx = 0;

      while (idx < this.data.length) {
         this.data[idx++] = r;
         this.data[idx++] = g;
         this.data[idx++] = b;
      }

      return this;
   }

   public Color getColor(Color out, int side) {
      side *= 3;
      return out.set(this.data[side], this.data[side + 1], this.data[side + 2], 1.0F);
   }

   public AmbientCubemap clear() {
      for (int i = 0; i < this.data.length; i++) {
         this.data[i] = 0.0F;
      }

      return this;
   }

   public AmbientCubemap clamp() {
      for (int i = 0; i < this.data.length; i++) {
         this.data[i] = clamp(this.data[i]);
      }

      return this;
   }

   public AmbientCubemap add(float r, float g, float b) {
      int idx = 0;

      while (idx < this.data.length) {
         int var10001 = idx++;
         this.data[var10001] = this.data[var10001] + r;
         var10001 = idx++;
         this.data[var10001] = this.data[var10001] + g;
         var10001 = idx++;
         this.data[var10001] = this.data[var10001] + b;
      }

      return this;
   }

   public AmbientCubemap add(Color color) {
      return this.add(color.r, color.g, color.b);
   }

   public AmbientCubemap add(float r, float g, float b, float x, float y, float z) {
      float x2 = x * x;
      float y2 = y * y;
      float z2 = z * z;
      float d = x2 + y2 + z2;
      if (d == 0.0F) {
         return this;
      } else {
         d = 1.0F / d * (d + 1.0F);
         float rd = r * d;
         float gd = g * d;
         float bd = b * d;
         int idx = x > 0.0F ? 0 : 3;
         this.data[idx] = this.data[idx] + x2 * rd;
         this.data[idx + 1] = this.data[idx + 1] + x2 * gd;
         this.data[idx + 2] = this.data[idx + 2] + x2 * bd;
         idx = y > 0.0F ? 6 : 9;
         this.data[idx] = this.data[idx] + y2 * rd;
         this.data[idx + 1] = this.data[idx + 1] + y2 * gd;
         this.data[idx + 2] = this.data[idx + 2] + y2 * bd;
         idx = z > 0.0F ? 12 : 15;
         this.data[idx] = this.data[idx] + z2 * rd;
         this.data[idx + 1] = this.data[idx + 1] + z2 * gd;
         this.data[idx + 2] = this.data[idx + 2] + z2 * bd;
         return this;
      }
   }

   public AmbientCubemap add(Color color, Vector3 direction) {
      return this.add(color.r, color.g, color.b, direction.x, direction.y, direction.z);
   }

   public AmbientCubemap add(float r, float g, float b, Vector3 direction) {
      return this.add(r, g, b, direction.x, direction.y, direction.z);
   }

   public AmbientCubemap add(Color color, float x, float y, float z) {
      return this.add(color.r, color.g, color.b, x, y, z);
   }

   public AmbientCubemap add(Color color, Vector3 point, Vector3 target) {
      return this.add(color.r, color.g, color.b, target.x - point.x, target.y - point.y, target.z - point.z);
   }

   public AmbientCubemap add(Color color, Vector3 point, Vector3 target, float intensity) {
      float t = intensity / (1.0F + target.dst(point));
      return this.add(color.r * t, color.g * t, color.b * t, target.x - point.x, target.y - point.y, target.z - point.z);
   }

   @Override
   public String toString() {
      String result = "";

      for (int i = 0; i < this.data.length; i += 3) {
         result = result + Float.toString(this.data[i]) + ", " + Float.toString(this.data[i + 1]) + ", " + Float.toString(this.data[i + 2]) + "\n";
      }

      return result;
   }
}
