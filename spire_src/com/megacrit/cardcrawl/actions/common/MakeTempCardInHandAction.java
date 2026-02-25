package com.megacrit.cardcrawl.actions.common;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndAddToDiscardEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndAddToHandEffect;

public class MakeTempCardInHandAction extends AbstractGameAction {
   private AbstractCard c;
   private static final float PADDING = 25.0F * Settings.scale;
   private boolean isOtherCardInCenter = true;
   private boolean sameUUID = false;

   public MakeTempCardInHandAction(AbstractCard card, boolean isOtherCardInCenter) {
      UnlockTracker.markCardAsSeen(card.cardID);
      this.amount = 1;
      this.actionType = AbstractGameAction.ActionType.CARD_MANIPULATION;
      this.c = card;
      if (this.c.type != AbstractCard.CardType.CURSE && this.c.type != AbstractCard.CardType.STATUS && AbstractDungeon.player.hasPower("MasterRealityPower")) {
         this.c.upgrade();
      }

      this.isOtherCardInCenter = isOtherCardInCenter;
   }

   public MakeTempCardInHandAction(AbstractCard card) {
      this(card, 1);
   }

   public MakeTempCardInHandAction(AbstractCard card, int amount) {
      UnlockTracker.markCardAsSeen(card.cardID);
      this.amount = amount;
      this.actionType = AbstractGameAction.ActionType.CARD_MANIPULATION;
      this.c = card;
      if (this.c.type != AbstractCard.CardType.CURSE && this.c.type != AbstractCard.CardType.STATUS && AbstractDungeon.player.hasPower("MasterRealityPower")) {
         this.c.upgrade();
      }
   }

   public MakeTempCardInHandAction(AbstractCard card, int amount, boolean isOtherCardInCenter) {
      this(card, amount);
      this.isOtherCardInCenter = isOtherCardInCenter;
   }

   public MakeTempCardInHandAction(AbstractCard card, boolean isOtherCardInCenter, boolean sameUUID) {
      this(card, 1);
      this.isOtherCardInCenter = isOtherCardInCenter;
      this.sameUUID = sameUUID;
   }

   @Override
   public void update() {
      if (this.amount == 0) {
         this.isDone = true;
      } else {
         int discardAmount = 0;
         int handAmount = this.amount;
         if (this.amount + AbstractDungeon.player.hand.size() > 10) {
            AbstractDungeon.player.createHandIsFullDialog();
            discardAmount = this.amount + AbstractDungeon.player.hand.size() - 10;
            handAmount -= discardAmount;
         }

         this.addToHand(handAmount);
         this.addToDiscard(discardAmount);
         if (this.amount > 0) {
            this.addToTop(new WaitAction(0.8F));
         }

         this.isDone = true;
      }
   }

   private void addToHand(int handAmt) {
      switch (this.amount) {
         case 0:
            break;
         case 1:
            if (handAmt == 1) {
               if (this.isOtherCardInCenter) {
                  AbstractDungeon.effectList
                     .add(
                        new ShowCardAndAddToHandEffect(this.makeNewCard(), Settings.WIDTH / 2.0F - (PADDING + AbstractCard.IMG_WIDTH), Settings.HEIGHT / 2.0F)
                     );
               } else {
                  AbstractDungeon.effectList.add(new ShowCardAndAddToHandEffect(this.makeNewCard()));
               }
            }
            break;
         case 2:
            if (handAmt == 1) {
               AbstractDungeon.effectList
                  .add(
                     new ShowCardAndAddToHandEffect(
                        this.makeNewCard(), Settings.WIDTH / 2.0F - (PADDING + AbstractCard.IMG_WIDTH * 0.5F), Settings.HEIGHT / 2.0F
                     )
                  );
            } else if (handAmt == 2) {
               AbstractDungeon.effectList
                  .add(new ShowCardAndAddToHandEffect(this.makeNewCard(), Settings.WIDTH / 2.0F + (PADDING + AbstractCard.IMG_WIDTH), Settings.HEIGHT / 2.0F));
               AbstractDungeon.effectList
                  .add(new ShowCardAndAddToHandEffect(this.makeNewCard(), Settings.WIDTH / 2.0F - (PADDING + AbstractCard.IMG_WIDTH), Settings.HEIGHT / 2.0F));
            }
            break;
         case 3:
            if (handAmt == 1) {
               AbstractDungeon.effectList
                  .add(new ShowCardAndAddToHandEffect(this.makeNewCard(), Settings.WIDTH / 2.0F - (PADDING + AbstractCard.IMG_WIDTH), Settings.HEIGHT / 2.0F));
            } else if (handAmt == 2) {
               AbstractDungeon.effectList
                  .add(new ShowCardAndAddToHandEffect(this.makeNewCard(), Settings.WIDTH / 2.0F + (PADDING + AbstractCard.IMG_WIDTH), Settings.HEIGHT / 2.0F));
               AbstractDungeon.effectList
                  .add(new ShowCardAndAddToHandEffect(this.makeNewCard(), Settings.WIDTH / 2.0F - (PADDING + AbstractCard.IMG_WIDTH), Settings.HEIGHT / 2.0F));
            } else if (handAmt == 3) {
               for (int i = 0; i < this.amount; i++) {
                  AbstractDungeon.effectList.add(new ShowCardAndAddToHandEffect(this.makeNewCard()));
               }
            }
            break;
         default:
            for (int i = 0; i < handAmt; i++) {
               AbstractDungeon.effectList
                  .add(
                     new ShowCardAndAddToHandEffect(
                        this.makeNewCard(),
                        MathUtils.random(Settings.WIDTH * 0.2F, Settings.WIDTH * 0.8F),
                        MathUtils.random(Settings.HEIGHT * 0.3F, Settings.HEIGHT * 0.7F)
                     )
                  );
            }
      }
   }

