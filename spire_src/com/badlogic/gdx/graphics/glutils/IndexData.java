package com.badlogic.gdx.graphics.glutils;

import com.badlogic.gdx.utils.Disposable;
import java.nio.ShortBuffer;

public interface IndexData extends Disposable {
   int getNumIndices();

   int getNumMaxIndices();

   void setIndices(short[] var1, int var2, int var3);

   void setIndices(ShortBuffer var1);

   void updateIndices(int var1, short[] var2, int var3, int var4);

   ShortBuffer getBuffer();

   void bind();

   void unbind();

   void invalidate();

   @Override
   void dispose();
}
