package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.tempCards.Shiv;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class NinjaScroll extends AbstractRelic {
   public static final String ID = "Ninja Scroll";
   private static final int AMOUNT = 3;

   public NinjaScroll() {
      super("Ninja Scroll", "ninjaScroll.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 3 + this.DESCRIPTIONS[1];
   }

   @Override
   public void atBattleStartPreDraw() {
      this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      this.addToBot(new MakeTempCardInHandAction(new Shiv(), 3, false));
   }

   @Override
   public AbstractRelic makeCopy() {
      return new NinjaScroll();
   }
}
