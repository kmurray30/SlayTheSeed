package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class EquilibriumPower extends AbstractPower {
   public static final String POWER_ID = "Equilibrium";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Equilibrium");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public EquilibriumPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Equilibrium";
      this.owner = owner;
      this.amount = amount;
      this.description = DESCRIPTIONS[0];
      this.loadRegion("retain");
      this.isTurnBased = true;
   }

   @Override
   public void updateDescription() {
      if (this.amount == 1) {
         this.description = DESCRIPTIONS[0];
      } else {
         this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
      }
   }

   @Override
   public void atEndOfTurn(boolean isPlayer) {
      if (isPlayer) {
         for (AbstractCard c : AbstractDungeon.player.hand.group) {
            if (!c.isEthereal) {
               c.retain = true;
            }
         }
      }
   }

   @Override
   public void atEndOfRound() {
      if (this.amount == 0) {
         this.addToBot(new RemoveSpecificPowerAction(this.owner, this.owner, "Equilibrium"));
      } else {
         this.addToBot(new ReducePowerAction(this.owner, this.owner, "Equilibrium", 1));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
