package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.GetAllInBattleInstances;
import java.util.UUID;

public class IncreaseMiscAction extends AbstractGameAction {
   private int miscIncrease;
   private UUID uuid;

   public IncreaseMiscAction(UUID targetUUID, int miscValue, int miscIncrease) {
      this.miscIncrease = miscIncrease;
      this.uuid = targetUUID;
   }

   @Override
   public void update() {
      for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
         if (c.uuid.equals(this.uuid)) {
            c.misc = c.misc + this.miscIncrease;
            c.applyPowers();
            c.baseBlock = c.misc;
            c.isBlockModified = false;
         }
      }

      for (AbstractCard cx : GetAllInBattleInstances.get(this.uuid)) {
         cx.misc = cx.misc + this.miscIncrease;
         cx.applyPowers();
         cx.baseBlock = cx.misc;
      }

      this.isDone = true;
   }
}
