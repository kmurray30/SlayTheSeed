package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class AttackBurnPower extends AbstractPower {
   public static final String POWER_ID = "Attack Burn";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Attack Burn");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   private boolean justApplied = true;

   public AttackBurnPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Attack Burn";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("attackBurn");
      this.type = AbstractPower.PowerType.DEBUFF;
      this.isTurnBased = true;
   }

   @Override
   public void atEndOfRound() {
      if (this.justApplied) {
         this.justApplied = false;
      } else {
         this.addToBot(new ReducePowerAction(this.owner, this.owner, "Attack Burn", 1));
      }
   }

   @Override
   public void updateDescription() {
      if (this.amount == 1) {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
      } else {
         this.description = DESCRIPTIONS[2] + this.amount + DESCRIPTIONS[3];
      }
   }

   @Override
   public void onUseCard(AbstractCard card, UseCardAction action) {
      if (card.type == AbstractCard.CardType.ATTACK) {
         action.exhaustCard = true;
         this.flash();
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
