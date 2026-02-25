package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class LoseDexterityPower extends AbstractPower {
   public static final String POWER_ID = "DexLoss";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("DexLoss");

   public LoseDexterityPower(AbstractCreature owner, int newAmount) {
      this.name = powerStrings.NAME;
      this.ID = "DexLoss";
      this.owner = owner;
      this.amount = newAmount;
      this.type = AbstractPower.PowerType.DEBUFF;
      this.updateDescription();
      this.loadRegion("flex");
   }

   @Override
   public void updateDescription() {
      this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
   }

   @Override
   public void atEndOfTurn(boolean isPlayer) {
      this.flash();
      this.addToBot(new ApplyPowerAction(this.owner, this.owner, new DexterityPower(this.owner, -this.amount), -this.amount));
      this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "DexLoss"));
   }
}
