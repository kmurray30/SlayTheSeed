package com.gikk.twirk.types.clearChat;

import com.gikk.twirk.enums.CLEARCHAT_MODE;
import com.gikk.twirk.types.AbstractType;

public interface ClearChat extends AbstractType {
   CLEARCHAT_MODE getMode();

   String getTarget();

   int getDuration();

   String getReason();
}
