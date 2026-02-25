package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class CreativeAIPower extends AbstractPower {
   public static final String POWER_ID = "Creative AI";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Creative AI");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public CreativeAIPower(AbstractCreature owner, int amt) {
      this.name = NAME;
      this.ID = "Creative AI";
      this.owner = owner;
      this.amount = amt;
      this.updateDescription();
      this.loadRegion("ai");
   }

   @Override
   public void atStartOfTurn() {
      for (int i = 0; i < this.amount; i++) {
         AbstractCard card = AbstractDungeon.returnTrulyRandomCardInCombat(AbstractCard.CardType.POWER).makeCopy();
         this.addToBot(new MakeTempCardInHandAction(card));
      }
   }

   @Override
   public void updateDescription() {
      if (this.amount > 1) {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
      } else {
         this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
