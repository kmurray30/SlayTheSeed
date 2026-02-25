package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class EscapePlanAction extends AbstractGameAction {
   private int blockGain;

   public EscapePlanAction(int blockGain) {
      this.duration = 0.0F;
      this.actionType = AbstractGameAction.ActionType.WAIT;
      this.blockGain = blockGain;
   }

   @Override
   public void update() {
      for (AbstractCard c : DrawCardAction.drawnCards) {
         if (c.type == AbstractCard.CardType.SKILL) {
            AbstractDungeon.actionManager.addToTop(new GainBlockAction(AbstractDungeon.player, AbstractDungeon.player, this.blockGain));
            break;
         }
      }

      this.isDone = true;
   }
}
