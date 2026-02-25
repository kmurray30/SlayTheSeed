package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class DrawReductionPower extends AbstractPower {
   public static final String POWER_ID = "Draw Reduction";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Draw Reduction");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;
   private boolean justApplied = true;

   public DrawReductionPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Draw Reduction";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("lessdraw");
      this.type = AbstractPower.PowerType.DEBUFF;
      this.isTurnBased = true;
   }

   @Override
   public void onInitialApplication() {
      AbstractDungeon.player.gameHandSize--;
   }

   @Override
   public void atEndOfRound() {
      if (this.justApplied) {
         this.justApplied = false;
      } else {
         this.addToBot(new ReducePowerAction(this.owner, this.owner, "Draw Reduction", 1));
      }
   }

   @Override
   public void onRemove() {
      AbstractDungeon.player.gameHandSize++;
   }

   @Override
   public void updateDescription() {
      if (this.amount == 1) {
         this.description = DESCRIPTIONS[0];
      } else {
         this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
