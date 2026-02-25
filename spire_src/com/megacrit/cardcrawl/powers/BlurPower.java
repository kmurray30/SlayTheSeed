package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class BlurPower extends AbstractPower {
   public static final String POWER_ID = "Blur";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Blur");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public BlurPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Blur";
      this.owner = owner;
      this.amount = amount;
      this.description = DESCRIPTIONS[0];
      this.loadRegion("blur");
      this.isTurnBased = true;
   }

   @Override
   public void updateDescription() {
      if (this.amount == 1) {
         this.description = DESCRIPTIONS[0];
      } else {
         this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
      }
   }

   @Override
   public void atEndOfRound() {
      if (this.amount == 0) {
         this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Blur"));
      } else {
         this.addToBot(new ReducePowerAction(this.owner, this.owner, "Blur", 1));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
