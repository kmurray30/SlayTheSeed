package com.badlogic.gdx;

import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.GLVersion;

public interface Graphics {
   boolean isGL30Available();

   GL20 getGL20();

   GL30 getGL30();

   int getWidth();

   int getHeight();

   int getBackBufferWidth();

   int getBackBufferHeight();

   long getFrameId();

   float getDeltaTime();

   float getRawDeltaTime();

   int getFramesPerSecond();

   Graphics.GraphicsType getType();

   GLVersion getGLVersion();

   float getPpiX();

   float getPpiY();

   float getPpcX();

   float getPpcY();

   float getDensity();

   boolean supportsDisplayModeChange();

   Graphics.Monitor getPrimaryMonitor();

   Graphics.Monitor getMonitor();

   Graphics.Monitor[] getMonitors();

   Graphics.DisplayMode[] getDisplayModes();

   Graphics.DisplayMode[] getDisplayModes(Graphics.Monitor var1);

   Graphics.DisplayMode getDisplayMode();

   Graphics.DisplayMode getDisplayMode(Graphics.Monitor var1);

   boolean setFullscreenMode(Graphics.DisplayMode var1);

   boolean setWindowedMode(int var1, int var2);

   void setTitle(String var1);

   void setUndecorated(boolean var1);

   void setResizable(boolean var1);

   void setVSync(boolean var1);

   Graphics.BufferFormat getBufferFormat();

   boolean supportsExtension(String var1);

   void setContinuousRendering(boolean var1);

   boolean isContinuousRendering();

   void requestRendering();

   boolean isFullscreen();

   Cursor newCursor(Pixmap var1, int var2, int var3);

   void setCursor(Cursor var1);

   void setSystemCursor(Cursor.SystemCursor var1);

   public static class BufferFormat {
      public final int r;
      public final int g;
      public final int b;
      public final int a;
      public final int depth;
      public final int stencil;
      public final int samples;
      public final boolean coverageSampling;

      public BufferFormat(int r, int g, int b, int a, int depth, int stencil, int samples, boolean coverageSampling) {
         this.r = r;
         this.g = g;
         this.b = b;
         this.a = a;
         this.depth = depth;
         this.stencil = stencil;
         this.samples = samples;
         this.coverageSampling = coverageSampling;
      }

      @Override
      public String toString() {
         return "r: "
            + this.r
            + ", g: "
            + this.g
            + ", b: "
            + this.b
            + ", a: "
            + this.a
            + ", depth: "
            + this.depth
            + ", stencil: "
            + this.stencil
            + ", num samples: "
            + this.samples
            + ", coverage sampling: "
            + this.coverageSampling;
      }
   }

   public static class DisplayMode {
      public final int width;
      public final int height;
      public final int refreshRate;
      public final int bitsPerPixel;

      protected DisplayMode(int width, int height, int refreshRate, int bitsPerPixel) {
         this.width = width;
         this.height = height;
         this.refreshRate = refreshRate;
         this.bitsPerPixel = bitsPerPixel;
      }

      @Override
      public String toString() {
         return this.width + "x" + this.height + ", bpp: " + this.bitsPerPixel + ", hz: " + this.refreshRate;
      }
   }

   public static enum GraphicsType {
      AndroidGL,
      LWJGL,
      WebGL,
      iOSGL,
      JGLFW,
      Mock,
      LWJGL3;
   }

   public static class Monitor {
      public final int virtualX;
      public final int virtualY;
      public final String name;

      protected Monitor(int virtualX, int virtualY, String name) {
         this.virtualX = virtualX;
         this.virtualY = virtualY;
         this.name = name;
      }
   }
}
