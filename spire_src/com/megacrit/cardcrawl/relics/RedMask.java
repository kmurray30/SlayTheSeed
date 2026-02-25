package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.WeakPower;

public class RedMask extends AbstractRelic {
   public static final String ID = "Red Mask";
   private static final int WEAK = 1;

   public RedMask() {
      super("Red Mask", "redMask.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.SOLID);
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
         this.addToBot(new ApplyPowerAction(mo, AbstractDungeon.player, new WeakPower(mo, 1, false), 1, true));
      }
   }

   @Override
   public AbstractRelic makeCopy() {
      return new RedMask();
   }
}
