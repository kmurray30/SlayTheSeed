package com.gikk.twirk.types.users;

import com.gikk.twirk.types.twitchMessage.TwitchMessage;

public interface TwitchUserBuilder {
   TwitchUser build(TwitchMessage var1);

   static TwitchUserBuilder getDefault() {
      return new DefaultTwitchUserBuilder();
   }
}
