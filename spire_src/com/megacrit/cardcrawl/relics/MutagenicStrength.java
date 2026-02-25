package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.LoseStrengthPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class MutagenicStrength extends AbstractRelic {
   public static final String ID = "MutagenicStrength";
   private static final int STR_AMT = 3;

   public MutagenicStrength() {
      super("MutagenicStrength", "mutagen.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 3 + this.DESCRIPTIONS[1] + 3 + this.DESCRIPTIONS[2];
   }

   @Override
   public void atBattleStart() {
      this.flash();
      this.addToTop(new ApplyPowerAction(AbstractDungeon.player, AbstractDungeon.player, new StrengthPower(AbstractDungeon.player, 3), 3));
      this.addToTop(new ApplyPowerAction(AbstractDungeon.player, AbstractDungeon.player, new LoseStrengthPower(AbstractDungeon.player, 3), 3));
      this.addToTop(new RelicAboveCreatureAction(AbstractDungeon.player, this));
   }

   @Override
   public AbstractRelic makeCopy() {
      return new MutagenicStrength();
   }
}
