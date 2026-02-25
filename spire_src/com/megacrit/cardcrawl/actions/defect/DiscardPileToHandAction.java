package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class DiscardPileToHandAction extends AbstractGameAction {
   public static final String[] TEXT;
   private AbstractPlayer p = AbstractDungeon.player;

   public DiscardPileToHandAction(int amount) {
      this.setValues(this.p, AbstractDungeon.player, amount);
      this.actionType = AbstractGameAction.ActionType.CARD_MANIPULATION;
   }

   @Override
   public void update() {
      if (this.p.hand.size() >= 10) {
         this.isDone = true;
      } else if (this.p.discardPile.size() == 1) {
         AbstractCard card = this.p.discardPile.group.get(0);
         if (this.p.hand.size() < 10) {
            this.p.hand.addToHand(card);
            this.p.discardPile.removeCard(card);
         }

         card.lighten(false);
         this.p.hand.refreshHandLayout();
         this.isDone = true;
      } else if (this.duration == 0.5F) {
         AbstractDungeon.gridSelectScreen.open(this.p.discardPile, this.amount, TEXT[0], false);
         this.tickDuration();
      } else {
         if (AbstractDungeon.gridSelectScreen.selectedCards.size() != 0) {
            for (AbstractCard c : AbstractDungeon.gridSelectScreen.selectedCards) {
               if (this.p.hand.size() < 10) {
                  this.p.hand.addToHand(c);
                  this.p.discardPile.removeCard(c);
               }

               c.lighten(false);
               c.unhover();
            }

            AbstractDungeon.gridSelectScreen.selectedCards.clear();
            this.p.hand.refreshHandLayout();

            for (AbstractCard c : this.p.discardPile.group) {
               c.unhover();
               c.target_x = CardGroup.DISCARD_PILE_X;
               c.target_y = 0.0F;
            }

            this.isDone = true;
         }

         this.tickDuration();
      }
   }

   static {
      TEXT = CardCrawlGame.languagePack.getUIString("DiscardPileToHandAction").TEXT;
   }
}
