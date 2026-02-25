package com.gikk.twirk.types.roomstate;

import com.gikk.twirk.types.AbstractType;

public interface Roomstate extends AbstractType {
   String getBroadcasterLanguage();

   int get9kMode();

   int getSubMode();

   int getSlowModeTimer();
}
