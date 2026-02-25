package com.gikk.twirk.types;

import com.gikk.twirk.types.emote.Emote;
import java.util.List;

public interface AbstractEmoteMessage extends AbstractType {
   boolean hasEmotes();

   List<Emote> getEmotes();

   long getSentTimestamp();

   long getRoomID();

   String getMessageID();
}
