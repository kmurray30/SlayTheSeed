package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class BiasPower extends AbstractPower {
   public static final String POWER_ID = "Bias";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Bias");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public BiasPower(AbstractCreature owner, int setAmount) {
      this.name = NAME;
      this.ID = "Bias";
      this.owner = owner;
      this.amount = setAmount;
      this.updateDescription();
      this.loadRegion("bias");
      this.type = AbstractPower.PowerType.DEBUFF;
   }

   @Override
   public void atStartOfTurn() {
      this.flash();
      this.addToBot(new ApplyPowerAction(this.owner, this.owner, new FocusPower(this.owner, -this.amount), -this.amount));
   }

   @Override
   public void stackPower(int stackAmount) {
      this.fontScale = 8.0F;
      this.amount += stackAmount;
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
