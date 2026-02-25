package com.gikk.twirk.types.roomstate;

import com.gikk.twirk.types.TagMap;
import com.gikk.twirk.types.twitchMessage.TwitchMessage;

class DefaultRoomstateBuilder implements RoomstateBuilder {
   String broadcasterLanguage;
   int r9kMode;
   int subMode;
   int slowModeTimer;
   String rawLine;

   @Override
   public Roomstate build(TwitchMessage message) {
      this.rawLine = message.getRaw();
      TagMap r = message.getTagMap();
      this.broadcasterLanguage = r.getAsString("broadcaster-lang");
      this.r9kMode = r.getAsInt("r9k");
      this.slowModeTimer = r.getAsInt("slow");
      this.subMode = r.getAsInt("subs-only");
      return new RoomstateImpl(this);
   }
}
