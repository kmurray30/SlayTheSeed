package com.badlogic.gdx.math;

import com.badlogic.gdx.utils.BooleanArray;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ShortArray;

public class DelaunayTriangulator {
   private static final float EPSILON = 1.0E-6F;
   private static final int INSIDE = 0;
   private static final int COMPLETE = 1;
   private static final int INCOMPLETE = 2;
   private final IntArray quicksortStack = new IntArray();
   private float[] sortedPoints;
   private final ShortArray triangles = new ShortArray(false, 16);
   private final ShortArray originalIndices = new ShortArray(false, 0);
   private final IntArray edges = new IntArray();
   private final BooleanArray complete = new BooleanArray(false, 16);
   private final float[] superTriangle = new float[6];
   private final Vector2 centroid = new Vector2();

   public ShortArray computeTriangles(FloatArray points, boolean sorted) {
      return this.computeTriangles(points.items, 0, points.size, sorted);
   }

   public ShortArray computeTriangles(float[] polygon, boolean sorted) {
      return this.computeTriangles(polygon, 0, polygon.length, sorted);
   }

   public ShortArray computeTriangles(float[] points, int offset, int count, boolean sorted) {
      ShortArray triangles = this.triangles;
      triangles.clear();
      if (count < 6) {
         return triangles;
      } else {
         triangles.ensureCapacity(count);
         if (!sorted) {
            if (this.sortedPoints == null || this.sortedPoints.length < count) {
               this.sortedPoints = new float[count];
            }

            System.arraycopy(points, offset, this.sortedPoints, 0, count);
            points = this.sortedPoints;
            offset = 0;
            this.sort(points, count);
         }

         int end = offset + count;
         float xmin = points[0];
         float ymin = points[1];
         float xmax = xmin;
         float ymax = ymin;

         for (int i = offset + 2; i < end; i++) {
            float value = points[i];
            if (value < xmin) {
               xmin = value;
            }

            if (value > xmax) {
               xmax = value;
            }

            value = points[++i];
            if (value < ymin) {
               ymin = value;
            }

            if (value > ymax) {
               ymax = value;
            }
         }

         float dx = xmax - xmin;
         float dy = ymax - ymin;
         float dmax = (dx > dy ? dx : dy) * 20.0F;
         float xmid = (xmax + xmin) / 2.0F;
         float ymid = (ymax + ymin) / 2.0F;
         float[] superTriangle = this.superTriangle;
         superTriangle[0] = xmid - dmax;
         superTriangle[1] = ymid - dmax;
         superTriangle[2] = xmid;
         superTriangle[3] = ymid + dmax;
         superTriangle[4] = xmid + dmax;
         superTriangle[5] = ymid - dmax;
         IntArray edges = this.edges;
         edges.ensureCapacity(count / 2);
         BooleanArray complete = this.complete;
         complete.clear();
         complete.ensureCapacity(count);
         triangles.add(end);
         triangles.add(end + 2);
         triangles.add(end + 4);
         complete.add(false);

         for (int pointIndex = offset; pointIndex < end; pointIndex += 2) {
            float x = points[pointIndex];
            float y = points[pointIndex + 1];
            short[] trianglesArray = triangles.items;
            boolean[] completeArray = complete.items;

            for (int triangleIndex = triangles.size - 1; triangleIndex >= 0; triangleIndex -= 3) {
               int completeIndex = triangleIndex / 3;
               if (!completeArray[completeIndex]) {
                  int p1 = trianglesArray[triangleIndex - 2];
                  int p2 = trianglesArray[triangleIndex - 1];
                  int p3 = trianglesArray[triangleIndex];
                  float x1;
                  float y1;
                  if (p1 >= end) {
                     int i = p1 - end;
                     x1 = superTriangle[i];
                     y1 = superTriangle[i + 1];
                  } else {
                     x1 = points[p1];
                     y1 = points[p1 + 1];
                  }

                  float x2;
                  float y2;
                  if (p2 >= end) {
                     int i = p2 - end;
                     x2 = superTriangle[i];
                     y2 = superTriangle[i + 1];
                  } else {
                     x2 = points[p2];
                     y2 = points[p2 + 1];
                  }

                  float x3;
                  float y3;
                  if (p3 >= end) {
                     int i = p3 - end;
                     x3 = superTriangle[i];
                     y3 = superTriangle[i + 1];
                  } else {
                     x3 = points[p3];
                     y3 = points[p3 + 1];
                  }

                  switch (this.circumCircle(x, y, x1, y1, x2, y2, x3, y3)) {
                     case 0:
                        edges.add(p1);
                        edges.add(p2);
                        edges.add(p2);
                        edges.add(p3);
                        edges.add(p3);
                        edges.add(p1);
                        triangles.removeIndex(triangleIndex);
                        triangles.removeIndex(triangleIndex - 1);
                        triangles.removeIndex(triangleIndex - 2);
                        complete.removeIndex(completeIndex);
                        break;
                     case 1:
                        completeArray[completeIndex] = true;
                  }
               }
            }

            int[] edgesArray = edges.items;
            int i = 0;

            for (int n = edges.size; i < n; i += 2) {
               int p1x = edgesArray[i];
               if (p1x != -1) {
                  int p2x = edgesArray[i + 1];
                  boolean skip = false;

                  for (int ii = i + 2; ii < n; ii += 2) {
                     if (p1x == edgesArray[ii + 1] && p2x == edgesArray[ii]) {
                        skip = true;
                        edgesArray[ii] = -1;
                     }
                  }

                  if (!skip) {
                     triangles.add(p1x);
                     triangles.add(edgesArray[i + 1]);
                     triangles.add(pointIndex);
                     complete.add(false);
                  }
               }
            }

            edges.clear();
         }

         short[] trianglesArray = triangles.items;

         for (int i = triangles.size - 1; i >= 0; i -= 3) {
            if (trianglesArray[i] >= end || trianglesArray[i - 1] >= end || trianglesArray[i - 2] >= end) {
               triangles.removeIndex(i);
               triangles.removeIndex(i - 1);
               triangles.removeIndex(i - 2);
            }
         }

         if (!sorted) {
            short[] originalIndicesArray = this.originalIndices.items;
            int ix = 0;

            for (int nx = triangles.size; ix < nx; ix++) {
               trianglesArray[ix] = (short)(originalIndicesArray[trianglesArray[ix] / 2] * 2);
            }
         }

         if (offset == 0) {
            int ix = 0;

            for (int nx = triangles.size; ix < nx; ix++) {
               trianglesArray[ix] = (short)(trianglesArray[ix] / 2);
            }
         } else {
            int ix = 0;

            for (int nx = triangles.size; ix < nx; ix++) {
               trianglesArray[ix] = (short)((trianglesArray[ix] - offset) / 2);
            }
         }

         return triangles;
      }
   }

