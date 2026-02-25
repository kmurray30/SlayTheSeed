package com.gikk.twirk.types.usernotice;

import com.gikk.twirk.types.twitchMessage.TwitchMessage;

public interface UsernoticeBuilder {
   Usernotice build(TwitchMessage var1);

   static UsernoticeBuilder getDefault() {
      return new DefaultUsernoticeBuilder();
   }
}
