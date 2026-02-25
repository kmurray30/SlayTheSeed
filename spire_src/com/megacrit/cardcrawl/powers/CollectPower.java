package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Miracle;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class CollectPower extends AbstractPower {
   public static final String POWER_ID = "Collect";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Collect");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public CollectPower(AbstractCreature owner, int numTurns) {
      this.name = NAME;
      this.ID = "Collect";
      this.owner = owner;
      this.amount = numTurns;
      this.isTurnBased = true;
      if (this.amount >= 999) {
         this.amount = 999;
      }

      this.updateDescription();
      this.loadRegion("energized_blue");
   }

   @Override
   public void stackPower(int stackAmount) {
      super.stackPower(stackAmount);
      if (this.amount >= 999) {
         this.amount = 999;
      }
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
   public void onEnergyRecharge() {
      this.flash();
      AbstractCard card = new Miracle();
      card.upgrade();
      this.addToBot(new MakeTempCardInHandAction(card));
      if (this.amount <= 1) {
         this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Collect"));
      } else {
         this.addToBot(new ReducePowerAction(this.owner, this.owner, "Collect", 1));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
