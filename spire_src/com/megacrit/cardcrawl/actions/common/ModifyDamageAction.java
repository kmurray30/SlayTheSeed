package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.helpers.GetAllInBattleInstances;
import java.util.UUID;

public class ModifyDamageAction extends AbstractGameAction {
   private UUID uuid;

   public ModifyDamageAction(UUID targetUUID, int amount) {
      this.setValues(this.target, this.source, amount);
      this.actionType = AbstractGameAction.ActionType.CARD_MANIPULATION;
      this.uuid = targetUUID;
   }

   @Override
   public void update() {
      for (AbstractCard c : GetAllInBattleInstances.get(this.uuid)) {
         c.baseDamage = c.baseDamage + this.amount;
         if (c.baseDamage < 0) {
            c.baseDamage = 0;
         }
      }

      this.isDone = true;
   }
}
