package com.gikk.twirk.types.roomstate;

import com.gikk.twirk.types.twitchMessage.TwitchMessage;

public interface RoomstateBuilder {
   static RoomstateBuilder getDefault() {
      return new DefaultRoomstateBuilder();
   }

   Roomstate build(TwitchMessage var1);
}
