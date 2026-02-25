package com.badlogic.gdx.backends.lwjgl.audio;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.ByteArrayOutputStream;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.MP3Decoder;
import javazoom.jl.decoder.OutputBuffer;

public class Mp3 {
   public static class Music extends OpenALMusic {
      private Bitstream bitstream;
      private OutputBuffer outputBuffer;
      private MP3Decoder decoder;

      public Music(OpenALAudio audio, FileHandle file) {
         super(audio, file);
         if (!audio.noDevice) {
            this.bitstream = new Bitstream(file.read());
            this.decoder = new MP3Decoder();
            this.bufferOverhead = 4096;

            try {
               Header header = this.bitstream.readFrame();
               if (header == null) {
                  throw new GdxRuntimeException("Empty MP3");
               } else {
                  int channels = header.mode() == 3 ? 1 : 2;
                  this.outputBuffer = new OutputBuffer(channels, false);
                  this.decoder.setOutputBuffer(this.outputBuffer);
                  this.setup(channels, header.getSampleRate());
               }
            } catch (BitstreamException var5) {
               throw new GdxRuntimeException("error while preloading mp3", var5);
            }
         }
      }

      @Override
      public int read(byte[] buffer) {
         try {
            boolean setup = this.bitstream == null;
            if (setup) {
               this.bitstream = new Bitstream(this.file.read());
               this.decoder = new MP3Decoder();
            }

            int totalLength = 0;
            int minRequiredLength = buffer.length - 4608;

            while (totalLength <= minRequiredLength) {
               Header header = this.bitstream.readFrame();
               if (header == null) {
                  break;
               }

               if (setup) {
                  int channels = header.mode() == 3 ? 1 : 2;
                  this.outputBuffer = new OutputBuffer(channels, false);
                  this.decoder.setOutputBuffer(this.outputBuffer);
                  this.setup(channels, header.getSampleRate());
                  setup = false;
               }

               try {
                  this.decoder.decodeFrame(header, this.bitstream);
               } catch (Exception var7) {
               }

               this.bitstream.closeFrame();
               int length = this.outputBuffer.reset();
               System.arraycopy(this.outputBuffer.getBuffer(), 0, buffer, totalLength, length);
               totalLength += length;
            }

            return totalLength;
         } catch (Throwable var8) {
            this.reset();
            throw new GdxRuntimeException("Error reading audio data.", var8);
         }
      }

      @Override
      public void reset() {
         if (this.bitstream != null) {
            try {
               this.bitstream.close();
            } catch (BitstreamException var2) {
            }

            this.bitstream = null;
         }
      }
   }

   public static class Sound extends OpenALSound {
      public Sound(OpenALAudio audio, FileHandle file) {
         super(audio);
         if (!audio.noDevice) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
            Bitstream bitstream = new Bitstream(file.read());
            MP3Decoder decoder = new MP3Decoder();

            try {
               OutputBuffer outputBuffer = null;
               int sampleRate = -1;
               int channels = -1;

               while (true) {
                  Header header = bitstream.readFrame();
                  if (header == null) {
                     bitstream.close();
                     this.setup(output.toByteArray(), channels, sampleRate);
                     return;
                  }

                  if (outputBuffer == null) {
                     channels = header.mode() == 3 ? 1 : 2;
                     outputBuffer = new OutputBuffer(channels, false);
                     decoder.setOutputBuffer(outputBuffer);
                     sampleRate = header.getSampleRate();
                  }

                  try {
                     decoder.decodeFrame(header, bitstream);
                  } catch (Exception var11) {
                  }

                  bitstream.closeFrame();
                  output.write(outputBuffer.getBuffer(), 0, outputBuffer.reset());
               }
            } catch (Throwable var12) {
               throw new GdxRuntimeException("Error reading audio data.", var12);
            }
         }
      }
   }
}
