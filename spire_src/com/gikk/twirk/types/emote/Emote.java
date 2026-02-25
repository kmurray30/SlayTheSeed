package com.gikk.twirk.types.emote;

import com.gikk.twirk.enums.EMOTE_SIZE;
import java.util.LinkedList;

public interface Emote {
   int getEmoteID();

   LinkedList<Emote.EmoteIndices> getIndices();

   String getPattern();

   String getEmoteImageUrl(EMOTE_SIZE var1);

   public static class EmoteIndices {
      public final int beingIndex;
      public final int endIndex;

      public EmoteIndices(int begin, int end) {
         this.beingIndex = begin;
         this.endIndex = end;
      }

      @Override
      public String toString() {
         return "(" + this.beingIndex + "," + this.endIndex + ")";
      }
   }
}
