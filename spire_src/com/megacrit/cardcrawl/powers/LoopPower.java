package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class LoopPower extends AbstractPower {
   public static final String POWER_ID = "Loop";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Loop");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public LoopPower(AbstractCreature owner, int amt) {
      this.name = NAME;
      this.ID = "Loop";
      this.owner = owner;
      this.amount = amt;
      this.updateDescription();
      this.loadRegion("loop");
   }

   @Override
   public void atStartOfTurn() {
      if (!AbstractDungeon.player.orbs.isEmpty()) {
         this.flash();

         for (int i = 0; i < this.amount; i++) {
            AbstractDungeon.player.orbs.get(0).onStartOfTurn();
            AbstractDungeon.player.orbs.get(0).onEndOfTurn();
         }
      }
   }

   @Override
   public void updateDescription() {
      if (this.amount <= 1) {
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
