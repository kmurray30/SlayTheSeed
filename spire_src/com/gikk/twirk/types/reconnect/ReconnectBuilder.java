package com.gikk.twirk.types.reconnect;

public interface ReconnectBuilder {
   static ReconnectBuilder getDefault() {
      return new DefaultReconnectBuilder();
   }

   Reconnect build();
}
