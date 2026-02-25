package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class PenNibPower extends AbstractPower {
   public static final String POWER_ID = "Pen Nib";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Pen Nib");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public PenNibPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Pen Nib";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("penNib");
      this.type = AbstractPower.PowerType.BUFF;
      this.isTurnBased = true;
      this.priority = 6;
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      if (card.type == AbstractCard.CardType.ATTACK) {
         this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Pen Nib"));
      }
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0];
   }

   @Override
   public float atDamageGive(float damage, DamageInfo.DamageType type) {
      return type == DamageInfo.DamageType.NORMAL ? damage * 2.0F : damage;
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
