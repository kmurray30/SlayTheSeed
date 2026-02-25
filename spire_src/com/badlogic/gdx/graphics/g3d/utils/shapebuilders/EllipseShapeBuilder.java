package com.badlogic.gdx.graphics.g3d.utils.shapebuilders;

import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class EllipseShapeBuilder extends BaseShapeBuilder {
   public static void build(
      MeshPartBuilder builder, float radius, int divisions, float centerX, float centerY, float centerZ, float normalX, float normalY, float normalZ
   ) {
      build(builder, radius, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, 0.0F, 360.0F);
   }

   public static void build(MeshPartBuilder builder, float radius, int divisions, Vector3 center, Vector3 normal) {
      build(builder, radius, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z);
   }

   public static void build(MeshPartBuilder builder, float radius, int divisions, Vector3 center, Vector3 normal, Vector3 tangent, Vector3 binormal) {
      build(
         builder,
         radius,
         divisions,
         center.x,
         center.y,
         center.z,
         normal.x,
         normal.y,
         normal.z,
         tangent.x,
         tangent.y,
         tangent.z,
         binormal.x,
         binormal.y,
         binormal.z
      );
   }

   public static void build(
      MeshPartBuilder builder,
      float radius,
      int divisions,
      float centerX,
      float centerY,
      float centerZ,
      float normalX,
      float normalY,
      float normalZ,
      float tangentX,
      float tangentY,
      float tangentZ,
      float binormalX,
      float binormalY,
      float binormalZ
   ) {
      build(
         builder,
         radius,
         divisions,
         centerX,
         centerY,
         centerZ,
         normalX,
         normalY,
         normalZ,
         tangentX,
         tangentY,
         tangentZ,
         binormalX,
         binormalY,
         binormalZ,
         0.0F,
         360.0F
      );
   }

   public static void build(
      MeshPartBuilder builder,
      float radius,
      int divisions,
      float centerX,
      float centerY,
      float centerZ,
      float normalX,
      float normalY,
      float normalZ,
      float angleFrom,
      float angleTo
   ) {
      build(builder, radius * 2.0F, radius * 2.0F, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, angleFrom, angleTo);
   }

   public static void build(MeshPartBuilder builder, float radius, int divisions, Vector3 center, Vector3 normal, float angleFrom, float angleTo) {
      build(builder, radius, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z, angleFrom, angleTo);
   }

   public static void build(
      MeshPartBuilder builder, float radius, int divisions, Vector3 center, Vector3 normal, Vector3 tangent, Vector3 binormal, float angleFrom, float angleTo
   ) {
      build(
         builder,
         radius,
         divisions,
         center.x,
         center.y,
         center.z,
         normal.x,
         normal.y,
         normal.z,
         tangent.x,
         tangent.y,
         tangent.z,
         binormal.x,
         binormal.y,
         binormal.z,
         angleFrom,
         angleTo
      );
   }

   public static void build(
      MeshPartBuilder builder,
      float radius,
      int divisions,
      float centerX,
      float centerY,
      float centerZ,
      float normalX,
      float normalY,
      float normalZ,
      float tangentX,
      float tangentY,
      float tangentZ,
      float binormalX,
      float binormalY,
      float binormalZ,
      float angleFrom,
      float angleTo
   ) {
      build(
         builder,
         radius * 2.0F,
         radius * 2.0F,
         0.0F,
         0.0F,
         divisions,
         centerX,
         centerY,
         centerZ,
         normalX,
         normalY,
         normalZ,
         tangentX,
         tangentY,
         tangentZ,
         binormalX,
         binormalY,
         binormalZ,
         angleFrom,
         angleTo
      );
   }

   public static void build(
      MeshPartBuilder builder,
      float width,
      float height,
      int divisions,
      float centerX,
      float centerY,
      float centerZ,
      float normalX,
      float normalY,
      float normalZ
   ) {
      build(builder, width, height, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, 0.0F, 360.0F);
   }

   public static void build(MeshPartBuilder builder, float width, float height, int divisions, Vector3 center, Vector3 normal) {
      build(builder, width, height, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z);
   }

   public static void build(
      MeshPartBuilder builder, float width, float height, int divisions, Vector3 center, Vector3 normal, Vector3 tangent, Vector3 binormal
   ) {
      build(
         builder,
         width,
         height,
         divisions,
         center.x,
         center.y,
         center.z,
         normal.x,
         normal.y,
         normal.z,
         tangent.x,
         tangent.y,
         tangent.z,
         binormal.x,
         binormal.y,
         binormal.z
      );
   }

   public static void build(
      MeshPartBuilder builder,
      float width,
      float height,
      int divisions,
      float centerX,
      float centerY,
      float centerZ,
      float normalX,
      float normalY,
      float normalZ,
      float tangentX,
      float tangentY,
      float tangentZ,
      float binormalX,
      float binormalY,
      float binormalZ
   ) {
      build(
         builder,
         width,
         height,
         divisions,
         centerX,
         centerY,
         centerZ,
         normalX,
         normalY,
         normalZ,
         tangentX,
         tangentY,
         tangentZ,
         binormalX,
         binormalY,
         binormalZ,
         0.0F,
         360.0F
      );
   }

   public static void build(
      MeshPartBuilder builder,
      float width,
      float height,
      int divisions,
      float centerX,
      float centerY,
      float centerZ,
      float normalX,
      float normalY,
      float normalZ,
      float angleFrom,
      float angleTo
   ) {
      build(builder, width, height, 0.0F, 0.0F, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, angleFrom, angleTo);
   }

   public static void build(MeshPartBuilder builder, float width, float height, int divisions, Vector3 center, Vector3 normal, float angleFrom, float angleTo) {
      build(builder, width, height, 0.0F, 0.0F, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z, angleFrom, angleTo);
   }

   public static void build(
      MeshPartBuilder builder,
      float width,
      float height,
      int divisions,
      Vector3 center,
      Vector3 normal,
      Vector3 tangent,
      Vector3 binormal,
      float angleFrom,
      float angleTo
   ) {
      build(
         builder,
         width,
         height,
         0.0F,
         0.0F,
         divisions,
         center.x,
         center.y,
         center.z,
         normal.x,
         normal.y,
         normal.z,
         tangent.x,
         tangent.y,
         tangent.z,
         binormal.x,
         binormal.y,
         binormal.z,
         angleFrom,
         angleTo
      );
   }

   public static void build(
      MeshPartBuilder builder,
      float width,
      float height,
      int divisions,
      float centerX,
      float centerY,
      float centerZ,
      float normalX,
      float normalY,
      float normalZ,
      float tangentX,
      float tangentY,
      float tangentZ,
      float binormalX,
      float binormalY,
      float binormalZ,
      float angleFrom,
      float angleTo
   ) {
      build(
         builder,
         width,
         height,
         0.0F,
         0.0F,
         divisions,
         centerX,
         centerY,
         centerZ,
         normalX,
         normalY,
         normalZ,
         tangentX,
         tangentY,
         tangentZ,
         binormalX,
         binormalY,
         binormalZ,
         angleFrom,
         angleTo
      );
   }

   public static void build(
      MeshPartBuilder builder,
      float width,
      float height,
      float innerWidth,
      float innerHeight,
      int divisions,
      float centerX,
      float centerY,
      float centerZ,
      float normalX,
      float normalY,
      float normalZ,
      float angleFrom,
      float angleTo
   ) {
      tmpV1.set(normalX, normalY, normalZ).crs(0.0F, 0.0F, 1.0F);
      tmpV2.set(normalX, normalY, normalZ).crs(0.0F, 1.0F, 0.0F);
      if (tmpV2.len2() > tmpV1.len2()) {
         tmpV1.set(tmpV2);
      }

      tmpV2.set(tmpV1.nor()).crs(normalX, normalY, normalZ).nor();
      build(
         builder,
         width,
         height,
         innerWidth,
         innerHeight,
         divisions,
         centerX,
         centerY,
         centerZ,
         normalX,
         normalY,
         normalZ,
         tmpV1.x,
         tmpV1.y,
         tmpV1.z,
         tmpV2.x,
         tmpV2.y,
         tmpV2.z,
         angleFrom,
         angleTo
      );
   }

   public static void build(
      MeshPartBuilder builder,
      float width,
      float height,
      float innerWidth,
      float innerHeight,
      int divisions,
      float centerX,
      float centerY,
      float centerZ,
      float normalX,
      float normalY,
      float normalZ
   ) {
      build(builder, width, height, innerWidth, innerHeight, divisions, centerX, centerY, centerZ, normalX, normalY, normalZ, 0.0F, 360.0F);
   }

   public static void build(
      MeshPartBuilder builder, float width, float height, float innerWidth, float innerHeight, int divisions, Vector3 center, Vector3 normal
   ) {
      build(builder, width, height, innerWidth, innerHeight, divisions, center.x, center.y, center.z, normal.x, normal.y, normal.z, 0.0F, 360.0F);
   }

   public static void build(
      MeshPartBuilder builder,
      float width,
      float height,
      float innerWidth,
      float innerHeight,
      int divisions,
      float centerX,
      float centerY,
      float centerZ,
      float normalX,
      float normalY,
      float normalZ,
      float tangentX,
      float tangentY,
      float tangentZ,
      float binormalX,
      float binormalY,
      float binormalZ,
      float angleFrom,
      float angleTo
   ) {
      if (innerWidth <= 0.0F || innerHeight <= 0.0F) {
         builder.ensureVertices(divisions + 2);
         builder.ensureTriangleIndices(divisions);
      } else if (innerWidth == width && innerHeight == height) {
         builder.ensureVertices(divisions + 1);
         builder.ensureIndices(divisions + 1);
         if (builder.getPrimitiveType() != 1) {
            throw new GdxRuntimeException("Incorrect primitive type : expect GL_LINES because innerWidth == width && innerHeight == height");
         }
      } else {
         builder.ensureVertices((divisions + 1) * 2);
         builder.ensureRectangleIndices(divisions + 1);
      }

      float ao = (float) (Math.PI / 180.0) * angleFrom;
      float step = (float) (Math.PI / 180.0) * (angleTo - angleFrom) / divisions;
      Vector3 sxEx = tmpV1.set(tangentX, tangentY, tangentZ).scl(width * 0.5F);
      Vector3 syEx = tmpV2.set(binormalX, binormalY, binormalZ).scl(height * 0.5F);
      Vector3 sxIn = tmpV3.set(tangentX, tangentY, tangentZ).scl(innerWidth * 0.5F);
      Vector3 syIn = tmpV4.set(binormalX, binormalY, binormalZ).scl(innerHeight * 0.5F);
      MeshPartBuilder.VertexInfo currIn = vertTmp3.set(null, null, null, null);
      currIn.hasUV = currIn.hasPosition = currIn.hasNormal = true;
      currIn.uv.set(0.5F, 0.5F);
      currIn.position.set(centerX, centerY, centerZ);
      currIn.normal.set(normalX, normalY, normalZ);
      MeshPartBuilder.VertexInfo currEx = vertTmp4.set(null, null, null, null);
      currEx.hasUV = currEx.hasPosition = currEx.hasNormal = true;
      currEx.uv.set(0.5F, 0.5F);
      currEx.position.set(centerX, centerY, centerZ);
      currEx.normal.set(normalX, normalY, normalZ);
      short center = builder.vertex(currEx);
      float angle = 0.0F;
      float us = 0.5F * (innerWidth / width);
      float vs = 0.5F * (innerHeight / height);
      short i2 = 0;
      short i3 = 0;
      short i4 = 0;

      for (int i = 0; i <= divisions; i++) {
         angle = ao + step * i;
         float x = MathUtils.cos(angle);
         float y = MathUtils.sin(angle);
         currEx.position.set(centerX, centerY, centerZ).add(sxEx.x * x + syEx.x * y, sxEx.y * x + syEx.y * y, sxEx.z * x + syEx.z * y);
         currEx.uv.set(0.5F + 0.5F * x, 0.5F + 0.5F * y);
         short i1 = builder.vertex(currEx);
         if (!(innerWidth <= 0.0F) && !(innerHeight <= 0.0F)) {
            if (innerWidth == width && innerHeight == height) {
               if (i != 0) {
                  builder.line(i1, i2);
               }

               i2 = i1;
            } else {
               currIn.position.set(centerX, centerY, centerZ).add(sxIn.x * x + syIn.x * y, sxIn.y * x + syIn.y * y, sxIn.z * x + syIn.z * y);
               currIn.uv.set(0.5F + us * x, 0.5F + vs * y);
               i2 = i1;
               i1 = builder.vertex(currIn);
               if (i != 0) {
                  builder.rect(i1, i1, i4, i3);
               }

               i4 = i1;
               i3 = i1;
            }
         } else {
            if (i != 0) {
               builder.triangle(i1, i2, center);
            }

            i2 = i1;
         }
      }
   }
}
