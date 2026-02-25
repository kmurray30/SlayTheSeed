package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class ThirdEyeEffect extends AbstractGameEffect {
   private float x;
   private float y;

   public ThirdEyeEffect(float x, float y) {
      this.x = x;
      this.y = y;
   }

   @Override
   public void update() {
      AbstractDungeon.effectsQueue.add(new ThirdEyeParticleEffect(this.x, this.y, 800.0F, 0.0F));
      AbstractDungeon.effectsQueue.add(new ThirdEyeParticleEffect(this.x, this.y, -800.0F, 0.0F));
      AbstractDungeon.effectsQueue.add(new ThirdEyeParticleEffect(this.x, this.y, 0.0F, 500.0F));
      AbstractDungeon.effectsQueue.add(new ThirdEyeParticleEffect(this.x, this.y, 0.0F, -500.0F));
      AbstractDungeon.effectsQueue.add(new ThirdEyeParticleEffect(this.x, this.y, 600.0F, 0.0F));
      AbstractDungeon.effectsQueue.add(new ThirdEyeParticleEffect(this.x, this.y, -600.0F, 0.0F));
      AbstractDungeon.effectsQueue.add(new ThirdEyeParticleEffect(this.x, this.y, 0.0F, 400.0F));
      AbstractDungeon.effectsQueue.add(new ThirdEyeParticleEffect(this.x, this.y, 0.0F, -400.0F));
      this.isDone = true;
   }

   @Override
   public void render(SpriteBatch sb) {
   }

   @Override
   public void dispose() {
   }
}
