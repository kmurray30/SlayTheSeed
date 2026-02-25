package com.gikk.twirk.types.mode;

import com.gikk.twirk.types.twitchMessage.TwitchMessage;

public interface ModeBuilder {
   static ModeBuilder getDefault() {
      return new DefaultModeBuilder();
   }

   Mode build(TwitchMessage var1);
}
