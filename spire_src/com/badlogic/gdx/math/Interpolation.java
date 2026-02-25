package com.badlogic.gdx.math;

public abstract class Interpolation {
   public static final Interpolation linear = new Interpolation() {
      @Override
      public float apply(float a) {
         return a;
      }
   };
   public static final Interpolation smooth = new Interpolation() {
      @Override
      public float apply(float a) {
         return a * a * (3.0F - 2.0F * a);
      }
   };
   public static final Interpolation smooth2 = new Interpolation() {
      @Override
      public float apply(float a) {
         a = a * a * (3.0F - 2.0F * a);
         return a * a * (3.0F - 2.0F * a);
      }
   };
   public static final Interpolation smoother = new Interpolation() {
      @Override
      public float apply(float a) {
         return MathUtils.clamp(a * a * a * (a * (a * 6.0F - 15.0F) + 10.0F), 0.0F, 1.0F);
      }
   };
   public static final Interpolation fade = smoother;
   public static final Interpolation.Pow pow2 = new Interpolation.Pow(2);
   public static final Interpolation.PowIn pow2In = new Interpolation.PowIn(2);
   public static final Interpolation.PowOut pow2Out = new Interpolation.PowOut(2);
   public static final Interpolation pow2InInverse = new Interpolation() {
      @Override
      public float apply(float a) {
         return (float)Math.sqrt(a);
      }
   };
   public static final Interpolation pow2OutInverse = new Interpolation() {
      @Override
      public float apply(float a) {
         return 1.0F - (float)Math.sqrt(-(a - 1.0F));
      }
   };
   public static final Interpolation.Pow pow3 = new Interpolation.Pow(3);
   public static final Interpolation.PowIn pow3In = new Interpolation.PowIn(3);
   public static final Interpolation.PowOut pow3Out = new Interpolation.PowOut(3);
   public static final Interpolation pow3InInverse = new Interpolation() {
      @Override
      public float apply(float a) {
         return (float)Math.cbrt(a);
      }
   };
   public static final Interpolation pow3OutInverse = new Interpolation() {
      @Override
      public float apply(float a) {
         return 1.0F - (float)Math.cbrt(-(a - 1.0F));
      }
   };
   public static final Interpolation.Pow pow4 = new Interpolation.Pow(4);
   public static final Interpolation.PowIn pow4In = new Interpolation.PowIn(4);
   public static final Interpolation.PowOut pow4Out = new Interpolation.PowOut(4);
   public static final Interpolation.Pow pow5 = new Interpolation.Pow(5);
   public static final Interpolation.PowIn pow5In = new Interpolation.PowIn(5);
   public static final Interpolation.PowOut pow5Out = new Interpolation.PowOut(5);
   public static final Interpolation sine = new Interpolation() {
      @Override
      public float apply(float a) {
         return (1.0F - MathUtils.cos(a * (float) Math.PI)) / 2.0F;
      }
   };
   public static final Interpolation sineIn = new Interpolation() {
      @Override
      public float apply(float a) {
         return 1.0F - MathUtils.cos(a * (float) Math.PI / 2.0F);
      }
   };
   public static final Interpolation sineOut = new Interpolation() {
      @Override
      public float apply(float a) {
         return MathUtils.sin(a * (float) Math.PI / 2.0F);
      }
   };
   public static final Interpolation.Exp exp10 = new Interpolation.Exp(2.0F, 10.0F);
   public static final Interpolation.ExpIn exp10In = new Interpolation.ExpIn(2.0F, 10.0F);
   public static final Interpolation.ExpOut exp10Out = new Interpolation.ExpOut(2.0F, 10.0F);
   public static final Interpolation.Exp exp5 = new Interpolation.Exp(2.0F, 5.0F);
   public static final Interpolation.ExpIn exp5In = new Interpolation.ExpIn(2.0F, 5.0F);
   public static final Interpolation.ExpOut exp5Out = new Interpolation.ExpOut(2.0F, 5.0F);
   public static final Interpolation circle = new Interpolation() {
      @Override
      public float apply(float a) {
         if (a <= 0.5F) {
            a *= 2.0F;
            return (1.0F - (float)Math.sqrt(1.0F - a * a)) / 2.0F;
         } else {
            a = --a * 2.0F;
            return ((float)Math.sqrt(1.0F - a * a) + 1.0F) / 2.0F;
         }
      }
   };
   public static final Interpolation circleIn = new Interpolation() {
      @Override
      public float apply(float a) {
         return 1.0F - (float)Math.sqrt(1.0F - a * a);
      }
   };
   public static final Interpolation circleOut = new Interpolation() {
      @Override
      public float apply(float a) {
         a--;
         return (float)Math.sqrt(1.0F - a * a);
      }
   };
   public static final Interpolation.Elastic elastic = new Interpolation.Elastic(2.0F, 10.0F, 7, 1.0F);
   public static final Interpolation.ElasticIn elasticIn = new Interpolation.ElasticIn(2.0F, 10.0F, 6, 1.0F);
   public static final Interpolation.ElasticOut elasticOut = new Interpolation.ElasticOut(2.0F, 10.0F, 7, 1.0F);
   public static final Interpolation.Swing swing = new Interpolation.Swing(1.5F);
   public static final Interpolation.SwingIn swingIn = new Interpolation.SwingIn(2.0F);
   public static final Interpolation.SwingOut swingOut = new Interpolation.SwingOut(2.0F);
   public static final Interpolation.Bounce bounce = new Interpolation.Bounce(4);
   public static final Interpolation.BounceIn bounceIn = new Interpolation.BounceIn(4);
   public static final Interpolation.BounceOut bounceOut = new Interpolation.BounceOut(4);

