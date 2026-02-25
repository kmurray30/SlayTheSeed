package javazoom.jl.decoder;

public class OutputBuffer {
   public static final int BUFFERSIZE = 2304;
   private static final int MAXCHANNELS = 2;
   private Float replayGainScale;
   private int channels;
   private byte[] buffer;
   private int[] channelPointer;
   private boolean isBigEndian;

   public OutputBuffer(int channels, boolean isBigEndian) {
      this.channels = channels;
      this.isBigEndian = isBigEndian;
      this.buffer = new byte[2304 * channels];
      this.channelPointer = new int[channels];
      this.reset();
   }

   private void append(int channel, short value) {
      byte firstByte;
      byte secondByte;
      if (this.isBigEndian) {
         firstByte = (byte)(value >>> 8 & 0xFF);
         secondByte = (byte)(value & 255);
      } else {
         firstByte = (byte)(value & 255);
         secondByte = (byte)(value >>> 8 & 0xFF);
      }

      this.buffer[this.channelPointer[channel]] = firstByte;
      this.buffer[this.channelPointer[channel] + 1] = secondByte;
      this.channelPointer[channel] = this.channelPointer[channel] + this.channels * 2;
   }

   public void appendSamples(int channel, float[] f) {
      if (this.replayGainScale != null) {
         int i = 0;

         while (i < 32) {
            short s = this.clip(f[i++] * this.replayGainScale);
            this.append(channel, s);
         }
      } else {
         int i = 0;

         while (i < 32) {
            short s = this.clip(f[i++]);
            this.append(channel, s);
         }
      }
   }

   public byte[] getBuffer() {
      return this.buffer;
   }

   // $VF: Could not verify finally blocks. A semaphore variable has been added to preserve control flow.
   // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
   public int reset() {
      boolean var7 = false /* VF: Semaphore variable */;

      int var2;
      try {
         var7 = true;
         int index = this.channels - 1;
         var2 = this.channelPointer[index] - index * 2;
         var7 = false;
      } finally {
         if (var7) {
            for (int i = 0; i < this.channels; i++) {
               this.channelPointer[i] = i * 2;
            }
         }
      }

      for (int i = 0; i < this.channels; i++) {
         this.channelPointer[i] = i * 2;
      }

      return var2;
   }

   public void setReplayGainScale(Float replayGainScale) {
      this.replayGainScale = replayGainScale;
   }

   public boolean isStereo() {
      return this.channelPointer[1] == 2;
   }

   private final short clip(float sample) {
      return sample > 32767.0F ? 32767 : (sample < -32768.0F ? -32768 : (short)sample);
   }
}
