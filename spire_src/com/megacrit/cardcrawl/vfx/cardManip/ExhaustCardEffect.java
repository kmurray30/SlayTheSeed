package com.megacrit.cardcrawl.vfx.cardManip;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.ExhaustBlurEffect;
import com.megacrit.cardcrawl.vfx.ExhaustEmberEffect;

public class ExhaustCardEffect extends AbstractGameEffect {
   private AbstractCard c;
   private static final float DUR = 1.0F;

   public ExhaustCardEffect(AbstractCard c) {
      this.duration = 1.0F;
      this.c = c;
   }

   @Override
   public void update() {
      if (this.duration == 1.0F) {
         CardCrawlGame.sound.play("CARD_EXHAUST", 0.2F);

         for (int i = 0; i < 90; i++) {
            AbstractDungeon.effectsQueue.add(new ExhaustBlurEffect(this.c.current_x, this.c.current_y));
         }

         for (int i = 0; i < 50; i++) {
            AbstractDungeon.effectsQueue.add(new ExhaustEmberEffect(this.c.current_x, this.c.current_y));
         }
      }

      this.duration = this.duration - Gdx.graphics.getDeltaTime();
      if (!this.c.fadingOut && this.duration < 0.7F && !AbstractDungeon.player.hand.contains(this.c)) {
         this.c.fadingOut = true;
      }

      if (this.duration < 0.0F) {
         this.isDone = true;
         this.c.resetAttributes();
      }
   }

   @Override
   public void render(SpriteBatch sb) {
      this.c.render(sb);
   }

   @Override
   public void dispose() {
   }
}
