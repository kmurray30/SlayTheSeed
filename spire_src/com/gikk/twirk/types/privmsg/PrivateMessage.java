package com.gikk.twirk.types.privmsg;

import com.gikk.twirk.types.AbstractEmoteMessage;
import com.gikk.twirk.types.cheer.Cheer;
import java.util.List;

public interface PrivateMessage extends AbstractEmoteMessage {
   boolean isCheer();

   List<Cheer> getCheers();

   int getBits();
}
