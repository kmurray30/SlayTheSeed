package org.apache.commons.net.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubnetUtils {
   private static final String IP_ADDRESS = "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})";
   private static final String SLASH_FORMAT = "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})/(\\d{1,3})";
   private static final Pattern addressPattern = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
   private static final Pattern cidrPattern = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})/(\\d{1,3})");
   private static final int NBITS = 32;
   private int netmask = 0;
   private int address = 0;
   private int network = 0;
   private int broadcast = 0;
   private boolean inclusiveHostCount = false;

   public SubnetUtils(String cidrNotation) {
      this.calculate(cidrNotation);
   }

   public SubnetUtils(String address, String mask) {
      this.calculate(this.toCidrNotation(address, mask));
   }

   public boolean isInclusiveHostCount() {
      return this.inclusiveHostCount;
   }

   public void setInclusiveHostCount(boolean inclusiveHostCount) {
      this.inclusiveHostCount = inclusiveHostCount;
   }

   public final SubnetUtils.SubnetInfo getInfo() {
      return new SubnetUtils.SubnetInfo();
   }

   private void calculate(String mask) {
      Matcher matcher = cidrPattern.matcher(mask);
      if (!matcher.matches()) {
         throw new IllegalArgumentException("Could not parse [" + mask + "]");
      } else {
         this.address = this.matchAddress(matcher);
         int cidrPart = this.rangeCheck(Integer.parseInt(matcher.group(5)), 0, 32);

         for (int j = 0; j < cidrPart; j++) {
            this.netmask |= 1 << 31 - j;
         }

         this.network = this.address & this.netmask;
         this.broadcast = this.network | ~this.netmask;
      }
   }

   private int toInteger(String address) {
      Matcher matcher = addressPattern.matcher(address);
      if (matcher.matches()) {
         return this.matchAddress(matcher);
      } else {
         throw new IllegalArgumentException("Could not parse [" + address + "]");
      }
   }

   private int matchAddress(Matcher matcher) {
      int addr = 0;

      for (int i = 1; i <= 4; i++) {
         int n = this.rangeCheck(Integer.parseInt(matcher.group(i)), 0, 255);
         addr |= (n & 0xFF) << 8 * (4 - i);
      }

      return addr;
   }

   private int[] toArray(int val) {
      int[] ret = new int[4];

      for (int j = 3; j >= 0; j--) {
         ret[j] |= val >>> 8 * (3 - j) & 0xFF;
      }

      return ret;
   }

   private String format(int[] octets) {
      StringBuilder str = new StringBuilder();

      for (int i = 0; i < octets.length; i++) {
         str.append(octets[i]);
         if (i != octets.length - 1) {
            str.append(".");
         }
      }

      return str.toString();
   }

   private int rangeCheck(int value, int begin, int end) {
      if (value >= begin && value <= end) {
         return value;
      } else {
         throw new IllegalArgumentException("Value [" + value + "] not in range [" + begin + "," + end + "]");
      }
   }

   int pop(int x) {
      x -= x >>> 1 & 1431655765;
      x = (x & 858993459) + (x >>> 2 & 858993459);
      x = x + (x >>> 4) & 252645135;
      x += x >>> 8;
      x += x >>> 16;
      return x & 63;
   }

   private String toCidrNotation(String addr, String mask) {
      return addr + "/" + this.pop(this.toInteger(mask));
   }

   public final class SubnetInfo {
      private static final long UNSIGNED_INT_MASK = 4294967295L;

      private SubnetInfo() {
      }

      private int netmask() {
         return SubnetUtils.this.netmask;
      }

      private int network() {
         return SubnetUtils.this.network;
      }

      private int address() {
         return SubnetUtils.this.address;
      }

      private int broadcast() {
         return SubnetUtils.this.broadcast;
      }

      private long networkLong() {
         return SubnetUtils.this.network & 4294967295L;
      }

      private long broadcastLong() {
         return SubnetUtils.this.broadcast & 4294967295L;
      }

      private int low() {
         return SubnetUtils.this.isInclusiveHostCount() ? this.network() : (this.broadcastLong() - this.networkLong() > 1L ? this.network() + 1 : 0);
      }

      private int high() {
         return SubnetUtils.this.isInclusiveHostCount() ? this.broadcast() : (this.broadcastLong() - this.networkLong() > 1L ? this.broadcast() - 1 : 0);
      }

      public boolean isInRange(String address) {
         return this.isInRange(SubnetUtils.this.toInteger(address));
      }

      public boolean isInRange(int address) {
         long addLong = address & 4294967295L;
         long lowLong = this.low() & 4294967295L;
         long highLong = this.high() & 4294967295L;
         return addLong >= lowLong && addLong <= highLong;
      }

      public String getBroadcastAddress() {
         return SubnetUtils.this.format(SubnetUtils.this.toArray(this.broadcast()));
      }

      public String getNetworkAddress() {
         return SubnetUtils.this.format(SubnetUtils.this.toArray(this.network()));
      }

      public String getNetmask() {
         return SubnetUtils.this.format(SubnetUtils.this.toArray(this.netmask()));
      }

      public String getAddress() {
         return SubnetUtils.this.format(SubnetUtils.this.toArray(this.address()));
      }

      public String getLowAddress() {
         return SubnetUtils.this.format(SubnetUtils.this.toArray(this.low()));
      }

      public String getHighAddress() {
         return SubnetUtils.this.format(SubnetUtils.this.toArray(this.high()));
      }

      @Deprecated
      public int getAddressCount() {
         long countLong = this.getAddressCountLong();
         if (countLong > 2147483647L) {
            throw new RuntimeException("Count is larger than an integer: " + countLong);
         } else {
            return (int)countLong;
         }
      }

      public long getAddressCountLong() {
         long b = this.broadcastLong();
         long n = this.networkLong();
         long count = b - n + (SubnetUtils.this.isInclusiveHostCount() ? 1 : -1);
         return count < 0L ? 0L : count;
      }

      public int asInteger(String address) {
         return SubnetUtils.this.toInteger(address);
      }

      public String getCidrSignature() {
         return SubnetUtils.this.toCidrNotation(
            SubnetUtils.this.format(SubnetUtils.this.toArray(this.address())), SubnetUtils.this.format(SubnetUtils.this.toArray(this.netmask()))
         );
      }

      public String[] getAllAddresses() {
         int ct = this.getAddressCount();
         String[] addresses = new String[ct];
         if (ct == 0) {
            return addresses;
         } else {
            int add = this.low();

            for (int j = 0; add <= this.high(); j++) {
               addresses[j] = SubnetUtils.this.format(SubnetUtils.this.toArray(add));
               add++;
            }

            return addresses;
         }
      }

      @Override
      public String toString() {
         StringBuilder buf = new StringBuilder();
         buf.append("CIDR Signature:\t[")
            .append(this.getCidrSignature())
            .append("]")
            .append(" Netmask: [")
            .append(this.getNetmask())
            .append("]\n")
            .append("Network:\t[")
            .append(this.getNetworkAddress())
            .append("]\n")
            .append("Broadcast:\t[")
            .append(this.getBroadcastAddress())
            .append("]\n")
            .append("First Address:\t[")
            .append(this.getLowAddress())
            .append("]\n")
            .append("Last Address:\t[")
            .append(this.getHighAddress())
            .append("]\n")
            .append("# Addresses:\t[")
            .append(this.getAddressCount())
            .append("]\n");
         return buf.toString();
      }
   }
}
