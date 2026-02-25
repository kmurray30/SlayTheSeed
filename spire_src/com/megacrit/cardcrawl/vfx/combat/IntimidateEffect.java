package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class IntimidateEffect extends AbstractGameEffect {
   private static final float EFFECT_DUR = 1.0F;
   private float x;
   private float y;
   private float vfxTimer;
   private static final float VFX_INTERVAL = 0.016F;

   public IntimidateEffect(float newX, float newY) {
      this.duration = 1.0F;
      this.x = newX;
      this.y = newY;
   }

   @Override
   public void update() {
      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      this.vfxTimer = this.vfxTimer - Gdx.graphics.getDeltaTime();
      if (this.vfxTimer < 0.0F) {
         this.vfxTimer = 0.016F;
         AbstractDungeon.effectsQueue.add(new WobblyLineEffect(this.x, this.y, Settings.CREAM_COLOR.cpy()));
      }

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
