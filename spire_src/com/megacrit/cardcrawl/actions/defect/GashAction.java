package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.blue.Claw;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class GashAction extends AbstractGameAction {
   private AbstractCard card;

   public GashAction(AbstractCard card, int amount) {
      this.card = card;
      this.amount = amount;
   }

   @Override
   public void update() {
      this.card.baseDamage = this.card.baseDamage + this.amount;
      this.card.applyPowers();

      for (AbstractCard c : AbstractDungeon.player.discardPile.group) {
         if (c instanceof Claw) {
            c.baseDamage = c.baseDamage + this.amount;
            c.applyPowers();
         }
      }

      for (AbstractCard cx : AbstractDungeon.player.drawPile.group) {
         if (cx instanceof Claw) {
            cx.baseDamage = cx.baseDamage + this.amount;
            cx.applyPowers();
         }
      }

      for (AbstractCard cxx : AbstractDungeon.player.hand.group) {
         if (cxx instanceof Claw) {
            cxx.baseDamage = cxx.baseDamage + this.amount;
            cxx.applyPowers();
         }
      }

      this.isDone = true;
   }
}
