package com.badlogic.gdx.backends.lwjgl;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.SharedLibraryLoader;
import java.awt.Canvas;
import java.awt.Toolkit;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;

public class LwjglGraphics implements Graphics {
   static Array<String> extensions;
   static GLVersion glVersion;
   GL20 gl20;
   GL30 gl30;
   long frameId = -1L;
   float deltaTime = 0.0F;
   long frameStart = 0L;
   int frames = 0;
   int fps;
   long lastTime = System.nanoTime();
   Canvas canvas;
   boolean vsync = false;
   boolean resize = false;
   LwjglApplicationConfiguration config;
   Graphics.BufferFormat bufferFormat = new Graphics.BufferFormat(8, 8, 8, 8, 16, 8, 0, false);
   volatile boolean isContinuous = true;
   volatile boolean requestRendering = false;
   boolean softwareMode;
   boolean usingGL30;

   LwjglGraphics(LwjglApplicationConfiguration config) {
      this.config = config;
   }

   LwjglGraphics(Canvas canvas) {
      this.config = new LwjglApplicationConfiguration();
      this.config.width = canvas.getWidth();
      this.config.height = canvas.getHeight();
      this.canvas = canvas;
   }

   LwjglGraphics(Canvas canvas, LwjglApplicationConfiguration config) {
      this.config = config;
      this.canvas = canvas;
   }

   @Override
   public GL20 getGL20() {
      return this.gl20;
   }

   @Override
   public int getHeight() {
      return this.canvas != null ? Math.max(1, this.canvas.getHeight()) : (int)(Display.getHeight() * Display.getPixelScaleFactor());
   }

   @Override
   public int getWidth() {
      return this.canvas != null ? Math.max(1, this.canvas.getWidth()) : (int)(Display.getWidth() * Display.getPixelScaleFactor());
   }

   @Override
   public int getBackBufferWidth() {
      return this.getWidth();
   }

   @Override
   public int getBackBufferHeight() {
      return this.getHeight();
   }

   public boolean isGL20Available() {
      return this.gl20 != null;
   }

   @Override
   public long getFrameId() {
      return this.frameId;
   }

   @Override
   public float getDeltaTime() {
      return this.deltaTime;
   }

   @Override
   public float getRawDeltaTime() {
      return this.deltaTime;
   }

   @Override
   public Graphics.GraphicsType getType() {
      return Graphics.GraphicsType.LWJGL;
   }

   @Override
   public GLVersion getGLVersion() {
      return glVersion;
   }

   @Override
   public int getFramesPerSecond() {
      return this.fps;
   }

   void updateTime() {
      long time = System.nanoTime();
      this.deltaTime = (float)(time - this.lastTime) / 1.0E9F;
      this.lastTime = time;
      if (time - this.frameStart >= 1000000000L) {
         this.fps = this.frames;
         this.frames = 0;
         this.frameStart = time;
      }

      this.frames++;
   }

