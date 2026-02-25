package com.gikk.twirk.types.usernotice;

import com.gikk.twirk.types.TagMap;
import com.gikk.twirk.types.emote.Emote;
import com.gikk.twirk.types.twitchMessage.TwitchMessage;
import com.gikk.twirk.types.usernotice.subtype.Raid;
import com.gikk.twirk.types.usernotice.subtype.Ritual;
import com.gikk.twirk.types.usernotice.subtype.Subscription;
import java.util.List;

class DefaultUsernoticeBuilder implements UsernoticeBuilder {
   String rawLine;
   List<Emote> emotes;
   String messageID;
   int roomID;
   long sentTimestamp;
   String systemMessage;
   Subscription subscription;
   Raid raid;
   Ritual ritual;
   String message;

   @Override
   public Usernotice build(TwitchMessage message) {
      this.rawLine = message.getRaw();
      this.emotes = message.getEmotes();
      TagMap map = message.getTagMap();
      this.messageID = map.getAsString("id");
      this.roomID = map.getAsInt("room-id");
      this.sentTimestamp = map.getAsLong("tmi-sent-ts");
      this.systemMessage = map.getAsString("system-msg");
      String type = map.get("msg-id");
      if (type.equals("sub") || type.equals("resub") || type.equals("subgift") || type.equals("anonsubgift")) {
         this.subscription = TypeParser.parseSubscription(map);
      } else if (type.equals("raid")) {
         this.raid = TypeParser.parseRaid(map);
      } else if (type.equals("ritual")) {
         this.ritual = TypeParser.parseRitual(map);
      }

      this.message = message.getContent();
      Usernotice un = new UsernoticeImpl(this);
      this.clear();
      return un;
   }

   private void clear() {
      this.subscription = null;
      this.raid = null;
      this.ritual = null;
   }
}
