package com.gikk.twirk.types;

import com.gikk.twirk.enums.USER_TYPE;
import com.gikk.twirk.types.twitchMessage.TwitchMessage;
import com.gikk.twirk.types.users.Userstate;

public abstract class AbstractTwitchUserFields {
   private static final int[] DEFAULT_COLORS = new int[]{
      16711680, 255, 65280, 11674146, 16744272, 10145074, 16729344, 3050327, 14329120, 13789470, 6266528, 2003199, 16738740, 9055202, 65407
   };
   public String[] badges;
   public int bits;
   public String userName;
   public String displayName;
   public int color;
   public long userID;
   public int[] emoteSets;
   public boolean isOwner;
   public boolean isMod;
   public boolean isSub;
   public boolean isTurbo;
   public USER_TYPE userType;
   public Userstate userstate;
   public String rawLine;

   protected void parseUserProperties(TwitchMessage message) {
      String channelOwner = message.getTarget().substring(1);
      TagMap r = message.getTagMap();
      String temp = message.getPrefix();
      String testLogin = r.getAsString("login");
      if (testLogin.isEmpty()) {
         this.userName = temp.contains("!") ? temp.substring(1, temp.indexOf("!")) : "";
      } else {
         this.userName = testLogin;
      }

      temp = r.getAsString("display-name");
      this.displayName = temp.isEmpty() ? Character.toUpperCase(this.userName.charAt(0)) + this.userName.substring(1) : temp;
      temp = r.getAsString("badges");
      this.badges = temp.isEmpty() ? new String[0] : temp.split(",");
      this.isMod = r.getAsBoolean("mod");
      this.isSub = r.getAsBoolean("subscriber");
      this.isTurbo = r.getAsBoolean("turbo");
      this.userID = r.getAsLong("user-id");
      this.color = r.getAsInt("color");
      this.color = this.color == -1 ? this.getDefaultColor() : this.color;
      this.emoteSets = this.parseEmoteSets(r.getAsString("emote-sets"));
      this.userType = this.parseUserType(r.getAsString("user-type"), this.displayName, channelOwner, this.isSub || this.isTurbo);
      this.isOwner = this.userType == USER_TYPE.OWNER;
      this.rawLine = message.getRaw();
   }

   private int[] parseEmoteSets(String emoteSet) {
      if (emoteSet.isEmpty()) {
         return new int[0];
      } else {
         String[] sets = emoteSet.split(",");
         int[] out = new int[sets.length];

         for (int i = 0; i < sets.length; i++) {
            out[i] = Integer.parseInt(sets[i]);
         }

         return out;
      }
   }

   private USER_TYPE parseUserType(String userType, String sender, String channelOwner, boolean isSub) {
      if (sender.equalsIgnoreCase(channelOwner)) {
         return USER_TYPE.OWNER;
      } else if (userType.equals("mod")) {
         return USER_TYPE.MOD;
      } else if (userType.equals("global_mod")) {
         return USER_TYPE.GLOBAL_MOD;
      } else if (userType.equals("admin")) {
         return USER_TYPE.ADMIN;
      } else if (userType.equals("staff")) {
         return USER_TYPE.STAFF;
      } else {
         return isSub ? USER_TYPE.SUBSCRIBER : USER_TYPE.DEFAULT;
      }
   }

   private int getDefaultColor() {
      if (this.displayName.isEmpty()) {
         return DEFAULT_COLORS[(int)System.currentTimeMillis() % DEFAULT_COLORS.length];
      } else {
         int n = this.displayName.charAt(0) + this.displayName.charAt(this.displayName.length() - 1);
         return DEFAULT_COLORS[n % DEFAULT_COLORS.length];
      }
   }
}
