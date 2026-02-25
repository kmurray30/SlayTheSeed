package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class DarkEmbracePower extends AbstractPower {
   public static final String POWER_ID = "Dark Embrace";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Dark Embrace");
   public static final String NAME;
   public static final String[] DESCRIPTIONS;

   public DarkEmbracePower(AbstractCreature owner, int amount) {
      this.name = NAME;
      this.ID = "Dark Embrace";
      this.owner = owner;
      this.amount = amount;
      this.updateDescription();
      this.loadRegion("darkembrace");
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
   public void onExhaust(AbstractCard card) {
      if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
         this.flash();
         this.addToBot(new DrawCardAction(this.owner, this.amount));
      }
   }

   static {
      NAME = powerStrings.NAME;
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
