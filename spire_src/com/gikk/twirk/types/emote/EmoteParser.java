package com.gikk.twirk.types.emote;

import java.util.LinkedList;
import java.util.List;

public class EmoteParser {
   private static final String EMOTES_IDENTIFIER = "emotes=";

   public static List<Emote> parseEmotes(String content, String tag) {
      List<Emote> emotes = new LinkedList<>();
      int begin = tag.indexOf("emotes=");
      int end = tag.indexOf(59, begin);
      if (begin != -1 && begin + "emotes=".length() != end) {
         String emotesString = tag.substring(begin + "emotes=".length(), end);
         EmoteImpl emote = new EmoteImpl();
         StringBuilder str = new StringBuilder();
         String emoteID = "";
         String beginIndex = "";

         for (char c : emotesString.toCharArray()) {
            switch (c) {
               case ',':
                  addIndices(emote, beginIndex, str.toString());
                  str.setLength(0);
                  break;
               case '-':
                  beginIndex = str.toString();
                  str.setLength(0);
                  break;
               case '/':
                  finalizeEmote(content, emotes, emote, emoteID, beginIndex, str.toString());
                  emote = new EmoteImpl();
                  str.setLength(0);
                  break;
               case ':':
                  emoteID = str.toString();
                  str.setLength(0);
                  break;
               default:
                  str.append(c);
            }
         }

         finalizeEmote(content, emotes, emote, emoteID, beginIndex, str.toString());
         return emotes;
      } else {
         return emotes;
      }
   }

   private static void finalizeEmote(String content, List<Emote> emotes, EmoteImpl emote, String emoteID, String beginIndex, String endIndex) {
      int begin = Integer.parseInt(beginIndex);
      int end = Integer.parseInt(endIndex) + 1;
      emote.addIndices(begin, end);
      emote.setEmoteID(Integer.parseInt(emoteID));
      emote.setPattern(content.substring(begin, end));
      emotes.add(emote);
   }

   private static void addIndices(EmoteImpl emote, String beginIndex, String endIndex) {
      int begin = Integer.parseInt(beginIndex);
      int end = Integer.parseInt(endIndex) + 1;
      emote.addIndices(begin, end);
   }
}
