package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class HeatsinkPower extends AbstractPower {
   public static final String POWER_ID = "Heatsink";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Heatsink");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public HeatsinkPower(AbstractCreature owner, int drawAmt) {
      this.name = NAME;
      this.ID = "Heatsink";
      this.owner = owner;
      this.amount = drawAmt;
      this.updateDescription();
      this.loadRegion("heatsink");
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      if (card.type == AbstractCard.CardType.POWER) {
         this.flash();
         this.addToTop(new DrawCardAction(this.owner, this.amount));
      }
   }

   @Override
   public void stackPower(int stackAmount) {
      this.fontScale = 8.0F;
      this.amount += stackAmount;
   }

   @Override
   public void updateDescription() {
      if (this.amount <= 1) {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
      } else {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
