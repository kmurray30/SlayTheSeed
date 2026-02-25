package com.badlogic.gdx.graphics.g3d.utils.shapebuilders;

import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ShortArray;

public class SphereShapeBuilder extends BaseShapeBuilder {
   private static final ShortArray tmpIndices = new ShortArray();

   public static void build(MeshPartBuilder builder, float width, float height, float depth, int divisionsU, int divisionsV) {
      build(builder, width, height, depth, divisionsU, divisionsV, 0.0F, 360.0F, 0.0F, 180.0F);
   }

   @Deprecated
   public static void build(MeshPartBuilder builder, Matrix4 transform, float width, float height, float depth, int divisionsU, int divisionsV) {
      build(builder, transform, width, height, depth, divisionsU, divisionsV, 0.0F, 360.0F, 0.0F, 180.0F);
   }

   public static void build(
      MeshPartBuilder builder,
      float width,
      float height,
      float depth,
      int divisionsU,
      int divisionsV,
      float angleUFrom,
      float angleUTo,
      float angleVFrom,
      float angleVTo
   ) {
      build(builder, matTmp1.idt(), width, height, depth, divisionsU, divisionsV, angleUFrom, angleUTo, angleVFrom, angleVTo);
   }

   @Deprecated
   public static void build(
      MeshPartBuilder builder,
      Matrix4 transform,
      float width,
      float height,
      float depth,
      int divisionsU,
      int divisionsV,
      float angleUFrom,
      float angleUTo,
      float angleVFrom,
      float angleVTo
   ) {
      float hw = width * 0.5F;
      float hh = height * 0.5F;
      float hd = depth * 0.5F;
      float auo = (float) (Math.PI / 180.0) * angleUFrom;
      float stepU = (float) (Math.PI / 180.0) * (angleUTo - angleUFrom) / divisionsU;
      float avo = (float) (Math.PI / 180.0) * angleVFrom;
      float stepV = (float) (Math.PI / 180.0) * (angleVTo - angleVFrom) / divisionsV;
      float us = 1.0F / divisionsU;
      float vs = 1.0F / divisionsV;
      float u = 0.0F;
      float v = 0.0F;
      float angleU = 0.0F;
      float angleV = 0.0F;
      MeshPartBuilder.VertexInfo curr1 = vertTmp3.set(null, null, null, null);
      curr1.hasUV = curr1.hasPosition = curr1.hasNormal = true;
      int s = divisionsU + 3;
      tmpIndices.clear();
      tmpIndices.ensureCapacity(divisionsU * 2);
      tmpIndices.size = s;
      int tempOffset = 0;
      builder.ensureVertices((divisionsV + 1) * (divisionsU + 1));
      builder.ensureRectangleIndices(divisionsU);

      for (int iv = 0; iv <= divisionsV; iv++) {
         angleV = avo + stepV * iv;
         v = vs * iv;
         float t = MathUtils.sin(angleV);
         float h = MathUtils.cos(angleV) * hh;

         for (int iu = 0; iu <= divisionsU; iu++) {
            angleU = auo + stepU * iu;
            u = 1.0F - us * iu;
            curr1.position.set(MathUtils.cos(angleU) * hw * t, h, MathUtils.sin(angleU) * hd * t).mul(transform);
            curr1.normal.set(curr1.position).nor();
            curr1.uv.set(u, v);
            tmpIndices.set(tempOffset, builder.vertex(curr1));
            int o = tempOffset + s;
            if (iv > 0 && iu > 0) {
               builder.rect(
                  tmpIndices.get(tempOffset),
                  tmpIndices.get((o - 1) % s),
                  tmpIndices.get((o - (divisionsU + 2)) % s),
                  tmpIndices.get((o - (divisionsU + 1)) % s)
               );
            }

            tempOffset = (tempOffset + 1) % tmpIndices.size;
         }
      }
   }
}
