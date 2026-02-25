package org.apache.commons.net;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public interface DatagramSocketFactory {
   DatagramSocket createDatagramSocket() throws SocketException;

   DatagramSocket createDatagramSocket(int var1) throws SocketException;

   DatagramSocket createDatagramSocket(int var1, InetAddress var2) throws SocketException;
}
