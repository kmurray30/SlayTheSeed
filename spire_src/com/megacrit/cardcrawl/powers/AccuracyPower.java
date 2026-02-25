package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Shiv;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class AccuracyPower extends AbstractPower {
   public static final String POWER_ID = "Accuracy";
   private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Accuracy");
   public static final String[] DESCRIPTIONS;

   public AccuracyPower(AbstractCreature owner, int amt) {
      this.name = powerStrings.NAME;
      this.ID = "Accuracy";
      this.owner = owner;
      this.amount = amt;
      this.updateDescription();
      this.loadRegion("accuracy");
      this.updateExistingShivs();
   }

   @Override
   public void updateDescription() {
      this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
   }

   @Override
   public void stackPower(int stackAmount) {
      this.fontScale = 8.0F;
      this.amount += stackAmount;
      this.updateExistingShivs();
   }

   private void updateExistingShivs() {
      for (AbstractCard c : AbstractDungeon.player.hand.group) {
         if (c instanceof Shiv) {
            if (!c.upgraded) {
               c.baseDamage = 4 + this.amount;
            } else {
               c.baseDamage = 6 + this.amount;
            }
         }
      }

      for (AbstractCard cx : AbstractDungeon.player.drawPile.group) {
         if (cx instanceof Shiv) {
            if (!cx.upgraded) {
               cx.baseDamage = 4 + this.amount;
            } else {
               cx.baseDamage = 6 + this.amount;
            }
         }
      }

      for (AbstractCard cxx : AbstractDungeon.player.discardPile.group) {
         if (cxx instanceof Shiv) {
            if (!cxx.upgraded) {
               cxx.baseDamage = 4 + this.amount;
            } else {
               cxx.baseDamage = 6 + this.amount;
            }
         }
      }

      for (AbstractCard cxxx : AbstractDungeon.player.exhaustPile.group) {
         if (cxxx instanceof Shiv) {
            if (!cxxx.upgraded) {
               cxxx.baseDamage = 4 + this.amount;
            } else {
               cxxx.baseDamage = 6 + this.amount;
            }
         }
      }
   }

   @Override
   public void onDrawOrDiscard() {
      for (AbstractCard c : AbstractDungeon.player.hand.group) {
         if (c instanceof Shiv) {
            if (!c.upgraded) {
               c.baseDamage = 4 + this.amount;
            } else {
               c.baseDamage = 6 + this.amount;
            }
         }
      }
   }

   static {
      DESCRIPTIONS = powerStrings.DESCRIPTIONS;
   }
}
