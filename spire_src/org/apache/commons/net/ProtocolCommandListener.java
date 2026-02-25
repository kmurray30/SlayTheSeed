package org.apache.commons.net;

import java.util.EventListener;

public interface ProtocolCommandListener extends EventListener {
   void protocolCommandSent(ProtocolCommandEvent var1);

   void protocolReplyReceived(ProtocolCommandEvent var1);
}
