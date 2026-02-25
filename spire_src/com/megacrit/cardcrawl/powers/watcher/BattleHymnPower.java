package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.cards.tempCards.Smite;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class BattleHymnPower extends AbstractPower {
   public static final String POWER_ID = "BattleHymn";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("BattleHymn");

   public BattleHymnPower(AbstractCreature owner, int amt) {
      this.name = powerStrings.NAME;
      this.ID = "BattleHymn";
      this.owner = owner;
      this.amount = amt;
      this.updateDescription();
      this.loadRegion("hymn");
   }

   @Override
   public void atStartOfTurn() {
      if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
         this.flash();
         this.addToBot(new MakeTempCardInHandAction(new Smite(), this.amount, false));
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
