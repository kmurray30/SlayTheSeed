package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.PlayTopCardAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class MayhemPower extends AbstractPower {
   public static final String POWER_ID = "Mayhem";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Mayhem");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public MayhemPower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Mayhem";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("mayhem");
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
   public void atStartOfTurn() {
      this.flash();

      for (int i = 0; i < this.amount; i++) {
         this.addToBot(new AbstractGameAction() {
            @Override
            public void update() {
               this.addToBot(new PlayTopCardAction(AbstractDungeon.getCurrRoom().monsters.getRandomMonster(null, true, AbstractDungeon.cardRandomRng), false));
               this.isDone = true;
            }
         });
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
