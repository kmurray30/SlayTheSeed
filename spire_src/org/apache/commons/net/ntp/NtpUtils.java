package org.apache.commons.net.ntp;

public final class NtpUtils {
   public static String getHostAddress(int address) {
      return (address >>> 24 & 0xFF) + "." + (address >>> 16 & 0xFF) + "." + (address >>> 8 & 0xFF) + "." + (address >>> 0 & 0xFF);
   }

   public static String getRefAddress(NtpV3Packet packet) {
      int address = packet == null ? 0 : packet.getReferenceId();
      return getHostAddress(address);
   }

   public static String getReferenceClock(NtpV3Packet message) {
      if (message == null) {
         return "";
      } else {
         int refId = message.getReferenceId();
         if (refId == 0) {
            return "";
         } else {
            StringBuilder buf = new StringBuilder(4);

            for (int shiftBits = 24; shiftBits >= 0; shiftBits -= 8) {
               char c = (char)(refId >>> shiftBits & 0xFF);
               if (c == 0) {
                  break;
               }

               if (!Character.isLetterOrDigit(c)) {
                  return "";
               }

               buf.append(c);
            }

            return buf.toString();
         }
      }
   }

   public static String getModeName(int mode) {
      switch (mode) {
         case 0:
            return "Reserved";
         case 1:
            return "Symmetric Active";
         case 2:
            return "Symmetric Passive";
         case 3:
            return "Client";
         case 4:
            return "Server";
         case 5:
            return "Broadcast";
         case 6:
            return "Control";
         case 7:
            return "Private";
         default:
            return "Unknown";
      }
   }
}
