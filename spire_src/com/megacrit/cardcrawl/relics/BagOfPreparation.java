package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class BagOfPreparation extends AbstractRelic {
   public static final String ID = "Bag of Preparation";
   private static final int NUM_CARDS = 2;

   public BagOfPreparation() {
      super("Bag of Preparation", "bag_of_prep.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 2 + this.DESCRIPTIONS[1];
   }

   @Override
   public void atBattleStart() {
      this.flash();
      this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      this.addToBot(new DrawCardAction(AbstractDungeon.player, 2));
   }

   @Override
   public AbstractRelic makeCopy() {
      return new BagOfPreparation();
   }
}