   public abstract float apply(float var1);

   public float apply(float start, float end, float a) {
      return start + (end - start) * this.apply(a);
   }

   public static class Bounce extends Interpolation.BounceOut {
      public Bounce(float[] widths, float[] heights) {
         super(widths, heights);
      }

      public Bounce(int bounces) {
         super(bounces);
      }

      private float out(float a) {
         float test = a + this.widths[0] / 2.0F;
         return test < this.widths[0] ? test / (this.widths[0] / 2.0F) - 1.0F : super.apply(a);
      }

      @Override
      public float apply(float a) {
         return a <= 0.5F ? (1.0F - this.out(1.0F - a * 2.0F)) / 2.0F : this.out(a * 2.0F - 1.0F) / 2.0F + 0.5F;
      }
   }

   public static class BounceIn extends Interpolation.BounceOut {
      public BounceIn(float[] widths, float[] heights) {
         super(widths, heights);
      }

      public BounceIn(int bounces) {
         super(bounces);
      }

      @Override
      public float apply(float a) {
         return 1.0F - super.apply(1.0F - a);
      }
   }

   public static class BounceOut extends Interpolation {
      final float[] widths;
      final float[] heights;

      public BounceOut(float[] widths, float[] heights) {
         if (widths.length != heights.length) {
            throw new IllegalArgumentException("Must be the same number of widths and heights.");
         } else {
            this.widths = widths;
            this.heights = heights;
         }
      }

      public BounceOut(int bounces) {
         if (bounces >= 2 && bounces <= 5) {
            this.widths = new float[bounces];
            this.heights = new float[bounces];
            this.heights[0] = 1.0F;
            switch (bounces) {
               case 2:
                  this.widths[0] = 0.6F;
                  this.widths[1] = 0.4F;
                  this.heights[1] = 0.33F;
                  break;
               case 3:
                  this.widths[0] = 0.4F;
                  this.widths[1] = 0.4F;
                  this.widths[2] = 0.2F;
                  this.heights[1] = 0.33F;
                  this.heights[2] = 0.1F;
                  break;
               case 4:
                  this.widths[0] = 0.34F;
                  this.widths[1] = 0.34F;
                  this.widths[2] = 0.2F;
                  this.widths[3] = 0.15F;
                  this.heights[1] = 0.26F;
                  this.heights[2] = 0.11F;
                  this.heights[3] = 0.03F;
                  break;
               case 5:
                  this.widths[0] = 0.3F;
                  this.widths[1] = 0.3F;
                  this.widths[2] = 0.2F;
                  this.widths[3] = 0.1F;
                  this.widths[4] = 0.1F;
                  this.heights[1] = 0.45F;
                  this.heights[2] = 0.3F;
                  this.heights[3] = 0.15F;
                  this.heights[4] = 0.06F;
            }

            this.widths[0] = this.widths[0] * 2.0F;
         } else {
            throw new IllegalArgumentException("bounces cannot be < 2 or > 5: " + bounces);
         }
      }

      @Override
      public float apply(float a) {
         if (a == 1.0F) {
            return 1.0F;
         } else {
            a += this.widths[0] / 2.0F;
            float width = 0.0F;
            float height = 0.0F;
            int i = 0;

            for (int n = this.widths.length; i < n; i++) {
               width = this.widths[i];
               if (a <= width) {
                  height = this.heights[i];
                  break;
               }

               a -= width;
            }

            a /= width;
            float z = 4.0F / width * height * a;
            return 1.0F - (z - z * a) * width;
         }
      }
   }

   public static class Elastic extends Interpolation {
      final float value;
      final float power;
      final float scale;
      final float bounces;

