package com.gikk.twirk;

import java.io.IOException;
import java.net.Socket;

public interface SocketFactory {
   Socket createSocket() throws IOException;
}
