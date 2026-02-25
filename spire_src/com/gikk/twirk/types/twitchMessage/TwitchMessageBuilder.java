package com.gikk.twirk.types.twitchMessage;

public interface TwitchMessageBuilder {
   TwitchMessage build(String var1);

   static TwitchMessageBuilder getDefault() {
      return new DefaultTwitchMessageBuilder();
   }
}
