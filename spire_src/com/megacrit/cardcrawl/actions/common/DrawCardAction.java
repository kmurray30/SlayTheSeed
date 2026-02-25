package com.megacrit.cardcrawl.actions.common;

import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.SoulGroup;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.PlayerTurnEffect;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DrawCardAction extends AbstractGameAction {
   private boolean shuffleCheck = false;
   private static final Logger logger = LogManager.getLogger(DrawCardAction.class.getName());
   public static ArrayList<AbstractCard> drawnCards = new ArrayList<>();
   private boolean clearDrawHistory = true;
   private AbstractGameAction followUpAction = null;

   public DrawCardAction(AbstractCreature source, int amount, boolean endTurnDraw) {
      if (endTurnDraw) {
         AbstractDungeon.topLevelEffects.add(new PlayerTurnEffect());
      }

      this.setValues(AbstractDungeon.player, source, amount);
      this.actionType = AbstractGameAction.ActionType.DRAW;
      if (Settings.FAST_MODE) {
         this.duration = Settings.ACTION_DUR_XFAST;
      } else {
         this.duration = Settings.ACTION_DUR_FASTER;
      }
   }

   public DrawCardAction(AbstractCreature source, int amount) {
      this(source, amount, false);
   }

   public DrawCardAction(int amount, boolean clearDrawHistory) {
      this(amount);
      this.clearDrawHistory = clearDrawHistory;
   }

   public DrawCardAction(int amount) {
      this(null, amount, false);
   }

   public DrawCardAction(int amount, AbstractGameAction action) {
      this(amount, action, true);
   }

   public DrawCardAction(int amount, AbstractGameAction action, boolean clearDrawHistory) {
      this(amount, clearDrawHistory);
      this.followUpAction = action;
   }

   @Override
   public void update() {
      if (this.clearDrawHistory) {
         this.clearDrawHistory = false;
         drawnCards.clear();
      }

      if (AbstractDungeon.player.hasPower("No Draw")) {
         AbstractDungeon.player.getPower("No Draw").flash();
         this.endActionWithFollowUp();
      } else if (this.amount <= 0) {
         this.endActionWithFollowUp();
      } else {
         int deckSize = AbstractDungeon.player.drawPile.size();
         int discardSize = AbstractDungeon.player.discardPile.size();
         if (!SoulGroup.isActive()) {
            if (deckSize + discardSize == 0) {
               this.endActionWithFollowUp();
            } else if (AbstractDungeon.player.hand.size() == 10) {
               AbstractDungeon.player.createHandIsFullDialog();
               this.endActionWithFollowUp();
            } else {
               if (!this.shuffleCheck) {
                  if (this.amount + AbstractDungeon.player.hand.size() > 10) {
                     int handSizeAndDraw = 10 - (this.amount + AbstractDungeon.player.hand.size());
                     this.amount += handSizeAndDraw;
                     AbstractDungeon.player.createHandIsFullDialog();
                  }

                  if (this.amount > deckSize) {
                     int tmp = this.amount - deckSize;
                     this.addToTop(new DrawCardAction(tmp, this.followUpAction, false));
                     this.addToTop(new EmptyDeckShuffleAction());
                     if (deckSize != 0) {
                        this.addToTop(new DrawCardAction(deckSize, false));
                     }

                     this.amount = 0;
                     this.isDone = true;
                     return;
                  }

                  this.shuffleCheck = true;
               }

               this.duration = this.duration - Gdx.graphics.getDeltaTime();
               if (this.amount != 0 && this.duration < 0.0F) {
                  if (Settings.FAST_MODE) {
                     this.duration = Settings.ACTION_DUR_XFAST;
                  } else {
                     this.duration = Settings.ACTION_DUR_FASTER;
                  }

                  this.amount--;
                  if (!AbstractDungeon.player.drawPile.isEmpty()) {
                     drawnCards.add(AbstractDungeon.player.drawPile.getTopCard());
                     AbstractDungeon.player.draw();
                     AbstractDungeon.player.hand.refreshHandLayout();
                  } else {
                     logger.warn(
                        "Player attempted to draw from an empty drawpile mid-DrawAction?MASTER DECK: " + AbstractDungeon.player.masterDeck.getCardNames()
                     );
                     this.endActionWithFollowUp();
                  }

                  if (this.amount == 0) {
                     this.endActionWithFollowUp();
                  }
               }
            }
         }
      }
   }

   private void endActionWithFollowUp() {
      this.isDone = true;
      if (this.followUpAction != null) {
         this.addToTop(this.followUpAction);
      }
   }
}
