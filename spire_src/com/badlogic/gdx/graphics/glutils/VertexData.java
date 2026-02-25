package com.badlogic.gdx.graphics.glutils;

import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.utils.Disposable;
import java.nio.FloatBuffer;

public interface VertexData extends Disposable {
   int getNumVertices();

   int getNumMaxVertices();

   VertexAttributes getAttributes();

   void setVertices(float[] var1, int var2, int var3);

   void updateVertices(int var1, float[] var2, int var3, int var4);

   FloatBuffer getBuffer();

   void bind(ShaderProgram var1);

   void bind(ShaderProgram var1, int[] var2);

   void unbind(ShaderProgram var1);

   void unbind(ShaderProgram var1, int[] var2);

   void invalidate();

   @Override
   void dispose();
}
