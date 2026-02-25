package com.gikk.twirk.types.users;

import com.gikk.twirk.types.twitchMessage.TwitchMessage;

public interface UserstateBuilder {
   static UserstateBuilder getDefault() {
      return new DefaultUserstateBuilder();
   }

   Userstate build(TwitchMessage var1);
}