      public Elastic(float value, float power, int bounces, float scale) {
         this.value = value;
         this.power = power;
         this.scale = scale;
         this.bounces = bounces * (float) Math.PI * (bounces % 2 == 0 ? 1 : -1);
      }

      @Override
      public float apply(float a) {
         if (a <= 0.5F) {
            a *= 2.0F;
            return (float)Math.pow(this.value, this.power * (a - 1.0F)) * MathUtils.sin(a * this.bounces) * this.scale / 2.0F;
         } else {
            a = 1.0F - a;
            a *= 2.0F;
            return 1.0F - (float)Math.pow(this.value, this.power * (a - 1.0F)) * MathUtils.sin(a * this.bounces) * this.scale / 2.0F;
         }
      }
   }

   public static class ElasticIn extends Interpolation.Elastic {
      public ElasticIn(float value, float power, int bounces, float scale) {
         super(value, power, bounces, scale);
      }

      @Override
      public float apply(float a) {
         return a >= 0.99 ? 1.0F : (float)Math.pow(this.value, this.power * (a - 1.0F)) * MathUtils.sin(a * this.bounces) * this.scale;
      }
   }

   public static class ElasticOut extends Interpolation.Elastic {
      public ElasticOut(float value, float power, int bounces, float scale) {
         super(value, power, bounces, scale);
      }

      @Override
      public float apply(float a) {
         if (a == 0.0F) {
            return 0.0F;
         } else {
            a = 1.0F - a;
            return 1.0F - (float)Math.pow(this.value, this.power * (a - 1.0F)) * MathUtils.sin(a * this.bounces) * this.scale;
         }
      }
   }

   public static class Exp extends Interpolation {
      final float value;
      final float power;
      final float min;
      final float scale;

      public Exp(float value, float power) {
         this.value = value;
         this.power = power;
         this.min = (float)Math.pow(value, -power);
         this.scale = 1.0F / (1.0F - this.min);
      }

      @Override
      public float apply(float a) {
         return a <= 0.5F
            ? ((float)Math.pow(this.value, this.power * (a * 2.0F - 1.0F)) - this.min) * this.scale / 2.0F
            : (2.0F - ((float)Math.pow(this.value, -this.power * (a * 2.0F - 1.0F)) - this.min) * this.scale) / 2.0F;
      }
   }

   public static class ExpIn extends Interpolation.Exp {
      public ExpIn(float value, float power) {
         super(value, power);
      }

      @Override
      public float apply(float a) {
         return ((float)Math.pow(this.value, this.power * (a - 1.0F)) - this.min) * this.scale;
      }
   }

   public static class ExpOut extends Interpolation.Exp {
      public ExpOut(float value, float power) {
         super(value, power);
      }

      @Override
      public float apply(float a) {
         return 1.0F - ((float)Math.pow(this.value, -this.power * a) - this.min) * this.scale;
      }
   }

   public static class Pow extends Interpolation {
      final int power;

      public Pow(int power) {
         this.power = power;
      }

      @Override
      public float apply(float a) {
         return a <= 0.5F
            ? (float)Math.pow(a * 2.0F, this.power) / 2.0F
            : (float)Math.pow((a - 1.0F) * 2.0F, this.power) / (this.power % 2 == 0 ? -2 : 2) + 1.0F;
      }
   }

   public static class PowIn extends Interpolation.Pow {
      public PowIn(int power) {
         super(power);
      }

      @Override
      public float apply(float a) {
         return (float)Math.pow(a, this.power);
      }
   }

   public static class PowOut extends Interpolation.Pow {
      public PowOut(int power) {
         super(power);
      }

      @Override
      public float apply(float a) {
         return (float)Math.pow(a - 1.0F, this.power) * (this.power % 2 == 0 ? -1 : 1) + 1.0F;
      }
   }

   public static class Swing extends Interpolation {
      private final float scale;

      public Swing(float scale) {
         this.scale = scale * 2.0F;
      }

      @Override
      public float apply(float a) {
         if (a <= 0.5F) {
            a *= 2.0F;
            return a * a * ((this.scale + 1.0F) * a - this.scale) / 2.0F;
         } else {
            a = --a * 2.0F;
            return a * a * ((this.scale + 1.0F) * a + this.scale) / 2.0F + 1.0F;
         }
      }
   }

   public static class SwingIn extends Interpolation {
      private final float scale;

      public SwingIn(float scale) {
         this.scale = scale;
      }

      @Override
      public float apply(float a) {
         return a * a * ((this.scale + 1.0F) * a - this.scale);
      }
   }

   public static class SwingOut extends Interpolation {
      private final float scale;

      public SwingOut(float scale) {
         this.scale = scale;
      }

      @Override
      public float apply(float a) {
         return --a * a * ((this.scale + 1.0F) * a + this.scale) + 1.0F;
      }
   }
}
