package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;
import java.util.ArrayList;
import java.util.Iterator;

public class ExhumeAction extends AbstractGameAction {
   private AbstractPlayer p;
   private final boolean upgrade;
   private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("ExhumeAction");
   public static final String[] TEXT;
   private ArrayList<AbstractCard> exhumes = new ArrayList<>();

   public ExhumeAction(boolean upgrade) {
      this.upgrade = upgrade;
      this.p = AbstractDungeon.player;
      this.setValues(this.p, AbstractDungeon.player, this.amount);
      this.actionType = AbstractGameAction.ActionType.CARD_MANIPULATION;
      this.duration = Settings.ACTION_DUR_FAST;
   }

   @Override
   public void update() {
      if (this.duration == Settings.ACTION_DUR_FAST) {
         if (AbstractDungeon.player.hand.size() == 10) {
            AbstractDungeon.player.createHandIsFullDialog();
            this.isDone = true;
         } else if (this.p.exhaustPile.isEmpty()) {
            this.isDone = true;
         } else if (this.p.exhaustPile.size() == 1) {
            if (this.p.exhaustPile.group.get(0).cardID.equals("Exhume")) {
               this.isDone = true;
            } else {
               AbstractCard c = this.p.exhaustPile.getTopCard();
               c.unfadeOut();
               this.p.hand.addToHand(c);
               if (AbstractDungeon.player.hasPower("Corruption") && c.type == AbstractCard.CardType.SKILL) {
                  c.setCostForTurn(-9);
               }

               this.p.exhaustPile.removeCard(c);
               if (this.upgrade && c.canUpgrade()) {
                  c.upgrade();
               }

               c.unhover();
               c.fadingOut = false;
               this.isDone = true;
            }
         } else {
            for (AbstractCard cx : this.p.exhaustPile.group) {
               cx.stopGlowing();
               cx.unhover();
               cx.unfadeOut();
            }

            Iterator<AbstractCard> cx = this.p.exhaustPile.group.iterator();

            while (cx.hasNext()) {
               AbstractCard derp = cx.next();
               if (derp.cardID.equals("Exhume")) {
                  cx.remove();
                  this.exhumes.add(derp);
               }
            }

            if (this.p.exhaustPile.isEmpty()) {
               this.p.exhaustPile.group.addAll(this.exhumes);
               this.exhumes.clear();
               this.isDone = true;
            } else {
               AbstractDungeon.gridSelectScreen.open(this.p.exhaustPile, 1, TEXT[0], false);
               this.tickDuration();
            }
         }
      } else {
         if (!AbstractDungeon.gridSelectScreen.selectedCards.isEmpty()) {
            for (AbstractCard cx : AbstractDungeon.gridSelectScreen.selectedCards) {
               this.p.hand.addToHand(cx);
               if (AbstractDungeon.player.hasPower("Corruption") && cx.type == AbstractCard.CardType.SKILL) {
                  cx.setCostForTurn(-9);
               }

               this.p.exhaustPile.removeCard(cx);
               if (this.upgrade && cx.canUpgrade()) {
                  cx.upgrade();
               }

               cx.unhover();
            }

            AbstractDungeon.gridSelectScreen.selectedCards.clear();
            this.p.hand.refreshHandLayout();
            this.p.exhaustPile.group.addAll(this.exhumes);
            this.exhumes.clear();

            for (AbstractCard cx : this.p.exhaustPile.group) {
               cx.unhover();
               cx.target_x = CardGroup.DISCARD_PILE_X;
               cx.target_y = 0.0F;
            }
         }

         this.tickDuration();
      }
   }

   static {
      TEXT = uiStrings.TEXT;
   }
}
