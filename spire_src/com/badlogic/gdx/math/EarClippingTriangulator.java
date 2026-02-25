package com.badlogic.gdx.math;

import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ShortArray;

public class EarClippingTriangulator {
   private static final int CONCAVE = -1;
   private static final int TANGENTIAL = 0;
   private static final int CONVEX = 1;
   private final ShortArray indicesArray = new ShortArray();
   private short[] indices;
   private float[] vertices;
   private int vertexCount;
   private final IntArray vertexTypes = new IntArray();
   private final ShortArray triangles = new ShortArray();

   public ShortArray computeTriangles(FloatArray vertices) {
      return this.computeTriangles(vertices.items, 0, vertices.size);
   }

   public ShortArray computeTriangles(float[] vertices) {
      return this.computeTriangles(vertices, 0, vertices.length);
   }

   public ShortArray computeTriangles(float[] vertices, int offset, int count) {
      this.vertices = vertices;
      int vertexCount = this.vertexCount = count / 2;
      int vertexOffset = offset / 2;
      ShortArray indicesArray = this.indicesArray;
      indicesArray.clear();
      indicesArray.ensureCapacity(vertexCount);
      indicesArray.size = vertexCount;
      short[] indices = this.indices = indicesArray.items;
      if (areVerticesClockwise(vertices, offset, count)) {
         for (short i = 0; i < vertexCount; i++) {
            indices[i] = (short)(vertexOffset + i);
         }
      } else {
         int i = 0;

         for (int n = vertexCount - 1; i < vertexCount; i++) {
            indices[i] = (short)(vertexOffset + n - i);
         }
      }

      IntArray vertexTypes = this.vertexTypes;
      vertexTypes.clear();
      vertexTypes.ensureCapacity(vertexCount);
      int i = 0;

      for (int n = vertexCount; i < n; i++) {
         vertexTypes.add(this.classifyVertex(i));
      }

      ShortArray triangles = this.triangles;
      triangles.clear();
      triangles.ensureCapacity(Math.max(0, vertexCount - 2) * 3);
      this.triangulate();
      return triangles;
   }

   private void triangulate() {
      int[] vertexTypes = this.vertexTypes.items;

      while (this.vertexCount > 3) {
         int earTipIndex = this.findEarTip();
         this.cutEarTip(earTipIndex);
         int previousIndex = this.previousIndex(earTipIndex);
         int nextIndex = earTipIndex == this.vertexCount ? 0 : earTipIndex;
         vertexTypes[previousIndex] = this.classifyVertex(previousIndex);
         vertexTypes[nextIndex] = this.classifyVertex(nextIndex);
      }

      if (this.vertexCount == 3) {
         ShortArray triangles = this.triangles;
         short[] indices = this.indices;
         triangles.add(indices[0]);
         triangles.add(indices[1]);
         triangles.add(indices[2]);
      }
   }

   private int classifyVertex(int index) {
      short[] indices = this.indices;
      int previous = indices[this.previousIndex(index)] * 2;
      int current = indices[index] * 2;
      int next = indices[this.nextIndex(index)] * 2;
      float[] vertices = this.vertices;
      return computeSpannedAreaSign(vertices[previous], vertices[previous + 1], vertices[current], vertices[current + 1], vertices[next], vertices[next + 1]);
   }

   private int findEarTip() {
      int vertexCount = this.vertexCount;

      for (int i = 0; i < vertexCount; i++) {
         if (this.isEarTip(i)) {
            return i;
         }
      }

      int[] vertexTypes = this.vertexTypes.items;

      for (int ix = 0; ix < vertexCount; ix++) {
         if (vertexTypes[ix] != -1) {
            return ix;
         }
      }

      return 0;
   }

   private boolean isEarTip(int earTipIndex) {
      int[] vertexTypes = this.vertexTypes.items;
      if (vertexTypes[earTipIndex] == -1) {
         return false;
      } else {
         int previousIndex = this.previousIndex(earTipIndex);
         int nextIndex = this.nextIndex(earTipIndex);
         short[] indices = this.indices;
         int p1 = indices[previousIndex] * 2;
         int p2 = indices[earTipIndex] * 2;
         int p3 = indices[nextIndex] * 2;
         float[] vertices = this.vertices;
         float p1x = vertices[p1];
         float p1y = vertices[p1 + 1];
         float p2x = vertices[p2];
         float p2y = vertices[p2 + 1];
         float p3x = vertices[p3];
         float p3y = vertices[p3 + 1];

         for (int i = this.nextIndex(nextIndex); i != previousIndex; i = this.nextIndex(i)) {
            if (vertexTypes[i] != 1) {
               int v = indices[i] * 2;
               float vx = vertices[v];
               float vy = vertices[v + 1];
               if (computeSpannedAreaSign(p3x, p3y, p1x, p1y, vx, vy) >= 0
                  && computeSpannedAreaSign(p1x, p1y, p2x, p2y, vx, vy) >= 0
                  && computeSpannedAreaSign(p2x, p2y, p3x, p3y, vx, vy) >= 0) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   private void cutEarTip(int earTipIndex) {
      short[] indices = this.indices;
      ShortArray triangles = this.triangles;
      triangles.add(indices[this.previousIndex(earTipIndex)]);
      triangles.add(indices[earTipIndex]);
      triangles.add(indices[this.nextIndex(earTipIndex)]);
      this.indicesArray.removeIndex(earTipIndex);
      this.vertexTypes.removeIndex(earTipIndex);
      this.vertexCount--;
   }

   private int previousIndex(int index) {
      return (index == 0 ? this.vertexCount : index) - 1;
   }

   private int nextIndex(int index) {
      return (index + 1) % this.vertexCount;
   }

   private static boolean areVerticesClockwise(float[] vertices, int offset, int count) {
      if (count <= 2) {
         return false;
      } else {
         float area = 0.0F;
         int i = offset;

         for (int n = offset + count - 3; i < n; i += 2) {
            float p1x = vertices[i];
            float p1y = vertices[i + 1];
            float p2x = vertices[i + 2];
            float p2y = vertices[i + 3];
            area += p1x * p2y - p2x * p1y;
         }

         float p1x = vertices[offset + count - 2];
         float p1y = vertices[offset + count - 1];
         float p2x = vertices[offset];
         float p2y = vertices[offset + 1];
         return area + p1x * p2y - p2x * p1y < 0.0F;
      }
   }

   private static int computeSpannedAreaSign(float p1x, float p1y, float p2x, float p2y, float p3x, float p3y) {
      float area = p1x * (p3y - p2y);
      area += p2x * (p1y - p3y);
      area += p3x * (p2y - p1y);
      return (int)Math.signum(area);
   }
}
