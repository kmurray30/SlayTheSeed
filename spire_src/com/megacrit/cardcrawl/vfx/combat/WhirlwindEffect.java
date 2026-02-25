package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.BorderLongFlashEffect;

public class WhirlwindEffect extends AbstractGameEffect {
   private int count = 0;
   private float timer = 0.0F;
   private boolean reverse = false;

   public WhirlwindEffect(Color setColor, boolean reverse) {
      this.color = setColor.cpy();
      this.reverse = reverse;
   }

   public WhirlwindEffect() {
      this(new Color(0.9F, 0.9F, 1.0F, 1.0F), false);
   }

   @Override
   public void update() {
      this.timer = this.timer - Gdx.graphics.getDeltaTime();
      if (this.timer < 0.0F) {
         this.timer += 0.05F;
         if (this.count == 0) {
            AbstractDungeon.effectsQueue.add(new BorderLongFlashEffect(this.color.cpy()));
         }

         AbstractDungeon.effectsQueue.add(new WindyParticleEffect(this.color, this.reverse));
         this.count++;
         if (this.count == 18) {
            this.isDone = true;
         }
      }
   }

   @Override
   public void render(SpriteBatch sb) {
   }

   @Override
   public void dispose() {
   }
}
