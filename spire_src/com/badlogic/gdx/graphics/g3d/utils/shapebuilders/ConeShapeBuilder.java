package com.badlogic.gdx.graphics.g3d.utils.shapebuilders;

import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.math.MathUtils;

public class ConeShapeBuilder extends BaseShapeBuilder {
   public static void build(MeshPartBuilder builder, float width, float height, float depth, int divisions) {
      build(builder, width, height, depth, divisions, 0.0F, 360.0F);
   }

   public static void build(MeshPartBuilder builder, float width, float height, float depth, int divisions, float angleFrom, float angleTo) {
      build(builder, width, height, depth, divisions, angleFrom, angleTo, true);
   }

   public static void build(MeshPartBuilder builder, float width, float height, float depth, int divisions, float angleFrom, float angleTo, boolean close) {
      builder.ensureVertices(divisions + 2);
      builder.ensureTriangleIndices(divisions);
      float hw = width * 0.5F;
      float hh = height * 0.5F;
      float hd = depth * 0.5F;
      float ao = (float) (Math.PI / 180.0) * angleFrom;
      float step = (float) (Math.PI / 180.0) * (angleTo - angleFrom) / divisions;
      float us = 1.0F / divisions;
      float u = 0.0F;
      float angle = 0.0F;
      MeshPartBuilder.VertexInfo curr1 = vertTmp3.set(null, null, null, null);
      curr1.hasUV = curr1.hasPosition = curr1.hasNormal = true;
      MeshPartBuilder.VertexInfo curr2 = vertTmp4.set(null, null, null, null).setPos(0.0F, hh, 0.0F).setNor(0.0F, 1.0F, 0.0F).setUV(0.5F, 0.0F);
      short base = builder.vertex(curr2);
      short i2 = 0;

      for (int i = 0; i <= divisions; i++) {
         angle = ao + step * i;
         u = 1.0F - us * i;
         curr1.position.set(MathUtils.cos(angle) * hw, 0.0F, MathUtils.sin(angle) * hd);
         curr1.normal.set(curr1.position).nor();
         curr1.position.y = -hh;
         curr1.uv.set(u, 1.0F);
         short i1 = builder.vertex(curr1);
         if (i != 0) {
            builder.triangle(base, i1, i2);
         }

         i2 = i1;
      }

      if (close) {
         EllipseShapeBuilder.build(
            builder,
            width,
            depth,
            0.0F,
            0.0F,
            divisions,
            0.0F,
            -hh,
            0.0F,
            0.0F,
            -1.0F,
            0.0F,
            -1.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            180.0F - angleTo,
            180.0F - angleFrom
         );
      }
   }
}
