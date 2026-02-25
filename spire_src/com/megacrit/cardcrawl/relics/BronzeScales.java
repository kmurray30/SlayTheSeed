package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.ThornsPower;

public class BronzeScales extends AbstractRelic {
   public static final String ID = "Bronze Scales";
   private static final int DAMAGE = 3;

   public BronzeScales() {
      super("Bronze Scales", "bronzeScales.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 3 + this.DESCRIPTIONS[1];
   }

   @Override
   public void atBattleStart() {
      this.flash();
      this.addToTop(new ApplyPowerAction(AbstractDungeon.player, AbstractDungeon.player, new ThornsPower(AbstractDungeon.player, 3), 3));
   }

   @Override
   public AbstractRelic makeCopy() {
      return new BronzeScales();
   }
}
