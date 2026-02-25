package com.badlogic.gdx.graphics.g3d.utils;

import com.badlogic.gdx.graphics.GLTexture;

public interface TextureBinder {
   void begin();

   void end();

   int bind(TextureDescriptor var1);

   int bind(GLTexture var1);

   int getBindCount();

   int getReuseCount();

   void resetCounts();
}
