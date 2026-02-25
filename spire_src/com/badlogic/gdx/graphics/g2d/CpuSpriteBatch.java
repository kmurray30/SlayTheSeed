package com.badlogic.gdx.graphics.g2d;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class CpuSpriteBatch extends SpriteBatch {
   private final Matrix4 virtualMatrix = new Matrix4();
   private final Affine2 adjustAffine = new Affine2();
   private boolean adjustNeeded;
   private boolean haveIdentityRealMatrix = true;
   private final Affine2 tmpAffine = new Affine2();

   public CpuSpriteBatch() {
      this(1000);
   }

   public CpuSpriteBatch(int size) {
      this(size, null);
   }

   public CpuSpriteBatch(int size, ShaderProgram defaultShader) {
      super(size, defaultShader);
   }

   public void flushAndSyncTransformMatrix() {
      this.flush();
      if (this.adjustNeeded) {
         this.haveIdentityRealMatrix = checkIdt(this.virtualMatrix);
         if (!this.haveIdentityRealMatrix && this.virtualMatrix.det() == 0.0F) {
            throw new GdxRuntimeException("Transform matrix is singular, can't sync");
         }

         this.adjustNeeded = false;
         super.setTransformMatrix(this.virtualMatrix);
      }
   }

   @Override
   public Matrix4 getTransformMatrix() {
      return this.adjustNeeded ? this.virtualMatrix : super.getTransformMatrix();
   }

   @Override
   public void setTransformMatrix(Matrix4 transform) {
      Matrix4 realMatrix = super.getTransformMatrix();
      if (checkEqual(realMatrix, transform)) {
         this.adjustNeeded = false;
      } else if (this.isDrawing()) {
         this.virtualMatrix.setAsAffine(transform);
         this.adjustNeeded = true;
         if (this.haveIdentityRealMatrix) {
            this.adjustAffine.set(transform);
         } else {
            this.tmpAffine.set(transform);
            this.adjustAffine.set(realMatrix).inv().mul(this.tmpAffine);
         }
      } else {
         realMatrix.setAsAffine(transform);
         this.haveIdentityRealMatrix = checkIdt(realMatrix);
      }
   }

   public void setTransformMatrix(Affine2 transform) {
      Matrix4 realMatrix = super.getTransformMatrix();
      if (checkEqual(realMatrix, transform)) {
         this.adjustNeeded = false;
      } else {
         this.virtualMatrix.setAsAffine(transform);
         if (this.isDrawing()) {
            this.adjustNeeded = true;
            if (this.haveIdentityRealMatrix) {
               this.adjustAffine.set(transform);
            } else {
               this.adjustAffine.set(realMatrix).inv().mul(transform);
            }
         } else {
            realMatrix.setAsAffine(transform);
            this.haveIdentityRealMatrix = checkIdt(realMatrix);
         }
      }
   }

   @Override
   public void draw(
      Texture texture,
      float x,
      float y,
      float originX,
      float originY,
      float width,
      float height,
      float scaleX,
      float scaleY,
      float rotation,
      int srcX,
      int srcY,
      int srcWidth,
      int srcHeight,
      boolean flipX,
      boolean flipY
   ) {
      if (!this.adjustNeeded) {
         super.draw(texture, x, y, originX, originY, width, height, scaleX, scaleY, rotation, srcX, srcY, srcWidth, srcHeight, flipX, flipY);
      } else {
         this.drawAdjusted(texture, x, y, originX, originY, width, height, scaleX, scaleY, rotation, srcX, srcY, srcWidth, srcHeight, flipX, flipY);
      }
   }

   @Override
   public void draw(Texture texture, float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
      if (!this.adjustNeeded) {
         super.draw(texture, x, y, width, height, srcX, srcY, srcWidth, srcHeight, flipX, flipY);
      } else {
         this.drawAdjusted(texture, x, y, 0.0F, 0.0F, width, height, 1.0F, 1.0F, 0.0F, srcX, srcY, srcWidth, srcHeight, flipX, flipY);
      }
   }

   @Override
   public void draw(Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {
      if (!this.adjustNeeded) {
         super.draw(texture, x, y, srcX, srcY, srcWidth, srcHeight);
      } else {
         this.drawAdjusted(texture, x, y, 0.0F, 0.0F, srcWidth, srcHeight, 1.0F, 1.0F, 0.0F, srcX, srcY, srcWidth, srcHeight, false, false);
      }
   }

   @Override
   public void draw(Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2) {
      if (!this.adjustNeeded) {
         super.draw(texture, x, y, width, height, u, v, u2, v2);
      } else {
         this.drawAdjustedUV(texture, x, y, 0.0F, 0.0F, width, height, 1.0F, 1.0F, 0.0F, u, v, u2, v2, false, false);
      }
   }

   @Override
   public void draw(Texture texture, float x, float y) {
      if (!this.adjustNeeded) {
         super.draw(texture, x, y);
      } else {
         this.drawAdjusted(texture, x, y, 0.0F, 0.0F, texture.getWidth(), texture.getHeight(), 1.0F, 1.0F, 0.0F, 0, 1, 1, 0, false, false);
      }
   }

   @Override
   public void draw(Texture texture, float x, float y, float width, float height) {
      if (!this.adjustNeeded) {
         super.draw(texture, x, y, width, height);
      } else {
         this.drawAdjusted(texture, x, y, 0.0F, 0.0F, width, height, 1.0F, 1.0F, 0.0F, 0, 1, 1, 0, false, false);
      }
   }

   @Override
   public void draw(TextureRegion region, float x, float y) {
      if (!this.adjustNeeded) {
         super.draw(region, x, y);
      } else {
         this.drawAdjusted(region, x, y, 0.0F, 0.0F, region.getRegionWidth(), region.getRegionHeight(), 1.0F, 1.0F, 0.0F);
      }
   }

   @Override
   public void draw(TextureRegion region, float x, float y, float width, float height) {
      if (!this.adjustNeeded) {
         super.draw(region, x, y, width, height);
      } else {
         this.drawAdjusted(region, x, y, 0.0F, 0.0F, width, height, 1.0F, 1.0F, 0.0F);
      }
   }

   @Override
   public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {
      if (!this.adjustNeeded) {
         super.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation);
      } else {
         this.drawAdjusted(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation);
      }
   }

   @Override
   public void draw(
      TextureRegion region,
      float x,
      float y,
      float originX,
      float originY,
      float width,
      float height,
      float scaleX,
      float scaleY,
      float rotation,
      boolean clockwise
   ) {
      if (!this.adjustNeeded) {
         super.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation, clockwise);
      } else {
         this.drawAdjusted(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation, clockwise);
      }
   }

   @Override
   public void draw(Texture texture, float[] spriteVertices, int offset, int count) {
      if (count % 20 != 0) {
         throw new GdxRuntimeException("invalid vertex count");
      } else {
         if (!this.adjustNeeded) {
            super.draw(texture, spriteVertices, offset, count);
         } else {
            this.drawAdjusted(texture, spriteVertices, offset, count);
         }
      }
   }

   @Override
   public void draw(TextureRegion region, float width, float height, Affine2 transform) {
      if (!this.adjustNeeded) {
         super.draw(region, width, height, transform);
      } else {
         this.drawAdjusted(region, width, height, transform);
      }
   }

   private void drawAdjusted(
      TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation
   ) {
      this.drawAdjustedUV(
         region.texture, x, y, originX, originY, width, height, scaleX, scaleY, rotation, region.u, region.v2, region.u2, region.v, false, false
      );
   }

   private void drawAdjusted(
      Texture texture,
      float x,
      float y,
      float originX,
      float originY,
      float width,
      float height,
      float scaleX,
      float scaleY,
      float rotation,
      int srcX,
      int srcY,
      int srcWidth,
      int srcHeight,
      boolean flipX,
      boolean flipY
   ) {
      float invTexWidth = 1.0F / texture.getWidth();
      float invTexHeight = 1.0F / texture.getHeight();
      float u = srcX * invTexWidth;
      float v = (srcY + srcHeight) * invTexHeight;
      float u2 = (srcX + srcWidth) * invTexWidth;
      float v2 = srcY * invTexHeight;
      this.drawAdjustedUV(texture, x, y, originX, originY, width, height, scaleX, scaleY, rotation, u, v, u2, v2, flipX, flipY);
   }

   private void drawAdjustedUV(
      Texture texture,
      float x,
      float y,
      float originX,
      float originY,
      float width,
      float height,
      float scaleX,
      float scaleY,
      float rotation,
      float u,
      float v,
      float u2,
      float v2,
      boolean flipX,
      boolean flipY
   ) {
      if (!this.drawing) {
         throw new IllegalStateException("CpuSpriteBatch.begin must be called before draw.");
      } else {
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         } else if (this.idx == this.vertices.length) {
            super.flush();
         }

         float worldOriginX = x + originX;
         float worldOriginY = y + originY;
         float fx = -originX;
         float fy = -originY;
         float fx2 = width - originX;
         float fy2 = height - originY;
         if (scaleX != 1.0F || scaleY != 1.0F) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
         }

         float x1;
         float y1;
         float x2;
         float y2;
         float x3;
         float y3;
         float x4;
         float y4;
         if (rotation != 0.0F) {
            float cos = MathUtils.cosDeg(rotation);
            float sin = MathUtils.sinDeg(rotation);
            x1 = cos * fx - sin * fy;
            y1 = sin * fx + cos * fy;
            x2 = cos * fx - sin * fy2;
            y2 = sin * fx + cos * fy2;
            x3 = cos * fx2 - sin * fy2;
            y3 = sin * fx2 + cos * fy2;
            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
         } else {
            x1 = fx;
            y1 = fy;
            x2 = fx;
            y2 = fy2;
            x3 = fx2;
            y3 = fy2;
            x4 = fx2;
            y4 = fy;
         }

         x1 += worldOriginX;
         y1 += worldOriginY;
         x2 += worldOriginX;
         y2 += worldOriginY;
         x3 += worldOriginX;
         y3 += worldOriginY;
         x4 += worldOriginX;
         y4 += worldOriginY;
         if (flipX) {
            float tmp = u;
            u = u2;
            u2 = tmp;
         }

         if (flipY) {
            float tmp = v;
            v = v2;
            v2 = tmp;
         }

         Affine2 t = this.adjustAffine;
         this.vertices[this.idx + 0] = t.m00 * x1 + t.m01 * y1 + t.m02;
         this.vertices[this.idx + 1] = t.m10 * x1 + t.m11 * y1 + t.m12;
         this.vertices[this.idx + 2] = this.color;
         this.vertices[this.idx + 3] = u;
         this.vertices[this.idx + 4] = v;
         this.vertices[this.idx + 5] = t.m00 * x2 + t.m01 * y2 + t.m02;
         this.vertices[this.idx + 6] = t.m10 * x2 + t.m11 * y2 + t.m12;
         this.vertices[this.idx + 7] = this.color;
         this.vertices[this.idx + 8] = u;
         this.vertices[this.idx + 9] = v2;
         this.vertices[this.idx + 10] = t.m00 * x3 + t.m01 * y3 + t.m02;
         this.vertices[this.idx + 11] = t.m10 * x3 + t.m11 * y3 + t.m12;
         this.vertices[this.idx + 12] = this.color;
         this.vertices[this.idx + 13] = u2;
         this.vertices[this.idx + 14] = v2;
         this.vertices[this.idx + 15] = t.m00 * x4 + t.m01 * y4 + t.m02;
         this.vertices[this.idx + 16] = t.m10 * x4 + t.m11 * y4 + t.m12;
         this.vertices[this.idx + 17] = this.color;
         this.vertices[this.idx + 18] = u2;
         this.vertices[this.idx + 19] = v;
         this.idx += 20;
      }
   }

   private void drawAdjusted(
      TextureRegion region,
      float x,
      float y,
      float originX,
      float originY,
      float width,
      float height,
      float scaleX,
      float scaleY,
      float rotation,
      boolean clockwise
   ) {
      if (!this.drawing) {
         throw new IllegalStateException("CpuSpriteBatch.begin must be called before draw.");
      } else {
         if (region.texture != this.lastTexture) {
            this.switchTexture(region.texture);
         } else if (this.idx == this.vertices.length) {
            super.flush();
         }

         float worldOriginX = x + originX;
         float worldOriginY = y + originY;
         float fx = -originX;
         float fy = -originY;
         float fx2 = width - originX;
         float fy2 = height - originY;
         if (scaleX != 1.0F || scaleY != 1.0F) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
         }

         float x1;
         float y1;
         float x2;
         float y2;
         float x3;
         float y3;
         float x4;
         float y4;
         if (rotation != 0.0F) {
            float cos = MathUtils.cosDeg(rotation);
            float sin = MathUtils.sinDeg(rotation);
            x1 = cos * fx - sin * fy;
            y1 = sin * fx + cos * fy;
            x2 = cos * fx - sin * fy2;
            y2 = sin * fx + cos * fy2;
            x3 = cos * fx2 - sin * fy2;
            y3 = sin * fx2 + cos * fy2;
            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
         } else {
            x1 = fx;
            y1 = fy;
            x2 = fx;
            y2 = fy2;
            x3 = fx2;
            y3 = fy2;
            x4 = fx2;
            y4 = fy;
         }

         x1 += worldOriginX;
         y1 += worldOriginY;
         x2 += worldOriginX;
         y2 += worldOriginY;
         x3 += worldOriginX;
         y3 += worldOriginY;
         x4 += worldOriginX;
         y4 += worldOriginY;
         float u2;
         float v2;
         float u3;
         float v3;
         float u4;
         float v4;
         float u1;
         float v1;
         if (clockwise) {
            u1 = region.u2;
            v1 = region.v2;
            u2 = region.u;
            v2 = region.v2;
            u3 = region.u;
            v3 = region.v;
            u4 = region.u2;
            v4 = region.v;
         } else {
            u1 = region.u;
            v1 = region.v;
            u2 = region.u2;
            v2 = region.v;
            u3 = region.u2;
            v3 = region.v2;
            u4 = region.u;
            v4 = region.v2;
         }

         Affine2 t = this.adjustAffine;
         this.vertices[this.idx + 0] = t.m00 * x1 + t.m01 * y1 + t.m02;
         this.vertices[this.idx + 1] = t.m10 * x1 + t.m11 * y1 + t.m12;
         this.vertices[this.idx + 2] = this.color;
         this.vertices[this.idx + 3] = u1;
         this.vertices[this.idx + 4] = v1;
         this.vertices[this.idx + 5] = t.m00 * x2 + t.m01 * y2 + t.m02;
         this.vertices[this.idx + 6] = t.m10 * x2 + t.m11 * y2 + t.m12;
         this.vertices[this.idx + 7] = this.color;
         this.vertices[this.idx + 8] = u2;
         this.vertices[this.idx + 9] = v2;
         this.vertices[this.idx + 10] = t.m00 * x3 + t.m01 * y3 + t.m02;
         this.vertices[this.idx + 11] = t.m10 * x3 + t.m11 * y3 + t.m12;
         this.vertices[this.idx + 12] = this.color;
         this.vertices[this.idx + 13] = u3;
         this.vertices[this.idx + 14] = v3;
         this.vertices[this.idx + 15] = t.m00 * x4 + t.m01 * y4 + t.m02;
         this.vertices[this.idx + 16] = t.m10 * x4 + t.m11 * y4 + t.m12;
         this.vertices[this.idx + 17] = this.color;
         this.vertices[this.idx + 18] = u4;
         this.vertices[this.idx + 19] = v4;
         this.idx += 20;
      }
   }

   private void drawAdjusted(TextureRegion region, float width, float height, Affine2 transform) {
      if (!this.drawing) {
         throw new IllegalStateException("CpuSpriteBatch.begin must be called before draw.");
      } else {
         if (region.texture != this.lastTexture) {
            this.switchTexture(region.texture);
         } else if (this.idx == this.vertices.length) {
            super.flush();
         }

         float x1 = transform.m02;
         float y1 = transform.m12;
         float x2 = transform.m01 * height + transform.m02;
         float y2 = transform.m11 * height + transform.m12;
         float x3 = transform.m00 * width + transform.m01 * height + transform.m02;
         float y3 = transform.m10 * width + transform.m11 * height + transform.m12;
         float x4 = transform.m00 * width + transform.m02;
         float y4 = transform.m10 * width + transform.m12;
         float u = region.u;
         float v = region.v2;
         float u2 = region.u2;
         float v2 = region.v;
         Affine2 t = this.adjustAffine;
         this.vertices[this.idx + 0] = t.m00 * x1 + t.m01 * y1 + t.m02;
         this.vertices[this.idx + 1] = t.m10 * x1 + t.m11 * y1 + t.m12;
         this.vertices[this.idx + 2] = this.color;
         this.vertices[this.idx + 3] = u;
         this.vertices[this.idx + 4] = v;
         this.vertices[this.idx + 5] = t.m00 * x2 + t.m01 * y2 + t.m02;
         this.vertices[this.idx + 6] = t.m10 * x2 + t.m11 * y2 + t.m12;
         this.vertices[this.idx + 7] = this.color;
         this.vertices[this.idx + 8] = u;
         this.vertices[this.idx + 9] = v2;
         this.vertices[this.idx + 10] = t.m00 * x3 + t.m01 * y3 + t.m02;
         this.vertices[this.idx + 11] = t.m10 * x3 + t.m11 * y3 + t.m12;
         this.vertices[this.idx + 12] = this.color;
         this.vertices[this.idx + 13] = u2;
         this.vertices[this.idx + 14] = v2;
         this.vertices[this.idx + 15] = t.m00 * x4 + t.m01 * y4 + t.m02;
         this.vertices[this.idx + 16] = t.m10 * x4 + t.m11 * y4 + t.m12;
         this.vertices[this.idx + 17] = this.color;
         this.vertices[this.idx + 18] = u2;
         this.vertices[this.idx + 19] = v;
         this.idx += 20;
      }
   }

   private void drawAdjusted(Texture texture, float[] spriteVertices, int offset, int count) {
      if (!this.drawing) {
         throw new IllegalStateException("CpuSpriteBatch.begin must be called before draw.");
      } else {
         if (texture != this.lastTexture) {
            this.switchTexture(texture);
         }

         Affine2 t = this.adjustAffine;
         int copyCount = Math.min(this.vertices.length - this.idx, count);

         do {
            for (count -= copyCount; copyCount > 0; copyCount -= 5) {
               float x = spriteVertices[offset];
               float y = spriteVertices[offset + 1];
               this.vertices[this.idx] = t.m00 * x + t.m01 * y + t.m02;
               this.vertices[this.idx + 1] = t.m10 * x + t.m11 * y + t.m12;
               this.vertices[this.idx + 2] = spriteVertices[offset + 2];
               this.vertices[this.idx + 3] = spriteVertices[offset + 3];
               this.vertices[this.idx + 4] = spriteVertices[offset + 4];
               this.idx += 5;
               offset += 5;
            }

            if (count > 0) {
               super.flush();
               copyCount = Math.min(this.vertices.length, count);
            }
         } while (count > 0);
      }
   }

   private static boolean checkEqual(Matrix4 a, Matrix4 b) {
      return a == b
         ? true
         : a.val[0] == b.val[0] && a.val[1] == b.val[1] && a.val[4] == b.val[4] && a.val[5] == b.val[5] && a.val[12] == b.val[12] && a.val[13] == b.val[13];
   }

   private static boolean checkEqual(Matrix4 matrix, Affine2 affine) {
      float[] val = matrix.getValues();
      return val[0] == affine.m00 && val[1] == affine.m10 && val[4] == affine.m01 && val[5] == affine.m11 && val[12] == affine.m02 && val[13] == affine.m12;
   }

   private static boolean checkIdt(Matrix4 matrix) {
      float[] val = matrix.getValues();
      return val[0] == 1.0F && val[1] == 0.0F && val[4] == 0.0F && val[5] == 1.0F && val[12] == 0.0F && val[13] == 0.0F;
   }
}
