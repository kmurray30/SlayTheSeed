package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.status.Burn;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndAddToDiscardEffect;

public class BurnIncreaseAction extends AbstractGameAction {
   private static final float DURATION = 3.0F;
   private boolean gotBurned = false;

   public BurnIncreaseAction() {
      this.duration = 3.0F;
      this.actionType = AbstractGameAction.ActionType.WAIT;
   }

   @Override
   public void update() {
      if (this.duration == 3.0F) {
         for (AbstractCard c : AbstractDungeon.player.discardPile.group) {
            if (c instanceof Burn) {
               c.upgrade();
            }
         }

         for (AbstractCard cx : AbstractDungeon.player.drawPile.group) {
            if (cx instanceof Burn) {
               cx.upgrade();
            }
         }
      }

      if (this.duration < 1.5F && !this.gotBurned) {
         this.gotBurned = true;
         Burn b = new Burn();
         b.upgrade();
         AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect(b));
         Burn cxx = new Burn();
         cxx.upgrade();
         AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect(cxx));
         Burn d = new Burn();
         d.upgrade();
         AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect(d));
      }

      this.tickDuration();
   }
}
