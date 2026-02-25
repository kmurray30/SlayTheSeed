package com.badlogic.gdx.graphics.g3d.particles.values;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public final class RectangleSpawnShapeValue extends PrimitiveSpawnShapeValue {
   public RectangleSpawnShapeValue(RectangleSpawnShapeValue value) {
      super(value);
      this.load(value);
   }

   public RectangleSpawnShapeValue() {
   }

   @Override
   public void spawnAux(Vector3 vector, float percent) {
      float width = this.spawnWidth + this.spawnWidthDiff * this.spawnWidthValue.getScale(percent);
      float height = this.spawnHeight + this.spawnHeightDiff * this.spawnHeightValue.getScale(percent);
      float depth = this.spawnDepth + this.spawnDepthDiff * this.spawnDepthValue.getScale(percent);
      if (this.edges) {
         int a = MathUtils.random(-1, 1);
         float tx = 0.0F;
         float ty = 0.0F;
         float tz = 0.0F;
         if (a == -1) {
            tx = MathUtils.random(1) == 0 ? -width / 2.0F : width / 2.0F;
            if (tx == 0.0F) {
               ty = MathUtils.random(1) == 0 ? -height / 2.0F : height / 2.0F;
               tz = MathUtils.random(1) == 0 ? -depth / 2.0F : depth / 2.0F;
            } else {
               ty = MathUtils.random(height) - height / 2.0F;
               tz = MathUtils.random(depth) - depth / 2.0F;
            }
         } else if (a == 0) {
            tz = MathUtils.random(1) == 0 ? -depth / 2.0F : depth / 2.0F;
            if (tz == 0.0F) {
               ty = MathUtils.random(1) == 0 ? -height / 2.0F : height / 2.0F;
               tx = MathUtils.random(1) == 0 ? -width / 2.0F : width / 2.0F;
            } else {
               ty = MathUtils.random(height) - height / 2.0F;
               tx = MathUtils.random(width) - width / 2.0F;
            }
         } else {
            ty = MathUtils.random(1) == 0 ? -height / 2.0F : height / 2.0F;
            if (ty == 0.0F) {
               tx = MathUtils.random(1) == 0 ? -width / 2.0F : width / 2.0F;
               tz = MathUtils.random(1) == 0 ? -depth / 2.0F : depth / 2.0F;
            } else {
               tx = MathUtils.random(width) - width / 2.0F;
               tz = MathUtils.random(depth) - depth / 2.0F;
            }
         }

         vector.x = tx;
         vector.y = ty;
         vector.z = tz;
      } else {
         vector.x = MathUtils.random(width) - width / 2.0F;
         vector.y = MathUtils.random(height) - height / 2.0F;
         vector.z = MathUtils.random(depth) - depth / 2.0F;
      }
   }

   @Override
   public SpawnShapeValue copy() {
      return new RectangleSpawnShapeValue(this);
   }
}
