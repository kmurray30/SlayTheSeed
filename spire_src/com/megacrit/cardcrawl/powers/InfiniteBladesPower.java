package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.cards.tempCards.Shiv;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class InfiniteBladesPower extends AbstractPower {
   public static final String POWER_ID = "Infinite Blades";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Infinite Blades");

   public InfiniteBladesPower(AbstractCreature owner, int bladeAmt) {
      this.name = powerStrings.NAME;
      this.ID = "Infinite Blades";
      this.owner = owner;
      this.amount = bladeAmt;
      this.updateDescription();
      this.loadRegion("infiniteBlades");
   }

   @Override
   public void atStartOfTurn() {
      if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
         this.flash();
         this.addToBot(new MakeTempCardInHandAction(new Shiv(), this.amount, false));
      }
   }

   @Override
   public void stackPower(int stackAmount) {
      this.fontScale = 8.0F;
      this.amount += stackAmount;
   }

   @Override
   public void updateDescription() {
      if (this.amount > 1) {
         this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
      } else {
         this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[2];
      }
   }
}
