package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class DeadBranch extends AbstractRelic {
   public static final String ID = "Dead Branch";

   public DeadBranch() {
      super("Dead Branch", "deadBranch.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public void onExhaust(AbstractCard card) {
      if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
         this.flash();
         this.addToBot(new RelicAboveCreatureAction(AbstractDungeon.player, this));
         this.addToBot(new MakeTempCardInHandAction(AbstractDungeon.returnTrulyRandomCardInCombat().makeCopy(), false));
      }
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new DeadBranch();
   }
}
