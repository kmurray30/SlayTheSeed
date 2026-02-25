package com.badlogic.gdx.graphics.g3d.utils.shapebuilders;

import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class CapsuleShapeBuilder extends BaseShapeBuilder {
   public static void build(MeshPartBuilder builder, float radius, float height, int divisions) {
      if (height < 2.0F * radius) {
         throw new GdxRuntimeException("Height must be at least twice the radius");
      } else {
         float d = 2.0F * radius;
         CylinderShapeBuilder.build(builder, d, height - d, d, divisions, 0.0F, 360.0F, false);
         SphereShapeBuilder.build(builder, matTmp1.setToTranslation(0.0F, 0.5F * (height - d), 0.0F), d, d, d, divisions, divisions, 0.0F, 360.0F, 0.0F, 90.0F);
         SphereShapeBuilder.build(
            builder, matTmp1.setToTranslation(0.0F, -0.5F * (height - d), 0.0F), d, d, d, divisions, divisions, 0.0F, 360.0F, 90.0F, 180.0F
         );
      }
   }
}
