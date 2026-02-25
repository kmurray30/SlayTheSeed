package com.badlogic.gdx.graphics.g3d.utils.shapebuilders;

import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.math.MathUtils;

public class CylinderShapeBuilder extends BaseShapeBuilder {
   public static void build(MeshPartBuilder builder, float width, float height, float depth, int divisions) {
      build(builder, width, height, depth, divisions, 0.0F, 360.0F);
   }

   public static void build(MeshPartBuilder builder, float width, float height, float depth, int divisions, float angleFrom, float angleTo) {
      build(builder, width, height, depth, divisions, angleFrom, angleTo, true);
   }

   public static void build(MeshPartBuilder builder, float width, float height, float depth, int divisions, float angleFrom, float angleTo, boolean close) {
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
      MeshPartBuilder.VertexInfo curr2 = vertTmp4.set(null, null, null, null);
      curr2.hasUV = curr2.hasPosition = curr2.hasNormal = true;
      short i3 = 0;
      short i4 = 0;
      builder.ensureVertices(2 * (divisions + 1));
      builder.ensureRectangleIndices(divisions);

      for (int i = 0; i <= divisions; i++) {
         angle = ao + step * i;
         u = 1.0F - us * i;
         curr1.position.set(MathUtils.cos(angle) * hw, 0.0F, MathUtils.sin(angle) * hd);
         curr1.normal.set(curr1.position).nor();
         curr1.position.y = -hh;
         curr1.uv.set(u, 1.0F);
         curr2.position.set(curr1.position);
         curr2.normal.set(curr1.normal);
         curr2.position.y = hh;
         curr2.uv.set(u, 0.0F);
         short i2 = builder.vertex(curr1);
         short i1 = builder.vertex(curr2);
         if (i != 0) {
            builder.rect(i3, i1, i2, i4);
         }

         i4 = i2;
         i3 = i1;
      }

      if (close) {
         EllipseShapeBuilder.build(
            builder, width, depth, 0.0F, 0.0F, divisions, 0.0F, hh, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, angleFrom, angleTo
         );
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
