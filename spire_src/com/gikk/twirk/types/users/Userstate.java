package com.gikk.twirk.types.users;

import com.gikk.twirk.enums.USER_TYPE;
import com.gikk.twirk.types.AbstractType;

public interface Userstate extends AbstractType {
   int getColor();

   String getDisplayName();

   boolean isMod();

   boolean isSub();

   boolean isTurbo();

   USER_TYPE getUserType();

   int[] getEmoteSets();
}
