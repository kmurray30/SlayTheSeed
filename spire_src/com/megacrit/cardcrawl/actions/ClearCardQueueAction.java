package com.megacrit.cardcrawl.actions;

import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.cardManip.ExhaustCardEffect;

public class ClearCardQueueAction extends AbstractGameAction {
   @Override
   public void update() {
      for (CardQueueItem c : AbstractDungeon.actionManager.cardQueue) {
         if (AbstractDungeon.player.limbo.contains(c.card)) {
            AbstractDungeon.effectList.add(new ExhaustCardEffect(c.card));
            AbstractDungeon.player.limbo.group.remove(c.card);
         }
      }

      AbstractDungeon.actionManager.cardQueue.clear();
      this.isDone = true;
   }
}
