package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class EmpowerEffect extends AbstractGameEffect {
   private static final float SHAKE_DURATION = 0.25F;

   public EmpowerEffect(float x, float y) {
      CardCrawlGame.sound.play("CARD_POWER_IMPACT", 0.1F);

      for (int i = 0; i < 18; i++) {
         AbstractDungeon.effectList.add(new EmpowerCircleEffect(x, y));
      }

      CardCrawlGame.screenShake.rumble(0.25F);
   }

   @Override
   public void update() {
      this.isDone = true;
   }

   @Override
   public void render(SpriteBatch sb) {
   }

   @Override
   public void dispose() {
   }
}
