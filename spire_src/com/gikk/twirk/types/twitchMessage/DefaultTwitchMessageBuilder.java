package com.gikk.twirk.types.twitchMessage;

import com.gikk.twirk.types.TagMap;
import com.gikk.twirk.types.cheer.Cheer;
import com.gikk.twirk.types.cheer.CheerParser;
import com.gikk.twirk.types.emote.Emote;
import com.gikk.twirk.types.emote.EmoteParser;
import java.util.List;

class DefaultTwitchMessageBuilder implements TwitchMessageBuilder {
   String line;
   String tag;
   String prefix;
   String command;
   String target;
   String content;
   String id;
   int roomID;
   List<Emote> emotes;
   List<Cheer> cheers;
   TagMap tagMap;

   @Override
   public TwitchMessage build(String chatLine) {
      if (chatLine.startsWith("@")) {
         this.parseWithTag(chatLine);
      } else {
         this.parseWithoutTag(chatLine);
      }

      this.line = chatLine;
      this.tagMap = TagMap.getDefault(this.tag);
      this.id = this.tagMap.getAsString("id");
      this.roomID = this.tagMap.getAsInt("room-id");
      this.emotes = EmoteParser.parseEmotes(this.content, this.tag);
      this.cheers = CheerParser.parseCheer(this.tagMap, this.content);
      return new TwitchMessageImpl(this);
   }

   private void parseWithTag(String line) {
      String[] parts = line.split(" ", 5);
      if (parts.length == 5) {
         this.content = parts[4].startsWith(":") ? parts[4].substring(1) : parts[4];
      } else {
         this.content = "";
      }

      if (parts.length >= 4) {
         this.target = parts[3];
      } else {
         this.target = "";
      }

      if (parts.length >= 3) {
         this.command = parts[2];
      } else {
         this.command = "";
      }

      if (parts.length >= 2) {
         this.prefix = parts[1];
      } else {
         this.prefix = "";
      }

      if (parts.length >= 1) {
         this.tag = parts[0];
      } else {
         this.tag = "";
      }
   }

   private void parseWithoutTag(String line) {
      this.tag = "";
      StringBuilder build = new StringBuilder();
      int i = 0;

      char c;
      while ((c = line.charAt(i++)) != ' ') {
         build.append(c);
      }

      this.prefix = build.toString().trim();
      build.setLength(0);

      do {
         build.append(c);
      } while (i < line.length() && (c = line.charAt(i++)) != ' ');

      this.command = build.toString().trim();
      build.setLength(0);

      do {
         build.append(c);
      } while (i < line.length() && (c = line.charAt(i++)) != ':' && c != '+' && c != '-');

      this.target = build.toString().trim();
      build.setLength(0);
      if (i == line.length()) {
         this.content = "";
      } else {
         do {
            build.append(c);
         } while (i < line.length() && (c = line.charAt(i++)) != '\r');

         String temp = build.toString().trim();
         this.content = temp.startsWith(":") ? temp.substring(1) : temp;
      }
   }
}
