package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class DeckPoofEffect extends AbstractGameEffect {
   public DeckPoofEffect(float x, float y, boolean isDeck) {
      for (int i = 0; i < 70; i++) {
         AbstractDungeon.effectsQueue.add(new DeckPoofParticle(x, y, isDeck));
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
