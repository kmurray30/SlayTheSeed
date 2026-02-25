package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;

public class UpdateCardDescriptionAction extends AbstractGameAction {
   private AbstractCard targetCard;

   public UpdateCardDescriptionAction(AbstractCard targetCard) {
      this.targetCard = targetCard;
      this.actionType = AbstractGameAction.ActionType.TEXT;
      this.duration = 0.5F;
   }

   @Override
   public void update() {
      if (this.duration == 0.5F) {
         this.targetCard.initializeDescription();
      }

      this.tickDuration();
   }
}
