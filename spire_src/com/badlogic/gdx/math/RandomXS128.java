package com.badlogic.gdx.math;

import java.util.Random;

public class RandomXS128 extends Random {
   private static final double NORM_DOUBLE = 1.110223E-16F;
   private static final double NORM_FLOAT = 5.9604645E-8F;
   private long seed0;
   private long seed1;

   public RandomXS128() {
      this.setSeed(new Random().nextLong());
   }

   public RandomXS128(long seed) {
      this.setSeed(seed);
   }

   public RandomXS128(long seed0, long seed1) {
      this.setState(seed0, seed1);
   }

   @Override
   public long nextLong() {
      long s1 = this.seed0;
      long s0 = this.seed1;
      this.seed0 = s0;
      s1 ^= s1 << 23;
      return (this.seed1 = s1 ^ s0 ^ s1 >>> 17 ^ s0 >>> 26) + s0;
   }

   @Override
   protected final int next(int bits) {
      return (int)(this.nextLong() & (1L << bits) - 1L);
   }

   @Override
   public int nextInt() {
      return (int)this.nextLong();
   }

   @Override
   public int nextInt(int n) {
      return (int)this.nextLong(n);
   }

   @Override
   public long nextLong(long n) {
      if (n <= 0L) {
         throw new IllegalArgumentException("n must be positive");
      } else {
         long bits;
         long value;
         do {
            bits = this.nextLong() >>> 1;
            value = bits % n;
         } while (bits - value + (n - 1L) < 0L);

         return value;
      }
   }

   @Override
   public double nextDouble() {
      return (this.nextLong() >>> 11) * 1.110223E-16F;
   }

   @Override
   public float nextFloat() {
      return (float)((this.nextLong() >>> 40) * 5.9604645E-8F);
   }

   @Override
   public boolean nextBoolean() {
      return (this.nextLong() & 1L) != 0L;
   }

   @Override
   public void nextBytes(byte[] bytes) {
      int n = 0;
      int i = bytes.length;

      while (i != 0) {
         n = i < 8 ? i : 8;

         for (long bits = this.nextLong(); n-- != 0; bits >>= 8) {
            bytes[--i] = (byte)bits;
         }
      }
   }

   @Override
   public void setSeed(long seed) {
      long seed0 = murmurHash3(seed == 0L ? Long.MIN_VALUE : seed);
      this.setState(seed0, murmurHash3(seed0));
   }

   public void setState(long seed0, long seed1) {
      this.seed0 = seed0;
      this.seed1 = seed1;
   }

   public long getState(int seed) {
      return seed == 0 ? this.seed0 : this.seed1;
   }

   private static final long murmurHash3(long x) {
      x ^= x >>> 33;
      x *= -49064778989728563L;
      x ^= x >>> 33;
      x *= -4265267296055464877L;
      return x ^ x >>> 33;
   }
}
