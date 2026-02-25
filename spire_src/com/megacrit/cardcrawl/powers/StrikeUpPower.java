package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class StrikeUpPower extends AbstractPower {
   public static final String POWER_ID = "StrikeUp";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("StrikeUp");

   public StrikeUpPower(AbstractCreature owner, int amt) {
      this.name = powerStrings.NAME;
      this.ID = "StrikeUp";
      this.owner = owner;
      this.amount = amt;
      this.updateDescription();
      this.loadRegion("accuracy");
      this.updateExistingStrikes();
   }

   @Override
   public void updateDescription() {
      this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
   }

   @Override
   public void stackPower(int stackAmount) {
      this.fontScale = 8.0F;
      this.amount += stackAmount;
      this.updateExistingStrikes();
   }

   private void updateExistingStrikes() {
      for (AbstractCard c : AbstractDungeon.player.hand.group) {
         if (c.hasTag(AbstractCard.CardTags.STRIKE)) {
            c.baseDamage = CardLibrary.getCard(c.cardID).baseDamage + this.amount;
         }
      }

      for (AbstractCard cx : AbstractDungeon.player.drawPile.group) {
         if (cx.hasTag(AbstractCard.CardTags.STRIKE)) {
            cx.baseDamage = CardLibrary.getCard(cx.cardID).baseDamage + this.amount;
         }
      }

      for (AbstractCard cxx : AbstractDungeon.player.discardPile.group) {
         if (cxx.hasTag(AbstractCard.CardTags.STRIKE)) {
            cxx.baseDamage = CardLibrary.getCard(cxx.cardID).baseDamage + this.amount;
         }
      }

      for (AbstractCard cxxx : AbstractDungeon.player.exhaustPile.group) {
         if (cxxx.hasTag(AbstractCard.CardTags.STRIKE)) {
            cxxx.baseDamage = CardLibrary.getCard(cxxx.cardID).baseDamage + this.amount;
         }
      }
   }

   @Override
   public void onDrawOrDiscard() {
      for (AbstractCard c : AbstractDungeon.player.hand.group) {
         if (c.hasTag(AbstractCard.CardTags.STRIKE)) {
            c.baseDamage = CardLibrary.getCard(c.cardID).baseDamage + this.amount;
         }
      }
   }
}
