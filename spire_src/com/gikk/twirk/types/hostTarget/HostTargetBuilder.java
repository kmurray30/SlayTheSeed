package com.gikk.twirk.types.hostTarget;

import com.gikk.twirk.types.twitchMessage.TwitchMessage;

public interface HostTargetBuilder {
   static HostTargetBuilder getDefault() {
      return new DefaultHostTargetBuilder();
   }

   HostTarget build(TwitchMessage var1);
}
