package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class EvolvePower extends AbstractPower {
   public static final String POWER_ID = "Evolve";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Evolve");

   public EvolvePower(AbstractCreature owner, int drawAmt) {
      this.name = powerStrings.NAME;
      this.ID = "Evolve";
      this.owner = owner;
      this.amount = drawAmt;
      this.updateDescription();
      this.loadRegion("evolve");
   }

   @Override
   public void updateDescription() {
      if (this.amount == 1) {
         this.description = powerStrings.DESCRIPTIONS[0];
      } else {
         this.description = powerStrings.DESCRIPTIONS[1] + this.amount + powerStrings.DESCRIPTIONS[2];
      }
   }

   @Override
   public void onCardDraw(AbstractCard card) {
      if (card.type == AbstractCard.CardType.STATUS && !this.owner.hasPower("No Draw")) {
         this.flash();
         this.addToBot(new DrawCardAction(this.owner, this.amount));
      }
   }
}
