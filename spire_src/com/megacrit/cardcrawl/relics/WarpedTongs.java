package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.common.UpgradeRandomCardAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class WarpedTongs extends AbstractRelic {
   public static final String ID = "WarpedTongs";

   public WarpedTongs() {
      super("WarpedTongs", "tongs.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public void atTurnStartPostDraw() {
      this.flash();
      this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
      this.addToBot(new UpgradeRandomCardAction());
   }

   @Override
   public AbstractRelic makeCopy() {
      return new WarpedTongs();
   }
}
