package com.badlogic.gdx.graphics.g3d.environment;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class SphericalHarmonics {
   private static final float[] coeff = new float[]{0.282095F, 0.488603F, 0.488603F, 0.488603F, 1.092548F, 1.092548F, 1.092548F, 0.315392F, 0.546274F};
   public final float[] data;

   private static final float clamp(float v) {
      return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
   }

   public SphericalHarmonics() {
      this.data = new float[27];
   }

   public SphericalHarmonics(float[] copyFrom) {
      if (copyFrom.length != 27) {
         throw new GdxRuntimeException("Incorrect array size");
      } else {
         this.data = (float[])copyFrom.clone();
      }
   }

   public SphericalHarmonics set(float[] values) {
      for (int i = 0; i < this.data.length; i++) {
         this.data[i] = values[i];
      }

      return this;
   }

   public SphericalHarmonics set(AmbientCubemap other) {
      return this.set(other.data);
   }

   public SphericalHarmonics set(Color color) {
      return this.set(color.r, color.g, color.b);
   }

   public SphericalHarmonics set(float r, float g, float b) {
      int idx = 0;

      while (idx < this.data.length) {
         this.data[idx++] = r;
         this.data[idx++] = g;
         this.data[idx++] = b;
      }

      return this;
   }
}
