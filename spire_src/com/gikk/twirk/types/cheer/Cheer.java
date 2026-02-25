package com.gikk.twirk.types.cheer;

public interface Cheer {
   int getBits();

   String getMessage();

   String getImageURL(CheerTheme var1, CheerType var2, CheerSize var3);
}
