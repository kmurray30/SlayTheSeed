package com.gikk.twirk.types.notice;

import com.gikk.twirk.enums.NOTICE_EVENT;
import com.gikk.twirk.types.AbstractType;

public interface Notice extends AbstractType {
   NOTICE_EVENT getEvent();

   String getMessage();

   String getRawNoticeID();
}
