package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ConservePower extends AbstractPower {
   public static final String POWER_ID = "Conserve";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Conserve");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public ConservePower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Conserve";
      this.owner = owner;
      this.amount = amount;
      this.description = DESCRIPTIONS[0];
      this.loadRegion("conserve");
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
         this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Conserve"));
      } else {
         this.addToBot(new ReducePowerAction(this.owner, this.owner, "Conserve", 1));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
