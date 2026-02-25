package com.badlogic.gdx.utils;

public final class TimeUtils {
   private static final long nanosPerMilli = 1000000L;

   public static long nanoTime() {
      return System.nanoTime();
   }

   public static long millis() {
      return System.currentTimeMillis();
   }

   public static long nanosToMillis(long nanos) {
      return nanos / 1000000L;
   }

   public static long millisToNanos(long millis) {
      return millis * 1000000L;
   }

   public static long timeSinceNanos(long prevTime) {
      return nanoTime() - prevTime;
   }

   public static long timeSinceMillis(long prevTime) {
      return millis() - prevTime;
   }
}
