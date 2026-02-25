package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class RitualPower extends AbstractPower {
   public static final String POWER_ID = "Ritual";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Ritual");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   private boolean skipFirst = true;
   private boolean onPlayer;

   public RitualPower(AbstractCreature owner, int strAmt, boolean playerControlled) {
      this.name = NAME;
      this.ID = "Ritual";
      this.owner = owner;
      this.amount = strAmt;
      this.onPlayer = playerControlled;
      this.updateDescription();
      this.loadRegion("ritual");
   }

   @Override
   public void updateDescription() {
      if (!this.onPlayer) {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
      } else {
         this.description = DESCRIPTIONS[2] + this.amount + DESCRIPTIONS[1];
      }
   }

   @Override
   public void atEndOfTurn(boolean isPlayer) {
      if (isPlayer) {
         this.flash();
         this.addToBot(new ApplyPowerAction(this.owner, this.owner, new StrengthPower(this.owner, this.amount), this.amount));
      }
   }

   @Override
   public void atEndOfRound() {
      if (!this.onPlayer) {
         if (!this.skipFirst) {
            this.flash();
            this.addToBot(new ApplyPowerAction(this.owner, this.owner, new StrengthPower(this.owner, this.amount), this.amount));
         } else {
            this.skipFirst = false;
         }
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