   void setupDisplay() throws LWJGLException {
      if (this.config.useHDPI) {
         System.setProperty("org.lwjgl.opengl.Display.enableHighDPI", "true");
      }

      if (this.canvas != null) {
         Display.setParent(this.canvas);
      } else {
         boolean displayCreated = false;
         if (!this.config.fullscreen) {
            displayCreated = this.setWindowedMode(this.config.width, this.config.height);
         } else {
            Graphics.DisplayMode bestMode = null;

            for (Graphics.DisplayMode mode : this.getDisplayModes()) {
               if (mode.width == this.config.width
                  && mode.height == this.config.height
                  && (bestMode == null || bestMode.refreshRate < this.getDisplayMode().refreshRate)) {
                  bestMode = mode;
               }
            }

            if (bestMode == null) {
               bestMode = this.getDisplayMode();
            }

            displayCreated = this.setFullscreenMode(bestMode);
         }

         if (!displayCreated) {
            if (this.config.setDisplayModeCallback != null) {
               this.config = this.config.setDisplayModeCallback.onFailure(this.config);
               if (this.config != null) {
                  displayCreated = this.setWindowedMode(this.config.width, this.config.height);
               }
            }

            if (!displayCreated) {
               throw new GdxRuntimeException(
                  "Couldn't set display mode " + this.config.width + "x" + this.config.height + ", fullscreen: " + this.config.fullscreen
               );
            }
         }

         if (this.config.iconPaths.size > 0) {
            ByteBuffer[] icons = new ByteBuffer[this.config.iconPaths.size];
            int i = 0;

            for (int n = this.config.iconPaths.size; i < n; i++) {
               Pixmap pixmap = new Pixmap(Gdx.files.getFileHandle(this.config.iconPaths.get(i), this.config.iconFileTypes.get(i)));
               if (pixmap.getFormat() != Pixmap.Format.RGBA8888) {
                  Pixmap rgba = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), Pixmap.Format.RGBA8888);
                  rgba.drawPixmap(pixmap, 0, 0);
                  pixmap.dispose();
                  pixmap = rgba;
               }

               icons[i] = ByteBuffer.allocateDirect(pixmap.getPixels().limit());
               ((Buffer)icons[i].put(pixmap.getPixels())).flip();
               pixmap.dispose();
            }

            Display.setIcon(icons);
         }
      }

      Display.setTitle(this.config.title);
      Display.setResizable(this.config.resizable);
      Display.setInitialBackground(this.config.initialBackgroundColor.r, this.config.initialBackgroundColor.g, this.config.initialBackgroundColor.b);
      Display.setLocation(this.config.x, this.config.y);
      this.createDisplayPixelFormat(this.config.useGL30, this.config.gles30ContextMajorVersion, this.config.gles30ContextMinorVersion);
      this.initiateGL();
   }

   void initiateGL() {
      extractVersion();
      extractExtensions();
      this.initiateGLInstances();
   }

   private static void extractVersion() {
      String versionString = GL11.glGetString(7938);
      String vendorString = GL11.glGetString(7936);
      String rendererString = GL11.glGetString(7937);
      glVersion = new GLVersion(Application.ApplicationType.Desktop, versionString, vendorString, rendererString);
   }

   private static void extractExtensions() {
      extensions = new Array<>();
      if (glVersion.isVersionEqualToOrHigher(3, 2)) {
         int numExtensions = GL11.glGetInteger(33309);

         for (int i = 0; i < numExtensions; i++) {
            extensions.add(org.lwjgl.opengl.GL30.glGetStringi(7939, i));
         }
      } else {
         extensions.addAll(GL11.glGetString(7939).split(" "));
      }
   }

   private static boolean fullCompatibleWithGLES3() {
      return glVersion.isVersionEqualToOrHigher(4, 3);
   }

   private static boolean fullCompatibleWithGLES2() {
      return glVersion.isVersionEqualToOrHigher(4, 1) || extensions.contains("GL_ARB_ES2_compatibility", false);
   }

   private static boolean supportsFBO() {
      return glVersion.isVersionEqualToOrHigher(3, 0)
         || extensions.contains("GL_EXT_framebuffer_object", false)
         || extensions.contains("GL_ARB_framebuffer_object", false);
   }

   private void createDisplayPixelFormat(boolean useGL30, int gles30ContextMajor, int gles30ContextMinor) {
      try {
         if (useGL30) {
            ContextAttribs context = new ContextAttribs(gles30ContextMajor, gles30ContextMinor).withForwardCompatible(false).withProfileCore(true);

            try {
               Display.create(
                  new PixelFormat(this.config.r + this.config.g + this.config.b, this.config.a, this.config.depth, this.config.stencil, this.config.samples),
                  context
               );
            } catch (Exception var9) {
               System.out.println("LwjglGraphics: OpenGL " + gles30ContextMajor + "." + gles30ContextMinor + "+ core profile (GLES 3.0) not supported.");
               this.createDisplayPixelFormat(false, gles30ContextMajor, gles30ContextMinor);
               return;
            }

            System.out
               .println(
                  "LwjglGraphics: created OpenGL " + gles30ContextMajor + "." + gles30ContextMinor + "+ core profile (GLES 3.0) context. This is experimental!"
               );
            this.usingGL30 = true;
         } else {
            Display.create(
               new PixelFormat(this.config.r + this.config.g + this.config.b, this.config.a, this.config.depth, this.config.stencil, this.config.samples)
            );
            this.usingGL30 = false;
         }

         this.bufferFormat = new Graphics.BufferFormat(
            this.config.r, this.config.g, this.config.b, this.config.a, this.config.depth, this.config.stencil, this.config.samples, false
         );
      } catch (Exception var12) {
         Display.destroy();

         try {
            Thread.sleep(200L);
         } catch (InterruptedException var8) {
         }

         try {
            Display.create(new PixelFormat(0, 16, 8));
            if (this.getDisplayMode().bitsPerPixel == 16) {
               this.bufferFormat = new Graphics.BufferFormat(5, 6, 5, 0, 16, 8, 0, false);
            }

            if (this.getDisplayMode().bitsPerPixel == 24) {
               this.bufferFormat = new Graphics.BufferFormat(8, 8, 8, 0, 16, 8, 0, false);
            }

            if (this.getDisplayMode().bitsPerPixel == 32) {
               this.bufferFormat = new Graphics.BufferFormat(8, 8, 8, 8, 16, 8, 0, false);
            }
         } catch (Exception var11) {
            Display.destroy();

            try {
               Thread.sleep(200L);
            } catch (InterruptedException var7) {
            }

            try {
               Display.create(new PixelFormat());
            } catch (Exception var10) {
               if (!this.softwareMode && this.config.allowSoftwareMode) {
                  this.softwareMode = true;
                  System.setProperty("org.lwjgl.opengl.Display.allowSoftwareOpenGL", "true");
                  this.createDisplayPixelFormat(useGL30, gles30ContextMajor, gles30ContextMinor);
                  return;
               }

               throw new GdxRuntimeException("OpenGL is not supported by the video driver.", var10);
            }

            if (this.getDisplayMode().bitsPerPixel == 16) {
               this.bufferFormat = new Graphics.BufferFormat(5, 6, 5, 0, 8, 0, 0, false);
            }

            if (this.getDisplayMode().bitsPerPixel == 24) {
               this.bufferFormat = new Graphics.BufferFormat(8, 8, 8, 0, 8, 0, 0, false);
            }

            if (this.getDisplayMode().bitsPerPixel == 32) {
               this.bufferFormat = new Graphics.BufferFormat(8, 8, 8, 8, 8, 0, 0, false);
            }
         }
      }
   }

   public void initiateGLInstances() {
      if (this.usingGL30) {
         this.gl30 = new LwjglGL30();
         this.gl20 = this.gl30;
      } else {
         this.gl20 = new LwjglGL20();
      }

      if (!glVersion.isVersionEqualToOrHigher(2, 0)) {
         throw new GdxRuntimeException(
            "OpenGL 2.0 or higher with the FBO extension is required. OpenGL version: " + GL11.glGetString(7938) + "\n" + glVersion.getDebugVersionString()
         );
      } else if (!supportsFBO()) {
         throw new GdxRuntimeException(
            "OpenGL 2.0 or higher with the FBO extension is required. OpenGL version: "
               + GL11.glGetString(7938)
               + ", FBO extension: false\n"
               + glVersion.getDebugVersionString()
         );
      } else {
         Gdx.gl = this.gl20;
         Gdx.gl20 = this.gl20;
         Gdx.gl30 = this.gl30;
      }
   }

   @Override
   public float getPpiX() {
      return Toolkit.getDefaultToolkit().getScreenResolution();
   }

   @Override
   public float getPpiY() {
      return Toolkit.getDefaultToolkit().getScreenResolution();
   }

   @Override
   public float getPpcX() {
      return Toolkit.getDefaultToolkit().getScreenResolution() / 2.54F;
   }

   @Override
   public float getPpcY() {
      return Toolkit.getDefaultToolkit().getScreenResolution() / 2.54F;
   }

   @Override
   public float getDensity() {
      return this.config.overrideDensity != -1 ? this.config.overrideDensity / 160.0F : Toolkit.getDefaultToolkit().getScreenResolution() / 160.0F;
   }

   @Override
   public boolean supportsDisplayModeChange() {
      return true;
   }

   @Override
   public Graphics.Monitor getPrimaryMonitor() {
      return new LwjglGraphics.LwjglMonitor(0, 0, "Primary Monitor");
   }

   @Override
   public Graphics.Monitor getMonitor() {
      return this.getPrimaryMonitor();
   }

   @Override
   public Graphics.Monitor[] getMonitors() {
      return new Graphics.Monitor[]{this.getPrimaryMonitor()};
   }

   @Override
   public Graphics.DisplayMode[] getDisplayModes(Graphics.Monitor monitor) {
      return this.getDisplayModes();
   }

   @Override
   public Graphics.DisplayMode getDisplayMode(Graphics.Monitor monitor) {
      return this.getDisplayMode();
   }

   @Override
   public boolean setFullscreenMode(Graphics.DisplayMode displayMode) {
      org.lwjgl.opengl.DisplayMode mode = ((LwjglGraphics.LwjglDisplayMode)displayMode).mode;

      try {
         if (!mode.isFullscreenCapable()) {
            Display.setDisplayMode(mode);
         } else {
            Display.setDisplayModeAndFullscreen(mode);
         }

         float scaleFactor = Display.getPixelScaleFactor();
         this.config.width = (int)(mode.getWidth() * scaleFactor);
         this.config.height = (int)(mode.getHeight() * scaleFactor);
         if (Gdx.gl != null) {
            Gdx.gl.glViewport(0, 0, this.config.width, this.config.height);
         }

         this.resize = true;
         return true;
      } catch (LWJGLException var4) {
         return false;
      }
   }

   @Override
   public boolean setWindowedMode(int width, int height) {
      if (this.getWidth() == width && this.getHeight() == height && !Display.isFullscreen()) {
         return true;
      } else {
         try {
            org.lwjgl.opengl.DisplayMode targetDisplayMode = null;
            boolean fullscreen = false;
            if (fullscreen) {
               org.lwjgl.opengl.DisplayMode[] modes = Display.getAvailableDisplayModes();
               int freq = 0;

               for (int i = 0; i < modes.length; i++) {
                  org.lwjgl.opengl.DisplayMode current = modes[i];
                  if (current.getWidth() == width && current.getHeight() == height) {
                     if ((targetDisplayMode == null || current.getFrequency() >= freq)
                        && (targetDisplayMode == null || current.getBitsPerPixel() > targetDisplayMode.getBitsPerPixel())) {
                        targetDisplayMode = current;
                        freq = current.getFrequency();
                     }

                     if (current.getBitsPerPixel() == Display.getDesktopDisplayMode().getBitsPerPixel()
                        && current.getFrequency() == Display.getDesktopDisplayMode().getFrequency()) {
                        targetDisplayMode = current;
                        break;
                     }
                  }
               }
            } else {
               targetDisplayMode = new org.lwjgl.opengl.DisplayMode(width, height);
            }

            if (targetDisplayMode == null) {
               return false;
            } else {
               boolean resizable = !fullscreen && this.config.resizable;
               Display.setDisplayMode(targetDisplayMode);
               Display.setFullscreen(fullscreen);
               if (resizable == Display.isResizable()) {
                  Display.setResizable(!resizable);
               }

               Display.setResizable(resizable);
               float scaleFactor = Display.getPixelScaleFactor();
               this.config.width = (int)(targetDisplayMode.getWidth() * scaleFactor);
               this.config.height = (int)(targetDisplayMode.getHeight() * scaleFactor);
               if (Gdx.gl != null) {
                  Gdx.gl.glViewport(0, 0, this.config.width, this.config.height);
               }

               this.resize = true;
               return true;
            }
         } catch (LWJGLException var9) {
            return false;
         }
      }
   }

   @Override
   public Graphics.DisplayMode[] getDisplayModes() {
      try {
         org.lwjgl.opengl.DisplayMode[] availableDisplayModes = Display.getAvailableDisplayModes();
         Graphics.DisplayMode[] modes = new Graphics.DisplayMode[availableDisplayModes.length];
         int idx = 0;

         for (org.lwjgl.opengl.DisplayMode mode : availableDisplayModes) {
            if (mode.isFullscreenCapable()) {
               modes[idx++] = new LwjglGraphics.LwjglDisplayMode(mode.getWidth(), mode.getHeight(), mode.getFrequency(), mode.getBitsPerPixel(), mode);
            }
         }

         return modes;
      } catch (LWJGLException var8) {
         throw new GdxRuntimeException("Couldn't fetch available display modes", var8);
      }
   }

   @Override
   public Graphics.DisplayMode getDisplayMode() {
      org.lwjgl.opengl.DisplayMode mode = Display.getDesktopDisplayMode();
      return new LwjglGraphics.LwjglDisplayMode(mode.getWidth(), mode.getHeight(), mode.getFrequency(), mode.getBitsPerPixel(), mode);
   }

   @Override
   public void setTitle(String title) {
      Display.setTitle(title);
   }

   @Override
   public void setUndecorated(boolean undecorated) {
      System.setProperty("org.lwjgl.opengl.Window.undecorated", undecorated ? "true" : "false");
   }

   @Override
   public void setResizable(boolean resizable) {
      this.config.resizable = resizable;
      Display.setResizable(resizable);
   }

   @Override
   public Graphics.BufferFormat getBufferFormat() {
      return this.bufferFormat;
   }

   @Override
   public void setVSync(boolean vsync) {
      this.vsync = vsync;
      Display.setVSyncEnabled(vsync);
   }

   @Override
   public boolean supportsExtension(String extension) {
      return extensions.contains(extension, false);
   }

   @Override
   public void setContinuousRendering(boolean isContinuous) {
      this.isContinuous = isContinuous;
   }

   @Override
   public boolean isContinuousRendering() {
      return this.isContinuous;
   }

   @Override
   public void requestRendering() {
      synchronized (this) {
         this.requestRendering = true;
      }
   }

   public boolean shouldRender() {
      synchronized (this) {
         boolean rq = this.requestRendering;
         this.requestRendering = false;
         return rq || this.isContinuous || Display.isDirty();
      }
   }

   @Override
   public boolean isFullscreen() {
      return Display.isFullscreen();
   }

   public boolean isSoftwareMode() {
      return this.softwareMode;
   }

   @Override
   public boolean isGL30Available() {
      return this.gl30 != null;
   }

   @Override
   public GL30 getGL30() {
      return this.gl30;
   }

   @Override
   public Cursor newCursor(Pixmap pixmap, int xHotspot, int yHotspot) {
      return new LwjglCursor(pixmap, xHotspot, yHotspot);
   }

   @Override
   public void setCursor(Cursor cursor) {
      if (this.canvas == null || !SharedLibraryLoader.isMac) {
         try {
            Mouse.setNativeCursor(((LwjglCursor)cursor).lwjglCursor);
         } catch (LWJGLException var3) {
            throw new GdxRuntimeException("Could not set cursor image.", var3);
         }
      }
   }

   @Override
   public void setSystemCursor(Cursor.SystemCursor systemCursor) {
      if (this.canvas == null || !SharedLibraryLoader.isMac) {
         try {
            Mouse.setNativeCursor(null);
         } catch (LWJGLException var3) {
            throw new GdxRuntimeException("Couldn't set system cursor");
         }
      }
   }

   private class LwjglDisplayMode extends Graphics.DisplayMode {
      org.lwjgl.opengl.DisplayMode mode;

      public LwjglDisplayMode(int width, int height, int refreshRate, int bitsPerPixel, org.lwjgl.opengl.DisplayMode mode) {
         super(width, height, refreshRate, bitsPerPixel);
         this.mode = mode;
      }
   }

   private class LwjglMonitor extends Graphics.Monitor {
      protected LwjglMonitor(int virtualX, int virtualY, String name) {
         super(virtualX, virtualY, name);
      }
   }

   public interface SetDisplayModeCallback {
      LwjglApplicationConfiguration onFailure(LwjglApplicationConfiguration var1);
   }
}
