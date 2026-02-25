package org.apache.commons.net.ntp;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeStamp implements Serializable, Comparable<TimeStamp> {
   private static final long serialVersionUID = 8139806907588338737L;
   protected static final long msb0baseTime = 2085978496000L;
   protected static final long msb1baseTime = -2208988800000L;
   public static final String NTP_DATE_FORMAT = "EEE, MMM dd yyyy HH:mm:ss.SSS";
   private final long ntpTime;
   private DateFormat simpleFormatter;
   private DateFormat utcFormatter;

   public TimeStamp(long ntpTime) {
      this.ntpTime = ntpTime;
   }

   public TimeStamp(String hexStamp) throws NumberFormatException {
      this.ntpTime = decodeNtpHexString(hexStamp);
   }

   public TimeStamp(Date d) {
      this.ntpTime = d == null ? 0L : toNtpTime(d.getTime());
   }

   public long ntpValue() {
      return this.ntpTime;
   }

   public long getSeconds() {
      return this.ntpTime >>> 32 & 4294967295L;
   }

   public long getFraction() {
      return this.ntpTime & 4294967295L;
   }

   public long getTime() {
      return getTime(this.ntpTime);
   }

   public Date getDate() {
      long time = getTime(this.ntpTime);
      return new Date(time);
   }

   public static long getTime(long ntpTimeValue) {
      long seconds = ntpTimeValue >>> 32 & 4294967295L;
      long fraction = ntpTimeValue & 4294967295L;
      fraction = Math.round(1000.0 * fraction / 4.2949673E9F);
      long msb = seconds & 2147483648L;
      return msb == 0L ? 2085978496000L + seconds * 1000L + fraction : -2208988800000L + seconds * 1000L + fraction;
   }

   public static TimeStamp getNtpTime(long date) {
      return new TimeStamp(toNtpTime(date));
   }

   public static TimeStamp getCurrentTime() {
      return getNtpTime(System.currentTimeMillis());
   }

   protected static long decodeNtpHexString(String hexString) throws NumberFormatException {
      if (hexString == null) {
         throw new NumberFormatException("null");
      } else {
         int ind = hexString.indexOf(46);
         if (ind == -1) {
            return hexString.length() == 0 ? 0L : Long.parseLong(hexString, 16) << 32;
         } else {
            return Long.parseLong(hexString.substring(0, ind), 16) << 32 | Long.parseLong(hexString.substring(ind + 1), 16);
         }
      }
   }

   public static TimeStamp parseNtpString(String s) throws NumberFormatException {
      return new TimeStamp(decodeNtpHexString(s));
   }

   protected static long toNtpTime(long t) {
      boolean useBase1 = t < 2085978496000L;
      long baseTime;
      if (useBase1) {
         baseTime = t - -2208988800000L;
      } else {
         baseTime = t - 2085978496000L;
      }

      long seconds = baseTime / 1000L;
      long fraction = baseTime % 1000L * 4294967296L / 1000L;
      if (useBase1) {
         seconds |= 2147483648L;
      }

      return seconds << 32 | fraction;
   }

   @Override
   public int hashCode() {
      return (int)(this.ntpTime ^ this.ntpTime >>> 32);
   }

   @Override
   public boolean equals(Object obj) {
      return obj instanceof TimeStamp ? this.ntpTime == ((TimeStamp)obj).ntpValue() : false;
   }

   @Override
   public String toString() {
      return toString(this.ntpTime);
   }

   private static void appendHexString(StringBuilder buf, long l) {
      String s = Long.toHexString(l);

      for (int i = s.length(); i < 8; i++) {
         buf.append('0');
      }

      buf.append(s);
   }

   public static String toString(long ntpTime) {
      StringBuilder buf = new StringBuilder();
      appendHexString(buf, ntpTime >>> 32 & 4294967295L);
      buf.append('.');
      appendHexString(buf, ntpTime & 4294967295L);
      return buf.toString();
   }

   public String toDateString() {
      if (this.simpleFormatter == null) {
         this.simpleFormatter = new SimpleDateFormat("EEE, MMM dd yyyy HH:mm:ss.SSS", Locale.US);
         this.simpleFormatter.setTimeZone(TimeZone.getDefault());
      }

      Date ntpDate = this.getDate();
      return this.simpleFormatter.format(ntpDate);
   }

   public String toUTCString() {
      if (this.utcFormatter == null) {
         this.utcFormatter = new SimpleDateFormat("EEE, MMM dd yyyy HH:mm:ss.SSS 'UTC'", Locale.US);
         this.utcFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
      }

      Date ntpDate = this.getDate();
      return this.utcFormatter.format(ntpDate);
   }

   public int compareTo(TimeStamp anotherTimeStamp) {
      long thisVal = this.ntpTime;
      long anotherVal = anotherTimeStamp.ntpTime;
      return thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1);
   }
}
