package com.megacrit.cardcrawl.actions.common;

import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndAddToDiscardEffect;

public class MakeTempCardInDiscardAction extends AbstractGameAction {
   private AbstractCard c;
   private int numCards;
   private boolean sameUUID;

   public MakeTempCardInDiscardAction(AbstractCard card, int amount) {
      UnlockTracker.markCardAsSeen(card.cardID);
      this.numCards = amount;
      this.actionType = AbstractGameAction.ActionType.CARD_MANIPULATION;
      this.startDuration = Settings.FAST_MODE ? Settings.ACTION_DUR_FAST : 0.5F;
      this.duration = this.startDuration;
      this.c = card;
      this.sameUUID = false;
   }

   public MakeTempCardInDiscardAction(AbstractCard card, boolean sameUUID) {
      this(card, 1);
      this.sameUUID = sameUUID;
      if (!sameUUID
         && this.c.type != AbstractCard.CardType.CURSE
         && this.c.type != AbstractCard.CardType.STATUS
         && AbstractDungeon.player.hasPower("MasterRealityPower")) {
         this.c.upgrade();
      }
   }

   @Override
   public void update() {
      if (this.duration == this.startDuration) {
         if (this.numCards < 6) {
            for (int i = 0; i < this.numCards; i++) {
               AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect(this.makeNewCard()));
            }
         }

         this.duration = this.duration - Gdx.graphics.getDeltaTime();
      }

      this.tickDuration();
   }

   private AbstractCard makeNewCard() {
      return this.sameUUID ? this.c.makeSameInstanceOf() : this.c.makeStatEquivalentCopy();
   }
}
