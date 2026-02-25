package com.badlogic.gdx;

import java.util.Map;

public interface Preferences {
   Preferences putBoolean(String var1, boolean var2);

   Preferences putInteger(String var1, int var2);

   Preferences putLong(String var1, long var2);

   Preferences putFloat(String var1, float var2);

   Preferences putString(String var1, String var2);

   Preferences put(Map<String, ?> var1);

   boolean getBoolean(String var1);

   int getInteger(String var1);

   long getLong(String var1);

   float getFloat(String var1);

   String getString(String var1);

   boolean getBoolean(String var1, boolean var2);

   int getInteger(String var1, int var2);

   long getLong(String var1, long var2);

   float getFloat(String var1, float var2);

   String getString(String var1, String var2);

   Map<String, ?> get();

   boolean contains(String var1);

   void clear();

   void remove(String var1);

   void flush();
}
