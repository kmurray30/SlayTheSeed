package com.badlogic.gdx.math;

import java.util.Random;

public final class MathUtils {
   public static final float nanoToSec = 1.0E-9F;
   public static final float FLOAT_ROUNDING_ERROR = 1.0E-6F;
   public static final float PI = (float) Math.PI;
   public static final float PI2 = (float) (Math.PI * 2);
   public static final float E = (float) Math.E;
   private static final int SIN_BITS = 14;
   private static final int SIN_MASK = 16383;
   private static final int SIN_COUNT = 16384;
   private static final float radFull = (float) (Math.PI * 2);
   private static final float degFull = 360.0F;
   private static final float radToIndex = 2607.5945F;
   private static final float degToIndex = 45.511112F;
   public static final float radiansToDegrees = 180.0F / (float)Math.PI;
   public static final float radDeg = 180.0F / (float)Math.PI;
   public static final float degreesToRadians = (float) (Math.PI / 180.0);
   public static final float degRad = (float) (Math.PI / 180.0);
   public static Random random = new RandomXS128();
   private static final int BIG_ENOUGH_INT = 16384;
   private static final double BIG_ENOUGH_FLOOR = 16384.0;
   private static final double CEIL = 0.9999999;
   private static final double BIG_ENOUGH_CEIL = 16384.999999999996;
   private static final double BIG_ENOUGH_ROUND = 16384.5;

   public static float sin(float radians) {
      return MathUtils.Sin.table[(int)(radians * 2607.5945F) & 16383];
   }

   public static float cos(float radians) {
      return MathUtils.Sin.table[(int)((radians + (float) (Math.PI / 2)) * 2607.5945F) & 16383];
   }

   public static float sinDeg(float degrees) {
      return MathUtils.Sin.table[(int)(degrees * 45.511112F) & 16383];
   }

   public static float cosDeg(float degrees) {
      return MathUtils.Sin.table[(int)((degrees + 90.0F) * 45.511112F) & 16383];
   }

   public static float atan2(float y, float x) {
      if (x == 0.0F) {
         if (y > 0.0F) {
            return (float) (Math.PI / 2);
         } else {
            return y == 0.0F ? 0.0F : (float) (-Math.PI / 2);
         }
      } else {
         float z = y / x;
         if (Math.abs(z) < 1.0F) {
            float atan = z / (1.0F + 0.28F * z * z);
            return x < 0.0F ? atan + (y < 0.0F ? (float) -Math.PI : (float) Math.PI) : atan;
         } else {
            float atan = (float) (Math.PI / 2) - z / (z * z + 0.28F);
            return y < 0.0F ? atan - (float) Math.PI : atan;
         }
      }
   }

   public static int random(int range) {
      return random.nextInt(range + 1);
   }

   public static int random(int start, int end) {
      return start + random.nextInt(end - start + 1);
   }

   public static long random(long range) {
      return (long)(random.nextDouble() * range);
   }

   public static long random(long start, long end) {
      return start + (long)(random.nextDouble() * (end - start));
   }

   public static boolean randomBoolean() {
      return random.nextBoolean();
   }

   public static boolean randomBoolean(float chance) {
      return random() < chance;
   }

   public static float random() {
      return random.nextFloat();
   }

   public static float random(float range) {
      return random.nextFloat() * range;
   }

   public static float random(float start, float end) {
      return start + random.nextFloat() * (end - start);
   }

   public static int randomSign() {
      return 1 | random.nextInt() >> 31;
   }

   public static float randomTriangular() {
      return random.nextFloat() - random.nextFloat();
   }

   public static float randomTriangular(float max) {
      return (random.nextFloat() - random.nextFloat()) * max;
   }

   public static float randomTriangular(float min, float max) {
      return randomTriangular(min, max, (min + max) * 0.5F);
   }

   public static float randomTriangular(float min, float max, float mode) {
      float u = random.nextFloat();
      float d = max - min;
      return u <= (mode - min) / d ? min + (float)Math.sqrt(u * d * (mode - min)) : max - (float)Math.sqrt((1.0F - u) * d * (max - mode));
   }

   public static int nextPowerOfTwo(int value) {
      if (value == 0) {
         return 1;
      } else {
         value = --value | value >> 1;
         value |= value >> 2;
         value |= value >> 4;
         value |= value >> 8;
         value |= value >> 16;
         return value + 1;
      }
   }

   public static boolean isPowerOfTwo(int value) {
      return value != 0 && (value & value - 1) == 0;
   }

   public static short clamp(short value, short min, short max) {
      if (value < min) {
         return min;
      } else {
         return value > max ? max : value;
      }
   }

   public static int clamp(int value, int min, int max) {
      if (value < min) {
         return min;
      } else {
         return value > max ? max : value;
      }
   }

   public static long clamp(long value, long min, long max) {
      if (value < min) {
         return min;
      } else {
         return value > max ? max : value;
      }
   }

   public static float clamp(float value, float min, float max) {
      if (value < min) {
         return min;
      } else {
         return value > max ? max : value;
      }
   }

   public static double clamp(double value, double min, double max) {
      if (value < min) {
         return min;
      } else {
         return value > max ? max : value;
      }
   }

   public static float lerp(float fromValue, float toValue, float progress) {
      return fromValue + (toValue - fromValue) * progress;
   }

   public static float lerpAngle(float fromRadians, float toRadians, float progress) {
      float delta = (toRadians - fromRadians + (float) (Math.PI * 2) + (float) Math.PI) % (float) (Math.PI * 2) - (float) Math.PI;
      return (fromRadians + delta * progress + (float) (Math.PI * 2)) % (float) (Math.PI * 2);
   }

   public static float lerpAngleDeg(float fromDegrees, float toDegrees, float progress) {
      float delta = (toDegrees - fromDegrees + 360.0F + 180.0F) % 360.0F - 180.0F;
      return (fromDegrees + delta * progress + 360.0F) % 360.0F;
   }

   public static int floor(float value) {
      return (int)(value + 16384.0) - 16384;
   }

   public static int floorPositive(float value) {
      return (int)value;
   }

   public static int ceil(float value) {
      return 16384 - (int)(16384.0 - value);
   }

   public static int ceilPositive(float value) {
      return (int)(value + 0.9999999);
   }

   public static int round(float value) {
      return (int)(value + 16384.5) - 16384;
   }

   public static int roundPositive(float value) {
      return (int)(value + 0.5F);
   }

   public static boolean isZero(float value) {
      return Math.abs(value) <= 1.0E-6F;
   }

   public static boolean isZero(float value, float tolerance) {
      return Math.abs(value) <= tolerance;
   }

   public static boolean isEqual(float a, float b) {
      return Math.abs(a - b) <= 1.0E-6F;
   }

   public static boolean isEqual(float a, float b, float tolerance) {
      return Math.abs(a - b) <= tolerance;
   }

   public static float log(float a, float value) {
      return (float)(Math.log(value) / Math.log(a));
   }

   public static float log2(float value) {
      return log(2.0F, value);
   }

   private static class Sin {
      static final float[] table = new float[16384];

      static {
         for (int i = 0; i < 16384; i++) {
            table[i] = (float)Math.sin((i + 0.5F) / 16384.0F * (float) (Math.PI * 2));
         }

         for (int i = 0; i < 360; i += 90) {
            table[(int)(i * 45.511112F) & 16383] = (float)Math.sin(i * (float) (Math.PI / 180.0));
         }
      }
   }
}
