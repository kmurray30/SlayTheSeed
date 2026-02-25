package com.badlogic.gdx.graphics.glutils;

import com.badlogic.gdx.Gdx;

public class HdpiUtils {
   public static void glScissor(int x, int y, int width, int height) {
      if (Gdx.graphics.getWidth() == Gdx.graphics.getBackBufferWidth() && Gdx.graphics.getHeight() == Gdx.graphics.getBackBufferHeight()) {
         Gdx.gl.glScissor(x, y, width, height);
      } else {
         Gdx.gl.glScissor(toBackBufferX(x), toBackBufferY(y), toBackBufferX(width), toBackBufferY(height));
      }
   }

   public static void glViewport(int x, int y, int width, int height) {
      if (Gdx.graphics.getWidth() == Gdx.graphics.getBackBufferWidth() && Gdx.graphics.getHeight() == Gdx.graphics.getBackBufferHeight()) {
         Gdx.gl.glViewport(x, y, width, height);
      } else {
         Gdx.gl.glViewport(toBackBufferX(x), toBackBufferY(y), toBackBufferX(width), toBackBufferY(height));
      }
   }

   public static int toLogicalX(int backBufferX) {
      return (int)((float)(backBufferX * Gdx.graphics.getWidth()) / Gdx.graphics.getBackBufferWidth());
   }

   public static int toLogicalY(int backBufferY) {
      return (int)((float)(backBufferY * Gdx.graphics.getHeight()) / Gdx.graphics.getBackBufferHeight());
   }

   public static int toBackBufferX(int logicalX) {
      return (int)((float)(logicalX * Gdx.graphics.getBackBufferWidth()) / Gdx.graphics.getWidth());
   }

   public static int toBackBufferY(int logicalY) {
      return (int)((float)(logicalY * Gdx.graphics.getBackBufferHeight()) / Gdx.graphics.getHeight());
   }
}
