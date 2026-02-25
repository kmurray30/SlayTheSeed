package com.badlogic.gdx.graphics.g2d;

public class PolygonRegion {
   final float[] textureCoords;
   final float[] vertices;
   final short[] triangles;
   final TextureRegion region;

   public PolygonRegion(TextureRegion region, float[] vertices, short[] triangles) {
      this.region = region;
      this.vertices = vertices;
      this.triangles = triangles;
      float[] textureCoords = this.textureCoords = new float[vertices.length];
      float u = region.u;
      float v = region.v;
      float uvWidth = region.u2 - u;
      float uvHeight = region.v2 - v;
      int width = region.regionWidth;
      int height = region.regionHeight;
      int i = 0;

      for (int n = vertices.length; i < n; i++) {
         textureCoords[i] = u + uvWidth * (vertices[i] / width);
         textureCoords[++i] = v + uvHeight * (1.0F - vertices[i] / height);
      }
   }

   public float[] getVertices() {
      return this.vertices;
   }

   public short[] getTriangles() {
      return this.triangles;
   }

   public float[] getTextureCoords() {
      return this.textureCoords;
   }

   public TextureRegion getRegion() {
      return this.region;
   }
}
