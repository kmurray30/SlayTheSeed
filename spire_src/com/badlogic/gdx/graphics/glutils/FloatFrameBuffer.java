package com.badlogic.gdx.graphics.glutils;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

public class FloatFrameBuffer extends FrameBuffer {
   public FloatFrameBuffer(int width, int height, boolean hasDepth) {
      super(null, width, height, hasDepth);
   }

   @Override
   protected Texture createColorTexture() {
      FloatTextureData data = new FloatTextureData(this.width, this.height);
      Texture result = new Texture(data);
      if (Gdx.app.getType() != Application.ApplicationType.Desktop && Gdx.app.getType() != Application.ApplicationType.Applet) {
         result.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
      } else {
         result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
      }

      result.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
      return result;
   }

   @Override
   protected void disposeColorTexture(Texture colorTexture) {
      colorTexture.dispose();
   }
}
