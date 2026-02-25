package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class PhantasmalPower extends AbstractPower {
   public static final String POWER_ID = "Phantasmal";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Phantasmal");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public PhantasmalPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Phantasmal";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("phantasmal");
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
   public void atStartOfTurn() {
      this.flash();
      this.addToBot(new ApplyPowerAction(this.owner, this.owner, new DoubleDamagePower(this.owner, 1, false), this.amount));
      this.addToBot(new ReducePowerAction(this.owner, this.owner, "Phantasmal", 1));
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
