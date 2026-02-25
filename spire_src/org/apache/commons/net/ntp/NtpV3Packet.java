package org.apache.commons.net.ntp;

import java.net.DatagramPacket;

public interface NtpV3Packet {
   int NTP_PORT = 123;
   int LI_NO_WARNING = 0;
   int LI_LAST_MINUTE_HAS_61_SECONDS = 1;
   int LI_LAST_MINUTE_HAS_59_SECONDS = 2;
   int LI_ALARM_CONDITION = 3;
   int MODE_RESERVED = 0;
   int MODE_SYMMETRIC_ACTIVE = 1;
   int MODE_SYMMETRIC_PASSIVE = 2;
   int MODE_CLIENT = 3;
   int MODE_SERVER = 4;
   int MODE_BROADCAST = 5;
   int MODE_CONTROL_MESSAGE = 6;
   int MODE_PRIVATE = 7;
   int NTP_MINPOLL = 4;
   int NTP_MAXPOLL = 14;
   int NTP_MINCLOCK = 1;
   int NTP_MAXCLOCK = 10;
   int VERSION_3 = 3;
   int VERSION_4 = 4;
   String TYPE_NTP = "NTP";
   String TYPE_ICMP = "ICMP";
   String TYPE_TIME = "TIME";
   String TYPE_DAYTIME = "DAYTIME";

   DatagramPacket getDatagramPacket();

   void setDatagramPacket(DatagramPacket var1);

   int getLeapIndicator();

   void setLeapIndicator(int var1);

   int getMode();

   String getModeName();

   void setMode(int var1);

   int getPoll();

   void setPoll(int var1);

   int getPrecision();

   void setPrecision(int var1);

   int getRootDelay();

   void setRootDelay(int var1);

   double getRootDelayInMillisDouble();

   int getRootDispersion();

   void setRootDispersion(int var1);

   long getRootDispersionInMillis();

   double getRootDispersionInMillisDouble();

   int getVersion();

   void setVersion(int var1);

   int getStratum();

   void setStratum(int var1);

   String getReferenceIdString();

   int getReferenceId();

   void setReferenceId(int var1);

   TimeStamp getTransmitTimeStamp();

   TimeStamp getReferenceTimeStamp();

   TimeStamp getOriginateTimeStamp();

   TimeStamp getReceiveTimeStamp();

   void setTransmitTime(TimeStamp var1);

   void setReferenceTime(TimeStamp var1);

   void setOriginateTimeStamp(TimeStamp var1);

   void setReceiveTimeStamp(TimeStamp var1);

   String getType();
}
