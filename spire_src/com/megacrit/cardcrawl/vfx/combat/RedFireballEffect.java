package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class RedFireballEffect extends AbstractGameEffect {
   private static final float FIREBALL_INTERVAL = 0.016F;
   private float x;
   private float y;
   private float startX;
   private float startY;
   private float targetX;
   private float targetY;
   private float vfxTimer = 0.0F;
   private int timesUpgraded;

   public RedFireballEffect(float startX, float startY, float targetX, float targetY, int timesUpgraded) {
      this.startingDuration = 0.3F;
      this.duration = 0.3F;
      this.startX = startX;
      this.startY = startY;
      this.targetX = targetX;
      this.targetY = targetY;
      this.timesUpgraded = timesUpgraded;
      this.x = startX;
      this.y = startY;
   }

   @Override
   public void update() {
      this.x = Interpolation.fade.apply(this.targetX, this.startX, this.duration / this.startingDuration);
      if (this.duration > this.startingDuration / 2.0F) {
         this.y = Interpolation.pow4In.apply(this.startY, this.targetY, (this.duration - this.startingDuration / 2.0F) / this.startingDuration * 2.0F);
      } else {
         this.y = Interpolation.pow4Out.apply(this.targetY, this.startY, this.duration / this.startingDuration * 2.0F);
      }

      this.vfxTimer = this.vfxTimer - Gdx.graphics.getDeltaTime();
      if (this.vfxTimer < 0.0F) {
         this.vfxTimer += 0.016F;
         AbstractDungeon.effectsQueue.add(new RedFireBurstParticleEffect(this.x, this.y, this.timesUpgraded));
         AbstractDungeon.effectsQueue.add(new RedFireBurstParticleEffect(this.x, this.y, this.timesUpgraded));
      }

      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (this.duration < 0.0F) {
         this.isDone = true;
      }
   }

   @Override
   public void render(SpriteBatch sb) {
   }

   @Override
   public void dispose() {
   }
}
