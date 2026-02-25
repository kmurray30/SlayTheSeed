package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class RandomizeHandCostAction extends AbstractGameAction {
   private AbstractPlayer p;

   public RandomizeHandCostAction() {
      this.actionType = AbstractGameAction.ActionType.CARD_MANIPULATION;
      this.p = AbstractDungeon.player;
      this.duration = Settings.ACTION_DUR_FAST;
   }

   @Override
   public void update() {
      if (this.duration == Settings.ACTION_DUR_FAST) {
         for (AbstractCard card : this.p.hand.group) {
            if (card.cost >= 0) {
               int newCost = AbstractDungeon.cardRandomRng.random(3);
               if (card.cost != newCost) {
                  card.cost = newCost;
                  card.costForTurn = card.cost;
                  card.isCostModified = true;
               }
            }
         }

         this.isDone = true;
      } else {
         this.tickDuration();
      }
   }
}
