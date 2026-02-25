package com.gikk.twirk.types.clearChat;

import com.gikk.twirk.types.twitchMessage.TwitchMessage;

public interface ClearChatBuilder {
   ClearChat build(TwitchMessage var1);

   static ClearChatBuilder getDefault() {
      return new DefaultClearChatBuilder();
   }
}
