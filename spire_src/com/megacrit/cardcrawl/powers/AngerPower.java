package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class AngerPower extends AbstractPower {
   public static final String POWER_ID = "Anger";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Anger");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public AngerPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Anger";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("anger");
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      if (card.type == AbstractCard.CardType.SKILL) {
         this.addToTop(new ApplyPowerAction(this.owner, this.owner, new StrengthPower(this.owner, this.amount), this.amount));
         this.flash();
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
