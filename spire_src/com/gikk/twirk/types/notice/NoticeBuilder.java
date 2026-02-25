package com.gikk.twirk.types.notice;

import com.gikk.twirk.types.twitchMessage.TwitchMessage;

public interface NoticeBuilder {
   static NoticeBuilder getDefault() {
      return new DefaultNoticeBuilder();
   }

   Notice build(TwitchMessage var1);
}
