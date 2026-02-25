package com.badlogic.gdx;

public interface ApplicationLogger {
   void log(String var1, String var2);

   void log(String var1, String var2, Throwable var3);

   void error(String var1, String var2);

   void error(String var1, String var2, Throwable var3);

   void debug(String var1, String var2);

   void debug(String var1, String var2, Throwable var3);
}
