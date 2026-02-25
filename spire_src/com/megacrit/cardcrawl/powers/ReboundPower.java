package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ReboundPower extends AbstractPower {
   public static final String POWER_ID = "Rebound";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Rebound");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   private boolean justEvoked = true;

   public ReboundPower(AbstractCreature owner) {
      this.name = NAME;
      this.ID = "Rebound";
      this.owner = owner;
      this.amount = 1;
      this.updateDescription();
      this.loadRegion("rebound");
      this.isTurnBased = true;
      this.type = AbstractPower.PowerType.BUFF;
   }

   @Override
   public void updateDescription() {
      if (this.amount > 1) {
         this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
      } else {
         this.description = DESCRIPTIONS[0];
      }
   }

   @Override
   public void onAfterUseCard(AbstractCard card, UseCardAction action) {
      if (this.justEvoked) {
         this.justEvoked = false;
      } else {
         if (card.type != AbstractCard.CardType.POWER) {
            this.flash();
            action.reboundCard = true;
         }

         this.addToBot(new ReducePowerAction(this.owner, this.owner, "Rebound", 1));
      }
   }

   @Override
   public void atEndOfTurn(boolean isPlayer) {
      if (isPlayer) {
         this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Rebound"));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
