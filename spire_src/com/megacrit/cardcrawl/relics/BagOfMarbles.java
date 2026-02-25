package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.VulnerablePower;

public class BagOfMarbles extends AbstractRelic {
   public static final String ID = "Bag of Marbles";
   private static final int WEAK = 1;

   public BagOfMarbles() {
      super("Bag of Marbles", "marbles.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 1 + this.DESCRIPTIONS[1];
   }

   @Override
   public void atBattleStart() {
      this.flash();

      for (AbstractMonster mo : AbstractDungeon.getCurrRoom().monsters.monsters) {
         this.addToBot(new RelicAboveCreatureAction(mo, this));
         this.addToBot(new ApplyPowerAction(mo, AbstractDungeon.player, new VulnerablePower(mo, 1, false), 1, true));
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new BagOfMarbles();
   }
}
