package com.badlogic.gdx.graphics.g3d.particles.values;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public final class CylinderSpawnShapeValue extends PrimitiveSpawnShapeValue {
   public CylinderSpawnShapeValue(CylinderSpawnShapeValue cylinderSpawnShapeValue) {
      super(cylinderSpawnShapeValue);
      this.load(cylinderSpawnShapeValue);
   }

   public CylinderSpawnShapeValue() {
   }

   @Override
   public void spawnAux(Vector3 vector, float percent) {
      float width = this.spawnWidth + this.spawnWidthDiff * this.spawnWidthValue.getScale(percent);
      float height = this.spawnHeight + this.spawnHeightDiff * this.spawnHeightValue.getScale(percent);
      float depth = this.spawnDepth + this.spawnDepthDiff * this.spawnDepthValue.getScale(percent);
      float hf = height / 2.0F;
      float ty = MathUtils.random(height) - hf;
      float radiusX;
      float radiusZ;
      if (this.edges) {
         radiusX = width / 2.0F;
         radiusZ = depth / 2.0F;
      } else {
         radiusX = MathUtils.random(width) / 2.0F;
         radiusZ = MathUtils.random(depth) / 2.0F;
      }

      float spawnTheta = 0.0F;
      boolean isRadiusXZero = radiusX == 0.0F;
      boolean isRadiusZZero = radiusZ == 0.0F;
      if (!isRadiusXZero && !isRadiusZZero) {
         spawnTheta = MathUtils.random(360.0F);
      } else if (isRadiusXZero) {
         spawnTheta = MathUtils.random(1) == 0 ? -90.0F : 90.0F;
      } else if (isRadiusZZero) {
         spawnTheta = MathUtils.random(1) == 0 ? 0.0F : 180.0F;
      }

      vector.set(radiusX * MathUtils.cosDeg(spawnTheta), ty, radiusZ * MathUtils.sinDeg(spawnTheta));
   }

   @Override
   public SpawnShapeValue copy() {
      return new CylinderSpawnShapeValue(this);
   }
}
