package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class HornCleat extends AbstractRelic {
   public static final String ID = "HornCleat";
   private static final int TURN_ACTIVATION = 2;

   public HornCleat() {
      super("HornCleat", "horn_cleat.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.HEAVY);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 14 + this.DESCRIPTIONS[1];
   }

   @Override
   public void atBattleStart() {
      this.counter = 0;
   }

   @Override
   public void atTurnStart() {
      if (!this.grayscale) {
         this.counter++;
      }

      if (this.counter == 2) {
         this.flash();
         this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
         this.addToBot(new GainBlockAction(AbstractDungeon.player, AbstractDungeon.player, 14));
         this.counter = -1;
         this.grayscale = true;
      }
   }

   @Override
   public void onVictory() {
      this.counter = -1;
      this.grayscale = false;
   }

   @Override
   public AbstractRelic makeCopy() {
      return new HornCleat();
   }
}
