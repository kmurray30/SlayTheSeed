package com.gikk.twirk.types.users;

import com.gikk.twirk.enums.USER_TYPE;

public interface TwitchUser {
   String getUserName();

   String getDisplayName();

   boolean isOwner();

   boolean isMod();

   boolean isTurbo();

   boolean isSub();

   USER_TYPE getUserType();

   int getColor();

   String[] getBadges();

   long getUserID();
}
