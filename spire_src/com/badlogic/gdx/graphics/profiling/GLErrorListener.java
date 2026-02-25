package com.badlogic.gdx.graphics.profiling;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxRuntimeException;

public interface GLErrorListener {
   GLErrorListener LOGGING_LISTENER = new GLErrorListener() {
      @Override
      public void onError(int error) {
         String place = null;

         try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();

            for (int i = 0; i < stack.length; i++) {
               if ("check".equals(stack[i].getMethodName())) {
                  if (i + 1 < stack.length) {
                     StackTraceElement glMethod = stack[i + 1];
                     place = glMethod.getMethodName();
                  }
                  break;
               }
            }
         } catch (Exception var6) {
         }

         if (place != null) {
            Gdx.app.error("GLProfiler", "Error " + GLProfiler.resolveErrorNumber(error) + " from " + place);
         } else {
            Gdx.app.error("GLProfiler", "Error " + GLProfiler.resolveErrorNumber(error) + " at: ", new Exception());
         }
      }
   };
   GLErrorListener THROWING_LISTENER = new GLErrorListener() {
      @Override
      public void onError(int error) {
         throw new GdxRuntimeException("GLProfiler: Got GL error " + GLProfiler.resolveErrorNumber(error));
      }
   };

   void onError(int var1);
}
