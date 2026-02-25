package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.unique.RetainCardsAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class RetainCardPower extends AbstractPower {
   public static final String POWER_ID = "Retain Cards";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Retain Cards");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public RetainCardPower(AbstractCreature owner, int numCards) {
      this.name = NAME;
      this.ID = "Retain Cards";
      this.owner = owner;
      this.amount = numCards;
      this.updateDescription();
      this.loadRegion("retain");
   }

   @Override
   public void updateDescription() {
      if (this.amount == 1) {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
      } else {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
      }
   }

   @Override
   public void atEndOfTurn(boolean isPlayer) {
      if (isPlayer
         && !AbstractDungeon.player.hand.isEmpty()
         && !AbstractDungeon.player.hasRelic("Runic Pyramid")
         && !AbstractDungeon.player.hasPower("Equilibrium")) {
         this.addToBot(new RetainCardsAction(this.owner, this.amount));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
