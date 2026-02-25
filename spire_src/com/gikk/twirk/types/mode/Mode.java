package com.gikk.twirk.types.mode;

import com.gikk.twirk.types.AbstractType;

public interface Mode extends AbstractType {
   Mode.MODE_EVENT getEvent();

   String getUser();

   public static enum MODE_EVENT {
      GAINED_MOD,
      LOST_MOD;
   }
}
