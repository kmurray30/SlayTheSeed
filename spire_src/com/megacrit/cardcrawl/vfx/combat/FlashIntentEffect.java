package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class FlashIntentEffect extends AbstractGameEffect {
   private static final float DURATION = 1.0F;
   private static final float FLASH_INTERVAL = 0.17F;
   private float intervalTimer = 0.0F;
   private Texture img;
   private AbstractMonster m;

   public FlashIntentEffect(Texture img, AbstractMonster m) {
      this.duration = 1.0F;
      this.img = img;
      this.m = m;
   }

   @Override
   public void update() {
      this.intervalTimer = this.intervalTimer - Gdx.graphics.getDeltaTime();
      if (this.intervalTimer < 0.0F && !this.m.isDying) {
         this.intervalTimer = 0.17F;
         AbstractDungeon.effectsQueue.add(new FlashIntentParticle(this.img, this.m));
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