   private int circumCircle(float xp, float yp, float x1, float y1, float x2, float y2, float x3, float y3) {
      float y1y2 = Math.abs(y1 - y2);
      float y2y3 = Math.abs(y2 - y3);
      float xc;
      float yc;
      if (y1y2 < 1.0E-6F) {
         if (y2y3 < 1.0E-6F) {
            return 2;
         }

         float m2 = -(x3 - x2) / (y3 - y2);
         float mx2 = (x2 + x3) / 2.0F;
         float my2 = (y2 + y3) / 2.0F;
         xc = (x2 + x1) / 2.0F;
         yc = m2 * (xc - mx2) + my2;
      } else {
         float m1 = -(x2 - x1) / (y2 - y1);
         float mx1 = (x1 + x2) / 2.0F;
         float my1 = (y1 + y2) / 2.0F;
         if (y2y3 < 1.0E-6F) {
            xc = (x3 + x2) / 2.0F;
            yc = m1 * (xc - mx1) + my1;
         } else {
            float m2 = -(x3 - x2) / (y3 - y2);
            float mx2 = (x2 + x3) / 2.0F;
            float my2 = (y2 + y3) / 2.0F;
            xc = (m1 * mx1 - m2 * mx2 + my2 - my1) / (m1 - m2);
            yc = m1 * (xc - mx1) + my1;
         }
      }

      float dx = x2 - xc;
      float dy = y2 - yc;
      float rsqr = dx * dx + dy * dy;
      dx = xp - xc;
      dx *= dx;
      dy = yp - yc;
      if (dx + dy * dy - rsqr <= 1.0E-6F) {
         return 0;
      } else {
         return xp > xc && dx > rsqr ? 1 : 2;
      }
   }

   private void sort(float[] values, int count) {
      int pointCount = count / 2;
      this.originalIndices.clear();
      this.originalIndices.ensureCapacity(pointCount);
      short[] originalIndicesArray = this.originalIndices.items;
      short i = (short)0;

      while (i < pointCount) {
         originalIndicesArray[i] = i++;
      }

      i = (short)0;
      int upper = count - 1;
      IntArray stack = this.quicksortStack;
      stack.add(i);
      stack.add(upper - 1);

      while (stack.size > 0) {
         upper = stack.pop();
         i = (short)stack.pop();
         if (upper > i) {
            int ix = this.quicksortPartition(values, i, upper, originalIndicesArray);
            if (ix - i > upper - ix) {
               stack.add(i);
               stack.add(ix - 2);
            }

            stack.add(ix + 2);
            stack.add(upper);
            if (upper - ix >= ix - i) {
               stack.add(i);
               stack.add(ix - 2);
            }
         }
      }
   }

   private int quicksortPartition(float[] values, int lower, int upper, short[] originalIndices) {
      float value = values[lower];
      int up = upper;
      int down = lower + 2;

      while (down < up) {
         while (down < up && values[down] <= value) {
            down += 2;
         }

         while (values[up] > value) {
            up -= 2;
         }

         if (down < up) {
            float tempValue = values[down];
            values[down] = values[up];
            values[up] = tempValue;
            tempValue = values[down + 1];
            values[down + 1] = values[up + 1];
            values[up + 1] = tempValue;
            short tempIndex = originalIndices[down / 2];
            originalIndices[down / 2] = originalIndices[up / 2];
            originalIndices[up / 2] = tempIndex;
         }
      }

      values[lower] = values[up];
      values[up] = value;
      float tempValue = values[lower + 1];
      values[lower + 1] = values[up + 1];
      values[up + 1] = tempValue;
      short tempIndex = originalIndices[lower / 2];
      originalIndices[lower / 2] = originalIndices[up / 2];
      originalIndices[up / 2] = tempIndex;
      return up;
   }

   public void trim(ShortArray triangles, float[] points, float[] hull, int offset, int count) {
      short[] trianglesArray = triangles.items;

      for (int i = triangles.size - 1; i >= 0; i -= 3) {
         int p1 = trianglesArray[i - 2] * 2;
         int p2 = trianglesArray[i - 1] * 2;
         int p3 = trianglesArray[i] * 2;
         GeometryUtils.triangleCentroid(points[p1], points[p1 + 1], points[p2], points[p2 + 1], points[p3], points[p3 + 1], this.centroid);
         if (!Intersector.isPointInPolygon(hull, offset, count, this.centroid.x, this.centroid.y)) {
            triangles.removeIndex(i);
            triangles.removeIndex(i - 1);
            triangles.removeIndex(i - 2);
         }
      }
   }
}
