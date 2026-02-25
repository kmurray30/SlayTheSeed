package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.megacrit.cardcrawl.core.Settings;

public class ScreenShake {
   private float x = 0.0F;
   private float duration = 0.0F;
   private float startDuration = 0.0F;
   private float intensityValue;
   private float intervalSpeed;
   private boolean vertical;

   public void shake(ScreenShake.ShakeIntensity intensity, ScreenShake.ShakeDur dur, boolean isVertical) {
      this.duration = this.getDuration(dur);
      this.startDuration = this.duration;
      this.intensityValue = this.getIntensity(intensity);
      this.vertical = isVertical;
      this.intervalSpeed = 0.3F;
   }

   public void rumble(float dur) {
      this.duration = dur;
      this.startDuration = dur;
      this.intensityValue = 10.0F;
      this.vertical = false;
      this.intervalSpeed = 0.7F;
   }

   public void mildRumble(float dur) {
      this.duration = dur;
      this.startDuration = dur;
      this.intensityValue = 3.0F;
      this.vertical = false;
      this.intervalSpeed = 0.7F;
   }

   public void update(FitViewport viewport) {
      if (Settings.HORIZ_LETTERBOX_AMT == 0 && Settings.VERT_LETTERBOX_AMT == 0) {
         if (this.duration != 0.0F) {
            this.duration = this.duration - Gdx.graphics.getDeltaTime();
            if (this.duration < 0.0F) {
               this.duration = 0.0F;
               viewport.update(Settings.M_W, Settings.M_H);
               return;
            }

            float tmp = Interpolation.fade.apply(0.1F, this.intensityValue, this.duration / this.startDuration);
            this.x = MathUtils.cosDeg((float)(System.currentTimeMillis() % 360L) / this.intervalSpeed) * tmp;
            if (Settings.SCREEN_SHAKE) {
               if (this.vertical) {
                  viewport.update(Settings.M_W, (int)(Settings.M_H + Math.abs(this.x)));
               } else {
                  viewport.update((int)(Settings.M_W + this.x), Settings.M_H);
               }
            }
         }
      }
   }

   private float getIntensity(ScreenShake.ShakeIntensity intensity) {
      switch (intensity) {
         case LOW:
            return 20.0F * Settings.scale;
         case MED:
            return 50.0F * Settings.scale;
         default:
            return 100.0F * Settings.scale;
      }
   }

   private float getDuration(ScreenShake.ShakeDur dur) {
      switch (dur) {
         case SHORT:
            return 0.3F;
         case MED:
            return 0.5F;
         case LONG:
            return 1.0F;
         case XLONG:
            return 3.0F;
         default:
            return 1.0F;
      }
   }

   public static enum ShakeDur {
      SHORT,
      MED,
      LONG,
      XLONG;
   }

   public static enum ShakeIntensity {
      LOW,
      MED,
      HIGH;
   }
}
