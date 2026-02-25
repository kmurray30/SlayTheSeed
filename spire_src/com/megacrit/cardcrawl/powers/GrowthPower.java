package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class GrowthPower extends AbstractPower {
   public static final String POWER_ID = "GrowthPower";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("GrowthPower");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   private boolean skipFirst = true;

   public GrowthPower(AbstractCreature owner, int strAmt) {
      this.name = NAME;
      this.ID = "GrowthPower";
      this.owner = owner;
      this.amount = strAmt;
      this.updateDescription();
      this.loadRegion("ritual");
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
   }

   @Override
   public void atEndOfRound() {
      if (!this.skipFirst) {
         this.flash();
         this.addToBot(new ApplyPowerAction(this.owner, this.owner, new StrengthPower(this.owner, this.amount), this.amount));
      } else {
         this.skipFirst = false;
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
