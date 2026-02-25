package org.apache.commons.net.ntp;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class TimeInfo {
   private final NtpV3Packet _message;
   private List<String> _comments;
   private Long _delay;
   private Long _offset;
   private final long _returnTime;
   private boolean _detailsComputed;

   public TimeInfo(NtpV3Packet message, long returnTime) {
      this(message, returnTime, null, true);
   }

   public TimeInfo(NtpV3Packet message, long returnTime, List<String> comments) {
      this(message, returnTime, comments, true);
   }

   public TimeInfo(NtpV3Packet msgPacket, long returnTime, boolean doComputeDetails) {
      this(msgPacket, returnTime, null, doComputeDetails);
   }

   public TimeInfo(NtpV3Packet message, long returnTime, List<String> comments, boolean doComputeDetails) {
      if (message == null) {
         throw new IllegalArgumentException("message cannot be null");
      } else {
         this._returnTime = returnTime;
         this._message = message;
         this._comments = comments;
         if (doComputeDetails) {
            this.computeDetails();
         }
      }
   }

   public void addComment(String comment) {
      if (this._comments == null) {
         this._comments = new ArrayList<>();
      }

      this._comments.add(comment);
   }

   public void computeDetails() {
      if (!this._detailsComputed) {
         this._detailsComputed = true;
         if (this._comments == null) {
            this._comments = new ArrayList<>();
         }

         TimeStamp origNtpTime = this._message.getOriginateTimeStamp();
         long origTime = origNtpTime.getTime();
         TimeStamp rcvNtpTime = this._message.getReceiveTimeStamp();
         long rcvTime = rcvNtpTime.getTime();
         TimeStamp xmitNtpTime = this._message.getTransmitTimeStamp();
         long xmitTime = xmitNtpTime.getTime();
         if (origNtpTime.ntpValue() == 0L) {
            if (xmitNtpTime.ntpValue() != 0L) {
               this._offset = xmitTime - this._returnTime;
               this._comments.add("Error: zero orig time -- cannot compute delay");
            } else {
               this._comments.add("Error: zero orig time -- cannot compute delay/offset");
            }
         } else if (rcvNtpTime.ntpValue() != 0L && xmitNtpTime.ntpValue() != 0L) {
            long delayValue = this._returnTime - origTime;
            if (xmitTime < rcvTime) {
               this._comments.add("Error: xmitTime < rcvTime");
            } else {
               long delta = xmitTime - rcvTime;
               if (delta <= delayValue) {
                  delayValue -= delta;
               } else if (delta - delayValue == 1L) {
                  if (delayValue != 0L) {
                     this._comments.add("Info: processing time > total network time by 1 ms -> assume zero delay");
                     delayValue = 0L;
                  }
               } else {
                  this._comments.add("Warning: processing time > total network time");
               }
            }

            this._delay = delayValue;
            if (origTime > this._returnTime) {
               this._comments.add("Error: OrigTime > DestRcvTime");
            }

            this._offset = (rcvTime - origTime + (xmitTime - this._returnTime)) / 2L;
         } else {
            this._comments.add("Warning: zero rcvNtpTime or xmitNtpTime");
            if (origTime > this._returnTime) {
               this._comments.add("Error: OrigTime > DestRcvTime");
            } else {
               this._delay = this._returnTime - origTime;
            }

            if (rcvNtpTime.ntpValue() != 0L) {
               this._offset = rcvTime - origTime;
            } else if (xmitNtpTime.ntpValue() != 0L) {
               this._offset = xmitTime - this._returnTime;
            }
         }
      }
   }

   public List<String> getComments() {
      return this._comments;
   }

   public Long getDelay() {
      return this._delay;
   }

   public Long getOffset() {
      return this._offset;
   }

   public NtpV3Packet getMessage() {
      return this._message;
   }

   public InetAddress getAddress() {
      DatagramPacket pkt = this._message.getDatagramPacket();
      return pkt == null ? null : pkt.getAddress();
   }

   public long getReturnTime() {
      return this._returnTime;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         TimeInfo other = (TimeInfo)obj;
         return this._returnTime == other._returnTime && this._message.equals(other._message);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      int prime = 31;
      int result = (int)this._returnTime;
      return 31 * result + this._message.hashCode();
   }
}