   private void addToDiscard(int discardAmt) {
      switch (this.amount) {
         case 0:
            break;
         case 1:
            if (discardAmt == 1) {
               AbstractDungeon.effectList
                  .add(
                     new ShowCardAndAddToDiscardEffect(this.makeNewCard(), Settings.WIDTH / 2.0F + (PADDING + AbstractCard.IMG_WIDTH), Settings.HEIGHT / 2.0F)
                  );
            }
            break;
         case 2:
            if (discardAmt == 1) {
               AbstractDungeon.effectList
                  .add(
                     new ShowCardAndAddToDiscardEffect(
                        this.makeNewCard(), Settings.WIDTH * 0.5F - (PADDING + AbstractCard.IMG_WIDTH * 0.5F), Settings.HEIGHT * 0.5F
                     )
                  );
            } else if (discardAmt == 2) {
               AbstractDungeon.effectList
                  .add(
                     new ShowCardAndAddToDiscardEffect(
                        this.makeNewCard(), Settings.WIDTH * 0.5F - (PADDING + AbstractCard.IMG_WIDTH * 0.5F), Settings.HEIGHT * 0.5F
                     )
                  );
               AbstractDungeon.effectList
                  .add(
                     new ShowCardAndAddToDiscardEffect(
                        this.makeNewCard(), Settings.WIDTH * 0.5F + (PADDING + AbstractCard.IMG_WIDTH * 0.5F), Settings.HEIGHT * 0.5F
                     )
                  );
            }
            break;
         case 3:
            if (discardAmt == 1) {
               AbstractDungeon.effectList
                  .add(
                     new ShowCardAndAddToDiscardEffect(this.makeNewCard(), Settings.WIDTH * 0.5F + (PADDING + AbstractCard.IMG_WIDTH), Settings.HEIGHT * 0.5F)
                  );
            } else if (discardAmt == 2) {
               AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect(this.makeNewCard(), Settings.WIDTH * 0.5F, Settings.HEIGHT * 0.5F));
               AbstractDungeon.effectList
                  .add(
                     new ShowCardAndAddToDiscardEffect(this.makeNewCard(), Settings.WIDTH * 0.5F + (PADDING + AbstractCard.IMG_WIDTH), Settings.HEIGHT * 0.5F)
                  );
            } else if (discardAmt == 3) {
               AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect(this.makeNewCard(), Settings.WIDTH * 0.5F, Settings.HEIGHT * 0.5F));
               AbstractDungeon.effectList
                  .add(
                     new ShowCardAndAddToDiscardEffect(this.makeNewCard(), Settings.WIDTH * 0.5F - (PADDING + AbstractCard.IMG_WIDTH), Settings.HEIGHT * 0.5F)
                  );
               AbstractDungeon.effectList
                  .add(
                     new ShowCardAndAddToDiscardEffect(this.makeNewCard(), Settings.WIDTH * 0.5F + (PADDING + AbstractCard.IMG_WIDTH), Settings.HEIGHT * 0.5F)
                  );
            }
            break;
         default:
            for (int i = 0; i < discardAmt; i++) {
               AbstractDungeon.effectList
                  .add(
                     new ShowCardAndAddToDiscardEffect(
                        this.makeNewCard(),
                        MathUtils.random(Settings.WIDTH * 0.2F, Settings.WIDTH * 0.8F),
                        MathUtils.random(Settings.HEIGHT * 0.3F, Settings.HEIGHT * 0.7F)
                     )
                  );
            }
      }
   }

   private AbstractCard makeNewCard() {
      return this.sameUUID ? this.c.makeSameInstanceOf() : this.c.makeStatEquivalentCopy();
   }
}
