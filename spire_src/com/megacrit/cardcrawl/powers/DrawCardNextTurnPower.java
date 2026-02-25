package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class DrawCardNextTurnPower extends AbstractPower {
   public static final String POWER_ID = "Draw Card";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Draw Card");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public DrawCardNextTurnPower(AbstractCreature owner, int drawAmount) {
      this.name = NAME;
      this.ID = "Draw Card";
      this.owner = owner;
      this.amount = drawAmount;
      this.updateDescription();
      this.loadRegion("carddraw");
      this.priority = 20;
   }

   @Override
   public void updateDescription() {
      if (this.amount > 1) {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
      } else {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
      }
   }

   @Override
   public void atStartOfTurnPostDraw() {
      this.flash();
      this.addToBot(new DrawCardAction(this.owner, this.amount));
      this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Draw Card"));
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
