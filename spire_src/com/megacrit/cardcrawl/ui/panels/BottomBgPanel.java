package com.megacrit.cardcrawl.ui.panels;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;

public class BottomBgPanel {
   private static final float SNAP_THRESHOLD = 0.3F;
   private static final float LERP_SPEED = 7.0F;
   private float current_y;
   private float target_y;
   private float normal_y = 72.0F * Settings.scale;
   private float hide_y = 0.0F;
   private float overlay_y = Settings.HEIGHT * 0.5F;
   public boolean doneAnimating = true;

   public BottomBgPanel() {
      this.current_y = this.normal_y;
      this.target_y = this.current_y;
   }

   public void changeMode(BottomBgPanel.Mode mode) {
      switch (mode) {
         case NORMAL:
            this.target_y = this.normal_y;
            this.doneAnimating = false;
            break;
         case OVERLAY:
            this.target_y = this.overlay_y;
            this.doneAnimating = false;
            break;
         case HIDDEN:
            this.target_y = this.hide_y;
            this.doneAnimating = false;
      }
   }

   public void updatePositions() {
      if (this.current_y != this.target_y) {
         this.current_y = MathUtils.lerp(this.current_y, this.target_y, Gdx.graphics.getDeltaTime() * 7.0F);
         if (Math.abs(this.current_y - this.target_y) < 0.3F) {
            this.current_y = this.target_y;
            this.doneAnimating = true;
         } else {
            this.doneAnimating = false;
         }
      }
   }

   public static enum Mode {
      NORMAL,
      OVERLAY,
      HIDDEN;
   }
}
