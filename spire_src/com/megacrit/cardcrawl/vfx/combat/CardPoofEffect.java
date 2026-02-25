package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class CardPoofEffect extends AbstractGameEffect {
   public CardPoofEffect(float x, float y) {
      for (int i = 0; i < 50; i++) {
         AbstractDungeon.effectsQueue.add(new CardPoofParticle(x, y));
      }
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
