package com.badlogic.gdx.graphics.g3d.particles.values;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

public final class EllipseSpawnShapeValue extends PrimitiveSpawnShapeValue {
   PrimitiveSpawnShapeValue.SpawnSide side = PrimitiveSpawnShapeValue.SpawnSide.both;

   public EllipseSpawnShapeValue(EllipseSpawnShapeValue value) {
      super(value);
      this.load(value);
   }

   public EllipseSpawnShapeValue() {
   }

   @Override
   public void spawnAux(Vector3 vector, float percent) {
      float width = this.spawnWidth + this.spawnWidthDiff * this.spawnWidthValue.getScale(percent);
      float height = this.spawnHeight + this.spawnHeightDiff * this.spawnHeightValue.getScale(percent);
      float depth = this.spawnDepth + this.spawnDepthDiff * this.spawnDepthValue.getScale(percent);
      float minT = 0.0F;
      float maxT = (float) (Math.PI * 2);
      if (this.side == PrimitiveSpawnShapeValue.SpawnSide.top) {
         maxT = (float) Math.PI;
      } else if (this.side == PrimitiveSpawnShapeValue.SpawnSide.bottom) {
         maxT = (float) -Math.PI;
      }

      float t = MathUtils.random(minT, maxT);
      float radiusX;
      float radiusY;
      float radiusZ;
      if (this.edges) {
         if (width == 0.0F) {
            vector.set(0.0F, height / 2.0F * MathUtils.sin(t), depth / 2.0F * MathUtils.cos(t));
            return;
         }

         if (height == 0.0F) {
            vector.set(width / 2.0F * MathUtils.cos(t), 0.0F, depth / 2.0F * MathUtils.sin(t));
            return;
         }

         if (depth == 0.0F) {
            vector.set(width / 2.0F * MathUtils.cos(t), height / 2.0F * MathUtils.sin(t), 0.0F);
            return;
         }

         radiusX = width / 2.0F;
         radiusY = height / 2.0F;
         radiusZ = depth / 2.0F;
      } else {
         radiusX = MathUtils.random(width / 2.0F);
         radiusY = MathUtils.random(height / 2.0F);
         radiusZ = MathUtils.random(depth / 2.0F);
      }

      float z = MathUtils.random(-1.0F, 1.0F);
      float r = (float)Math.sqrt(1.0F - z * z);
      vector.set(radiusX * r * MathUtils.cos(t), radiusY * r * MathUtils.sin(t), radiusZ * z);
   }

   public PrimitiveSpawnShapeValue.SpawnSide getSide() {
      return this.side;
   }

   public void setSide(PrimitiveSpawnShapeValue.SpawnSide side) {
      this.side = side;
   }

   @Override
   public void load(ParticleValue value) {
      super.load(value);
      EllipseSpawnShapeValue shape = (EllipseSpawnShapeValue)value;
      this.side = shape.side;
   }

   @Override
   public SpawnShapeValue copy() {
      return new EllipseSpawnShapeValue(this);
   }

   @Override
   public void write(Json json) {
      super.write(json);
      json.writeValue("side", this.side);
   }

   @Override
   public void read(Json json, JsonValue jsonData) {
      super.read(json, jsonData);
      this.side = json.readValue("side", PrimitiveSpawnShapeValue.SpawnSide.class, jsonData);
   }
}
