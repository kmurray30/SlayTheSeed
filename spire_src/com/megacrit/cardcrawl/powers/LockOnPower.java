package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class LockOnPower extends AbstractPower {
   public static final String POWER_ID = "Lockon";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Lockon");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   public static final float MULTIPLIER = 1.5F;
   private static final int MULTI_STR = 50;

   public LockOnPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Lockon";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("lockon");
      this.type = AbstractPower.PowerType.DEBUFF;
      this.isTurnBased = true;
   }

   @Override
   public void atEndOfRound() {
      if (this.amount == 0) {
         this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Lockon"));
      } else {
         this.addToBot(new ReducePowerAction(this.owner, this.owner, "Lockon", 1));
      }
   }

   @Override
   public void updateDescription() {
      if (this.owner != null) {
         if (this.amount == 1) {
            this.description = DESCRIPTIONS[0] + 50 + DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
         } else {
            this.description = DESCRIPTIONS[0] + 50 + DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[3];
         }
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
