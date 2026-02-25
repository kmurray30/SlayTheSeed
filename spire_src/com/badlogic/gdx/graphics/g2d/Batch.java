package com.badlogic.gdx.graphics.g2d;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

public interface Batch extends Disposable {
   int X1 = 0;
   int Y1 = 1;
   int C1 = 2;
   int U1 = 3;
   int V1 = 4;
   int X2 = 5;
   int Y2 = 6;
   int C2 = 7;
   int U2 = 8;
   int V2 = 9;
   int X3 = 10;
   int Y3 = 11;
   int C3 = 12;
   int U3 = 13;
   int V3 = 14;
   int X4 = 15;
   int Y4 = 16;
   int C4 = 17;
   int U4 = 18;
   int V4 = 19;

   void begin();

   void end();

   void setColor(Color var1);

   void setColor(float var1, float var2, float var3, float var4);

   void setColor(float var1);

   Color getColor();

   float getPackedColor();

   void draw(
      Texture var1,
      float var2,
      float var3,
      float var4,
      float var5,
      float var6,
      float var7,
      float var8,
      float var9,
      float var10,
      int var11,
      int var12,
      int var13,
      int var14,
      boolean var15,
      boolean var16
   );

   void draw(Texture var1, float var2, float var3, float var4, float var5, int var6, int var7, int var8, int var9, boolean var10, boolean var11);

   void draw(Texture var1, float var2, float var3, int var4, int var5, int var6, int var7);

   void draw(Texture var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8, float var9);

   void draw(Texture var1, float var2, float var3);

   void draw(Texture var1, float var2, float var3, float var4, float var5);

   void draw(Texture var1, float[] var2, int var3, int var4);

   void draw(TextureRegion var1, float var2, float var3);

   void draw(TextureRegion var1, float var2, float var3, float var4, float var5);

   void draw(TextureRegion var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8, float var9, float var10);

   void draw(TextureRegion var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8, float var9, float var10, boolean var11);

   void draw(TextureRegion var1, float var2, float var3, Affine2 var4);

   void flush();

   void disableBlending();

   void enableBlending();

   void setBlendFunction(int var1, int var2);

   int getBlendSrcFunc();

   int getBlendDstFunc();

   Matrix4 getProjectionMatrix();

   Matrix4 getTransformMatrix();

   void setProjectionMatrix(Matrix4 var1);

   void setTransformMatrix(Matrix4 var1);

   void setShader(ShaderProgram var1);

   ShaderProgram getShader();

   boolean isBlendingEnabled();

   boolean isDrawing();
}
