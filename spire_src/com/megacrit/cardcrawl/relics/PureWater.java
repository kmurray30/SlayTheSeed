package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.tempCards.Miracle;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class PureWater extends AbstractRelic {
   public static final String ID = "PureWater";

   public PureWater() {
      super("PureWater", "clean_water.png", AbstractRelic.RelicTier.STARTER, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void atBattleStartPreDraw() {
      this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      this.addToBot(new MakeTempCardInHandAction(new Miracle(), 1, false));
   }

   @Override
   public AbstractRelic makeCopy() {
      return new PureWater();
   }
}
