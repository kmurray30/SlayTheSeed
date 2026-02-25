package com.gikk.twirk.types.cheer;

import java.util.Objects;

public class CheerImpl implements Cheer {
   private static final String ROOT_URL = "static-cdn.jtvnw.net/bits/<theme>/<type>/<color>/<size>";
   private final int bits;
   private final String message;

   public CheerImpl(int bits, String message) {
      this.bits = bits;
      this.message = message;
   }

   @Override
   public int getBits() {
      return this.bits;
   }

   @Override
   public String getMessage() {
      return this.message;
   }

   @Override
   public boolean equals(Object obj) {
      if (!(obj instanceof Cheer)) {
         return false;
      } else {
         Cheer other = (Cheer)obj;
         return this.bits == other.getBits() && this.message.equals(other.getMessage());
      }
   }

   @Override
   public int hashCode() {
      int hash = 5;
      hash = 17 * hash + this.bits;
      return 17 * hash + Objects.hashCode(this.message);
   }

   @Override
   public String getImageURL(CheerTheme theme, CheerType type, CheerSize size) {
      String color;
      if (this.bits < 100) {
         color = "gray";
      } else if (this.bits < 1000) {
         color = "purple";
      } else if (this.bits < 5000) {
         color = "green";
      } else if (this.bits < 10000) {
         color = "blue";
      } else {
         color = "red";
      }

      StringBuilder b = new StringBuilder("static-cdn.jtvnw.net/bits/<theme>/<type>/<color>/<size>");
      b.append("/").append(theme.getValue()).append("/").append(type.getValue()).append("/").append(color).append("/").append(size.getValue());
      return b.toString();
   }
}
