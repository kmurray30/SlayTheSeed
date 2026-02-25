package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class DrawPower extends AbstractPower {
   public static final String POWER_ID = "Draw";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Draw");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public DrawPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Draw";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("draw");
      if (amount < 0) {
         this.type = AbstractPower.PowerType.DEBUFF;
         this.loadRegion("draw2");
      } else {
         this.type = AbstractPower.PowerType.BUFF;
         this.loadRegion("draw");
      }

      this.isTurnBased = false;
      AbstractDungeon.player.gameHandSize += amount;
   }

   @Override
   public void onRemove() {
      AbstractDungeon.player.gameHandSize = AbstractDungeon.player.gameHandSize - this.amount;
   }

   @Override
   public void reducePower(int reduceAmount) {
      this.fontScale = 8.0F;
      this.amount -= reduceAmount;
      if (this.amount == 0) {
         this.addToTop(new RemoveSpecificPowerAction(this.owner, this.owner, "Draw"));
      }
   }

   @Override
   public void updateDescription() {
      if (this.amount > 0) {
         if (this.amount == 1) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
         } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[3];
         }

         this.type = AbstractPower.PowerType.BUFF;
      } else {
         if (this.amount == -1) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
         } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[4];
         }

         this.type = AbstractPower.PowerType.DEBUFF;
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
