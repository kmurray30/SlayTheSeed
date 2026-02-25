package com.badlogic.gdx.graphics.profiling;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.FloatCounter;

public abstract class GLProfiler {
   public static int calls;
   public static int textureBindings;
   public static int drawCalls;
   public static int shaderSwitches;
   public static final FloatCounter vertexCount = new FloatCounter(0);
   public static GLErrorListener listener = GLErrorListener.LOGGING_LISTENER;

   public static String resolveErrorNumber(int error) {
      switch (error) {
         case 1280:
            return "GL_INVALID_ENUM";
         case 1281:
            return "GL_INVALID_VALUE";
         case 1282:
            return "GL_INVALID_OPERATION";
         case 1283:
         case 1284:
         default:
            return "number " + error;
         case 1285:
            return "GL_OUT_OF_MEMORY";
         case 1286:
            return "GL_INVALID_FRAMEBUFFER_OPERATION";
      }
   }

   public static void enable() {
      if (!isEnabled()) {
         Gdx.gl30 = Gdx.gl30 == null ? null : new GL30Profiler(Gdx.gl30);
         Gdx.gl20 = (GL20)(Gdx.gl30 != null ? Gdx.gl30 : new GL20Profiler(Gdx.gl20));
         Gdx.gl = Gdx.gl20;
      }
   }

   public static void disable() {
      if (Gdx.gl30 != null && Gdx.gl30 instanceof GL30Profiler) {
         Gdx.gl30 = ((GL30Profiler)Gdx.gl30).gl30;
      }

      if (Gdx.gl20 != null && Gdx.gl20 instanceof GL20Profiler) {
         Gdx.gl20 = ((GL20Profiler)Gdx.gl).gl20;
      }

      if (Gdx.gl != null && Gdx.gl instanceof GL20Profiler) {
         Gdx.gl = ((GL20Profiler)Gdx.gl).gl20;
      }
   }

   public static boolean isEnabled() {
      return Gdx.gl30 instanceof GL30Profiler || Gdx.gl20 instanceof GL20Profiler;
   }

   public static void reset() {
      calls = 0;
      textureBindings = 0;
      drawCalls = 0;
      shaderSwitches = 0;
      vertexCount.reset();
   }
}
