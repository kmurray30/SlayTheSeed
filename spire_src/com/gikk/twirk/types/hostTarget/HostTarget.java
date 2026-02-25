package com.gikk.twirk.types.hostTarget;

import com.gikk.twirk.enums.HOSTTARGET_MODE;
import com.gikk.twirk.types.AbstractType;

public interface HostTarget extends AbstractType {
   HOSTTARGET_MODE getMode();

   String getTarget();

   int getViewerCount();
}
