package com.gikk.twirk.types;

import java.util.Map;

public interface TagMap extends Map<String, String> {
   String getAsString(String var1);

   int getAsInt(String var1);

   long getAsLong(String var1);

   boolean getAsBoolean(String var1);

   static TagMap getDefault(String tag) {
      return new TagMapImpl(tag);
   }
}
