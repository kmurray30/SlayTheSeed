package com.badlogic.gdx.scenes.scene2d.actions;

public abstract class RelativeTemporalAction extends TemporalAction {
   private float lastPercent;

   @Override
   protected void begin() {
      this.lastPercent = 0.0F;
   }

   @Override
   protected void update(float percent) {
      this.updateRelative(percent - this.lastPercent);
      this.lastPercent = percent;
   }

   protected abstract void updateRelative(float var1);
}
