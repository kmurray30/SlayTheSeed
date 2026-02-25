package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class CuriosityPower extends AbstractPower {
   public static final String POWER_ID = "Curiosity";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Curiosity");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public CuriosityPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Curiosity";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("curiosity");
      this.type = AbstractPower.PowerType.BUFF;
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      if (card.type == AbstractCard.CardType.POWER) {
         this.flash();
         this.addToBot(new ApplyPowerAction(this.owner, this.owner, new StrengthPower(this.owner, this.amount), this.amount));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
