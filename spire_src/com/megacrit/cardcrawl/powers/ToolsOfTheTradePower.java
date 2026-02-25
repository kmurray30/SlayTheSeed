package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.DiscardAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ToolsOfTheTradePower extends AbstractPower {
   public static final String POWER_ID = "Tools Of The Trade";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Tools Of The Trade");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public ToolsOfTheTradePower(AbstractCreature owner, int drawAmount) {
      this.name = NAME;
      this.ID = "Tools Of The Trade";
      this.owner = owner;
      this.amount = drawAmount;
      this.updateDescription();
      this.loadRegion("tools");
      this.priority = 25;
   }

   @Override
   public void updateDescription() {
      if (this.amount == 1) {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
      } else {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[3] + this.amount + DESCRIPTIONS[4];
      }
   }

   @Override
   public void atStartOfTurnPostDraw() {
      this.flash();
      this.addToBot(new DrawCardAction(this.owner, this.amount));
      this.addToBot(new DiscardAction(this.owner, this.owner, this.amount, false));
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
