package com.megacrit.cardcrawl.cards;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.cardManip.ExhaustCardEffect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CardGroup {
   private static final Logger logger = LogManager.getLogger(CardGroup.class.getName());
   public ArrayList<AbstractCard> group = new ArrayList<>();
   public float HAND_START_X = Settings.WIDTH * 0.36F;
   public float HAND_OFFSET_X = AbstractCard.IMG_WIDTH * 0.35F;
   private static final float HAND_HOVER_PUSH_AMT = 0.4F;
   private static final float PUSH_TAPER = 0.25F;
   private static final float TWO_CARD_PUSH_AMT = 0.2F;
   private static final float THREE_FOUR_CARD_PUSH_AMT = 0.27F;
   public static final float DRAW_PILE_X = Settings.WIDTH * 0.04F;
   public static final float DRAW_PILE_Y = 50.0F * Settings.scale;
   public static final int DISCARD_PILE_X = (int)(Settings.WIDTH + AbstractCard.IMG_WIDTH_S / 2.0F + 100.0F * Settings.scale);
   public static final int DISCARD_PILE_Y = 0;
   public CardGroup.CardGroupType type;
   public HashMap<Integer, Integer> handPositioningMap = new HashMap<>();
   private ArrayList<AbstractCard> queued = new ArrayList<>();
   private ArrayList<AbstractCard> inHand = new ArrayList<>();

   public CardGroup(CardGroup.CardGroupType type) {
      this.type = type;
   }

   public CardGroup(CardGroup g, CardGroup.CardGroupType type) {
      this.type = type;

      for (AbstractCard c : g.group) {
         this.group.add(c.makeSameInstanceOf());
      }
   }

   public ArrayList<CardSave> getCardDeck() {
      ArrayList<CardSave> retVal = new ArrayList<>();

      for (AbstractCard card : this.group) {
         retVal.add(new CardSave(card.cardID, card.timesUpgraded, card.misc));
      }

      return retVal;
   }

   public ArrayList<String> getCardNames() {
      ArrayList<String> retVal = new ArrayList<>();

      for (AbstractCard card : this.group) {
         retVal.add(card.cardID);
      }

      return retVal;
   }

   public ArrayList<String> getCardIdsForMetrics() {
      ArrayList<String> retVal = new ArrayList<>();

      for (AbstractCard card : this.group) {
         retVal.add(card.getMetricID());
      }

      return retVal;
   }

   public void clear() {
      this.group.clear();
   }

   public boolean contains(AbstractCard c) {
      return this.group.contains(c);
   }

   public boolean canUseAnyCard() {
      for (AbstractCard c : this.group) {
         if (c.hasEnoughEnergy()) {
            return true;
         }
      }

      return false;
   }

   public int fullSetCheck() {
      int times = 0;
      ArrayList<String> cardIDS = new ArrayList<>();

      for (AbstractCard c : this.group) {
         if (c.rarity != AbstractCard.CardRarity.BASIC) {
            cardIDS.add(c.cardID);
         }
      }

      HashMap<String, Integer> cardCount = new HashMap<>();

      for (String cardID : cardIDS) {
         if (cardCount.containsKey(cardID)) {
            cardCount.put(cardID, cardCount.get(cardID) + 1);
         } else {
            cardCount.put(cardID, 1);
         }
      }

      for (Entry<String, Integer> card : cardCount.entrySet()) {
         if (card.getValue() >= 4) {
            times++;
         }
      }

      return times;
   }

   public boolean pauperCheck() {
      for (AbstractCard c : this.group) {
         if (c.rarity == AbstractCard.CardRarity.RARE) {
            return false;
         }
      }

      return true;
   }

   public boolean cursedCheck() {
      int count = 0;

      for (AbstractCard c : this.group) {
         if (c.type == AbstractCard.CardType.CURSE) {
            count++;
         }
      }

      return count >= 5;
   }

   public boolean highlanderCheck() {
      ArrayList<String> cardIDS = new ArrayList<>();

      for (AbstractCard c : this.group) {
         if (c.rarity != AbstractCard.CardRarity.BASIC) {
            cardIDS.add(c.cardID);
         }
      }

      Set<String> set = new HashSet<>(cardIDS);
      return set.size() >= cardIDS.size();
   }

   public void applyPowers() {
      for (AbstractCard c : this.group) {
         c.applyPowers();
      }
   }

   public void removeCard(AbstractCard c) {
      this.group.remove(c);
      if (this.type == CardGroup.CardGroupType.MASTER_DECK) {
         c.onRemoveFromMasterDeck();

         for (AbstractRelic r : AbstractDungeon.player.relics) {
            r.onMasterDeckChange();
         }
      }
   }

   public boolean removeCard(String targetID) {
      Iterator<AbstractCard> i = this.group.iterator();

      while (i.hasNext()) {
         AbstractCard e = i.next();
         if (e.cardID.equals(targetID)) {
            i.remove();
            return true;
         }
      }

      return false;
   }

   public void addToHand(AbstractCard c) {
      c.untip();
      this.group.add(c);
   }

   public void refreshHandLayout() {
      if (AbstractDungeon.getCurrRoom().monsters == null || !AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead()) {
         if (AbstractDungeon.player.hasPower("Surrounded") && AbstractDungeon.getCurrRoom().monsters != null) {
            for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
               if (AbstractDungeon.player.flipHorizontal) {
                  if (AbstractDungeon.player.drawX < m.drawX) {
                     m.applyPowers();
                  } else {
                     m.applyPowers();
                     m.removeSurroundedPower();
                  }
               } else if (!AbstractDungeon.player.flipHorizontal) {
                  if (AbstractDungeon.player.drawX > m.drawX) {
                     m.applyPowers();
                  } else {
                     m.applyPowers();
                     m.removeSurroundedPower();
                  }
               }
            }
         }

         for (AbstractOrb o : AbstractDungeon.player.orbs) {
            o.hideEvokeValues();
         }

         if (AbstractDungeon.player.hand.size() + AbstractDungeon.player.drawPile.size() + AbstractDungeon.player.discardPile.size() <= 3
            && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT
            && AbstractDungeon.getCurrRoom().monsters != null
            && !AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead()
            && AbstractDungeon.floorNum > 3) {
            UnlockTracker.unlockAchievement("PURITY");
         }

         for (AbstractRelic r : AbstractDungeon.player.relics) {
            r.onRefreshHand();
         }

         float angleRange = 50.0F - (10 - this.group.size()) * 5.0F;
         float incrementAngle = angleRange / this.group.size();
         float sinkStart = 80.0F * Settings.scale;
         float sinkRange = 300.0F * Settings.scale;
         float incrementSink = sinkRange / this.group.size() / 2.0F;
         int middle = this.group.size() / 2;

         for (int i = 0; i < this.group.size(); i++) {
            this.group.get(i).setAngle(angleRange / 2.0F - incrementAngle * i - incrementAngle / 2.0F);
            int t = i - middle;
            if (t >= 0) {
               if (this.group.size() % 2 == 0) {
                  t = -(++t);
               } else {
                  t = -t;
               }
            }

            if (this.group.size() % 2 == 0) {
               t++;
            }

            t = (int)(t * 1.7F);
            this.group.get(i).target_y = sinkStart + incrementSink * t;
         }

         for (AbstractCard c : this.group) {
            c.targetDrawScale = 0.75F;
         }

         switch (this.group.size()) {
            case 0:
               return;
            case 1:
               this.group.get(0).target_x = Settings.WIDTH / 2.0F;
               break;
            case 2:
               this.group.get(0).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 0.47F;
               this.group.get(1).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 0.53F;
               break;
            case 3:
               this.group.get(0).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 0.9F;
               this.group.get(1).target_x = Settings.WIDTH / 2.0F;
               this.group.get(2).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 0.9F;
               AbstractCard var55 = this.group.get(0);
               var55.target_y = var55.target_y + 20.0F * Settings.scale;
               var55 = this.group.get(2);
               var55.target_y = var55.target_y + 20.0F * Settings.scale;
               break;
            case 4:
               this.group.get(0).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 1.36F;
               this.group.get(1).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 0.46F;
               this.group.get(2).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 0.46F;
               this.group.get(3).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 1.36F;
               AbstractCard var53 = this.group.get(1);
               var53.target_y = var53.target_y - 10.0F * Settings.scale;
               var53 = this.group.get(2);
               var53.target_y = var53.target_y - 10.0F * Settings.scale;
               break;
            case 5:
               this.group.get(0).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 1.7F;
               this.group.get(1).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 0.9F;
               this.group.get(2).target_x = Settings.WIDTH / 2.0F;
               this.group.get(3).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 0.9F;
               this.group.get(4).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 1.7F;
               AbstractCard var50 = this.group.get(0);
               var50.target_y = var50.target_y + 25.0F * Settings.scale;
               var50 = this.group.get(2);
               var50.target_y = var50.target_y - 10.0F * Settings.scale;
               var50 = this.group.get(4);
               var50.target_y = var50.target_y + 25.0F * Settings.scale;
               break;
            case 6:
               this.group.get(0).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 2.1F;
               this.group.get(1).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 1.3F;
               this.group.get(2).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 0.43F;
               this.group.get(3).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 0.43F;
               this.group.get(4).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 1.3F;
               this.group.get(5).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 2.1F;
               AbstractCard var48 = this.group.get(0);
               var48.target_y = var48.target_y + 10.0F * Settings.scale;
               var48 = this.group.get(5);
               var48.target_y = var48.target_y + 10.0F * Settings.scale;
               break;
            case 7:
               this.group.get(0).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 2.4F;
               this.group.get(1).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 1.7F;
               this.group.get(2).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 0.9F;
               this.group.get(3).target_x = Settings.WIDTH / 2.0F;
               this.group.get(4).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 0.9F;
               this.group.get(5).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 1.7F;
               this.group.get(6).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 2.4F;
               AbstractCard var43 = this.group.get(0);
               var43.target_y = var43.target_y + 25.0F * Settings.scale;
               var43 = this.group.get(1);
               var43.target_y = var43.target_y + 18.0F * Settings.scale;
               var43 = this.group.get(3);
               var43.target_y = var43.target_y - 6.0F * Settings.scale;
               var43 = this.group.get(5);
               var43.target_y = var43.target_y + 18.0F * Settings.scale;
               var43 = this.group.get(6);
               var43.target_y = var43.target_y + 25.0F * Settings.scale;
               break;
            case 8:
               this.group.get(0).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 2.5F;
               this.group.get(1).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 1.82F;
               this.group.get(2).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 1.1F;
               this.group.get(3).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 0.38F;
               this.group.get(4).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 0.38F;
               this.group.get(5).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 1.1F;
               this.group.get(6).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 1.77F;
               this.group.get(7).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 2.5F;
               AbstractCard var41 = this.group.get(1);
               var41.target_y = var41.target_y + 10.0F * Settings.scale;
               var41 = this.group.get(6);
               var41.target_y = var41.target_y + 10.0F * Settings.scale;

               for (AbstractCard c : this.group) {
                  c.targetDrawScale = 0.7125F;
               }
               break;
            case 9:
               this.group.get(0).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 2.8F;
               this.group.get(1).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 2.2F;
               this.group.get(2).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 1.53F;
               this.group.get(3).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 0.8F;
               this.group.get(4).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 0.0F;
               this.group.get(5).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 0.8F;
               this.group.get(6).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 1.53F;
               this.group.get(7).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 2.2F;
               this.group.get(8).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 2.8F;
               AbstractCard var35 = this.group.get(1);
               var35.target_y = var35.target_y + 22.0F * Settings.scale;
               var35 = this.group.get(2);
               var35.target_y = var35.target_y + 18.0F * Settings.scale;
               var35 = this.group.get(3);
               var35.target_y = var35.target_y + 12.0F * Settings.scale;
               var35 = this.group.get(5);
               var35.target_y = var35.target_y + 12.0F * Settings.scale;
               var35 = this.group.get(6);
               var35.target_y = var35.target_y + 18.0F * Settings.scale;
               var35 = this.group.get(7);
               var35.target_y = var35.target_y + 22.0F * Settings.scale;

               for (AbstractCard c : this.group) {
                  c.targetDrawScale = 0.67499995F;
               }
               break;
            case 10:
               this.group.get(0).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 2.9F;
               this.group.get(1).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 2.4F;
               this.group.get(2).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 1.8F;
               this.group.get(3).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 1.1F;
               this.group.get(4).target_x = Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH_S * 0.4F;
               this.group.get(5).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 0.4F;
               this.group.get(6).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 1.1F;
               this.group.get(7).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 1.8F;
               this.group.get(8).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 2.4F;
               this.group.get(9).target_x = Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH_S * 2.9F;
               AbstractCard var10000 = this.group.get(1);
               var10000.target_y = var10000.target_y + 20.0F * Settings.scale;
               var10000 = this.group.get(2);
               var10000.target_y = var10000.target_y + 17.0F * Settings.scale;
               var10000 = this.group.get(3);
               var10000.target_y = var10000.target_y + 12.0F * Settings.scale;
               var10000 = this.group.get(4);
               var10000.target_y = var10000.target_y + 5.0F * Settings.scale;
               var10000 = this.group.get(5);
               var10000.target_y = var10000.target_y + 5.0F * Settings.scale;
               var10000 = this.group.get(6);
               var10000.target_y = var10000.target_y + 12.0F * Settings.scale;
               var10000 = this.group.get(7);
               var10000.target_y = var10000.target_y + 17.0F * Settings.scale;
               var10000 = this.group.get(8);
               var10000.target_y = var10000.target_y + 20.0F * Settings.scale;

               for (AbstractCard c : this.group) {
                  c.targetDrawScale = 0.63750005F;
               }
               break;
            default:
               logger.info("WTF MATE, why so many cards");
         }

         AbstractCard card = AbstractDungeon.player.hoveredCard;
         if (card != null) {
            card.setAngle(0.0F);
            card.target_x = (card.current_x + card.target_x) / 2.0F;
            card.target_y = card.current_y;
         }

         for (CardQueueItem q : AbstractDungeon.actionManager.cardQueue) {
            if (q.card != null) {
               q.card.setAngle(0.0F);
               q.card.target_x = q.card.current_x;
               q.card.target_y = q.card.current_y;
            }
         }

         this.glowCheck();
      }
   }

   public void glowCheck() {
      for (AbstractCard c : this.group) {
         if (c.canUse(AbstractDungeon.player, null) && AbstractDungeon.screen != AbstractDungeon.CurrentScreen.HAND_SELECT) {
            c.beginGlowing();
         } else {
            c.stopGlowing();
         }

         c.triggerOnGlowCheck();
      }
   }

   public void stopGlowing() {
      for (AbstractCard c : this.group) {
         c.stopGlowing();
      }
   }

   public void hoverCardPush(AbstractCard c) {
      if (this.group.size() > 1) {
         int cardNum = -1;

         for (int i = 0; i < this.group.size(); i++) {
            if (c.equals(this.group.get(i))) {
               cardNum = i;
               break;
            }
         }

         float pushAmt = 0.4F;
         if (this.group.size() == 2) {
            pushAmt = 0.2F;
         } else if (this.group.size() == 3 || this.group.size() == 4) {
            pushAmt = 0.27F;
         }

         for (int currentSlot = cardNum + 1; currentSlot < this.group.size(); currentSlot++) {
            AbstractCard var10000 = this.group.get(currentSlot);
            var10000.target_x = var10000.target_x + AbstractCard.IMG_WIDTH_S * pushAmt;
            pushAmt *= 0.25F;
         }

         pushAmt = 0.4F;
         if (this.group.size() == 2) {
            pushAmt = 0.2F;
         } else if (this.group.size() == 3 || this.group.size() == 4) {
            pushAmt = 0.27F;
         }

         for (int var7 = cardNum - 1; var7 > -1 && var7 < this.group.size(); var7--) {
            AbstractCard var8 = this.group.get(var7);
            var8.target_x = var8.target_x - AbstractCard.IMG_WIDTH_S * pushAmt;
            pushAmt *= 0.25F;
         }
      }
   }

   public void addToTop(AbstractCard c) {
      this.group.add(c);
   }

   public void addToBottom(AbstractCard c) {
      this.group.add(0, c);
   }

   public void addToRandomSpot(AbstractCard c) {
      if (this.group.size() == 0) {
         this.group.add(c);
      } else {
         this.group.add(AbstractDungeon.cardRandomRng.random(this.group.size() - 1), c);
      }
   }

   public AbstractCard getTopCard() {
      return this.group.get(this.group.size() - 1);
   }

   public AbstractCard getNCardFromTop(int num) {
      return this.group.get(this.group.size() - 1 - num);
   }

   public AbstractCard getBottomCard() {
      return this.group.get(0);
   }

   public AbstractCard getHoveredCard() {
      for (AbstractCard c : this.group) {
         if (c.isHoveredInHand(0.7F)) {
            boolean success = true;

            for (CardQueueItem q : AbstractDungeon.actionManager.cardQueue) {
               if (q.card == c) {
                  success = false;
                  break;
               }
            }

            if (success) {
               return c;
            }
         }
      }

      return null;
   }

   public AbstractCard getRandomCard(Random rng) {
      return this.group.get(rng.random(this.group.size() - 1));
   }

   public AbstractCard getRandomCard(boolean useRng) {
      return useRng ? this.group.get(AbstractDungeon.cardRng.random(this.group.size() - 1)) : this.group.get(MathUtils.random(this.group.size() - 1));
   }

   public AbstractCard getRandomCard(boolean useRng, AbstractCard.CardRarity rarity) {
      ArrayList<AbstractCard> tmp = new ArrayList<>();

      for (AbstractCard c : this.group) {
         if (c.rarity == rarity) {
            tmp.add(c);
         }
      }

      if (tmp.isEmpty()) {
         logger.info("ERROR: No cards left for type: " + this.type.name());
         return null;
      } else {
         Collections.sort(tmp);
         return useRng ? tmp.get(AbstractDungeon.cardRng.random(tmp.size() - 1)) : tmp.get(MathUtils.random(tmp.size() - 1));
      }
   }

   public AbstractCard getRandomCard(Random rng, AbstractCard.CardRarity rarity) {
      ArrayList<AbstractCard> tmp = new ArrayList<>();

      for (AbstractCard c : this.group) {
         if (c.rarity == rarity) {
            tmp.add(c);
         }
      }

      if (tmp.isEmpty()) {
         logger.info("ERROR: No cards left for type: " + this.type.name());
         return null;
      } else {
         Collections.sort(tmp);
         return tmp.get(rng.random(tmp.size() - 1));
      }
   }

   public AbstractCard getRandomCard(AbstractCard.CardType type, boolean useRng) {
      ArrayList<AbstractCard> tmp = new ArrayList<>();

      for (AbstractCard c : this.group) {
         if (c.type == type) {
            tmp.add(c);
         }
      }

      if (tmp.isEmpty()) {
         logger.info("ERROR: No cards left for type: " + type.name());
         return null;
      } else {
         Collections.sort(tmp);
         return useRng ? tmp.get(AbstractDungeon.cardRng.random(tmp.size() - 1)) : tmp.get(MathUtils.random(tmp.size() - 1));
      }
   }

   public void removeTopCard() {
      this.group.remove(this.group.size() - 1);
   }

   public void shuffle() {
      Collections.shuffle(this.group, new java.util.Random(AbstractDungeon.shuffleRng.randomLong()));
   }

   public void shuffle(Random rng) {
      Collections.shuffle(this.group, new java.util.Random(rng.randomLong()));
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("");

      for (AbstractCard c : this.group) {
         sb.append(c.cardID);
         sb.append("\n");
      }

      return sb.toString();
   }

   public void update() {
      for (AbstractCard c : this.group) {
         c.update();
      }
   }

   public void updateHoverLogic() {
      for (AbstractCard c : this.group) {
         c.updateHoverLogic();
      }
   }

   public void render(SpriteBatch sb) {
      for (AbstractCard c : this.group) {
         c.render(sb);
      }
   }

   public void renderShowBottled(SpriteBatch sb) {
      for (AbstractCard c : this.group) {
         c.render(sb);
         if (c.inBottleFlame) {
            AbstractRelic tmp = RelicLibrary.getRelic("Bottled Flame");
            float prevX = tmp.currentX;
            float prevY = tmp.currentY;
            tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.scale = c.drawScale * Settings.scale * 1.5F;
            tmp.render(sb);
            tmp.currentX = prevX;
            tmp.currentY = prevY;
            Object var7 = null;
         } else if (c.inBottleLightning) {
            AbstractRelic tmp = RelicLibrary.getRelic("Bottled Lightning");
            float prevX = tmp.currentX;
            float prevY = tmp.currentY;
            tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.scale = c.drawScale * Settings.scale * 1.5F;
            tmp.render(sb);
            tmp.currentX = prevX;
            tmp.currentY = prevY;
            Object var9 = null;
         } else if (c.inBottleTornado) {
            AbstractRelic tmp = RelicLibrary.getRelic("Bottled Tornado");
            float prevX = tmp.currentX;
            float prevY = tmp.currentY;
            tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.scale = c.drawScale * Settings.scale * 1.5F;
            tmp.render(sb);
            tmp.currentX = prevX;
            tmp.currentY = prevY;
            Object var11 = null;
         }
      }
   }

   public void renderMasterDeck(SpriteBatch sb) {
      for (AbstractCard c : this.group) {
         c.render(sb);
         if (c.inBottleFlame) {
            AbstractRelic tmp = RelicLibrary.getRelic("Bottled Flame");
            float prevX = tmp.currentX;
            float prevY = tmp.currentY;
            tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.scale = c.drawScale * Settings.scale * 1.5F;
            tmp.render(sb);
            tmp.currentX = prevX;
            tmp.currentY = prevY;
            Object var7 = null;
         } else if (c.inBottleLightning) {
            AbstractRelic tmp = RelicLibrary.getRelic("Bottled Lightning");
            float prevX = tmp.currentX;
            float prevY = tmp.currentY;
            tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.scale = c.drawScale * Settings.scale * 1.5F;
            tmp.render(sb);
            tmp.currentX = prevX;
            tmp.currentY = prevY;
            Object var9 = null;
         } else if (c.inBottleTornado) {
            AbstractRelic tmp = RelicLibrary.getRelic("Bottled Tornado");
            float prevX = tmp.currentX;
            float prevY = tmp.currentY;
            tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
            tmp.scale = c.drawScale * Settings.scale * 1.5F;
            tmp.render(sb);
            tmp.currentX = prevX;
            tmp.currentY = prevY;
            Object var11 = null;
         }
      }
   }

   public void renderExceptOneCard(SpriteBatch sb, AbstractCard card) {
      for (AbstractCard c : this.group) {
         if (!c.equals(card)) {
            c.render(sb);
         }
      }
   }

   public void renderExceptOneCardShowBottled(SpriteBatch sb, AbstractCard card) {
      for (AbstractCard c : this.group) {
         if (!c.equals(card)) {
            c.render(sb);
            if (c.inBottleFlame) {
               AbstractRelic tmp = RelicLibrary.getRelic("Bottled Flame");
               float prevX = tmp.currentX;
               float prevY = tmp.currentY;
               tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.scale = c.drawScale * Settings.scale * 1.5F;
               tmp.render(sb);
               tmp.currentX = prevX;
               tmp.currentY = prevY;
               Object var8 = null;
            } else if (c.inBottleLightning) {
               AbstractRelic tmp = RelicLibrary.getRelic("Bottled Lightning");
               float prevX = tmp.currentX;
               float prevY = tmp.currentY;
               tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.scale = c.drawScale * Settings.scale * 1.5F;
               tmp.render(sb);
               tmp.currentX = prevX;
               tmp.currentY = prevY;
               Object var10 = null;
            } else if (c.inBottleTornado) {
               AbstractRelic tmp = RelicLibrary.getRelic("Bottled Tornado");
               float prevX = tmp.currentX;
               float prevY = tmp.currentY;
               tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.scale = c.drawScale * Settings.scale * 1.5F;
               tmp.render(sb);
               tmp.currentX = prevX;
               tmp.currentY = prevY;
               Object var12 = null;
            }
         }
      }
   }

   public void renderMasterDeckExceptOneCard(SpriteBatch sb, AbstractCard card) {
      for (AbstractCard c : this.group) {
         if (!c.equals(card)) {
            c.render(sb);
            if (c.inBottleFlame) {
               AbstractRelic tmp = RelicLibrary.getRelic("Bottled Flame");
               float prevX = tmp.currentX;
               float prevY = tmp.currentY;
               tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.scale = c.drawScale * Settings.scale * 1.5F;
               tmp.render(sb);
               tmp.currentX = prevX;
               tmp.currentY = prevY;
               Object var8 = null;
            } else if (c.inBottleLightning) {
               AbstractRelic tmp = RelicLibrary.getRelic("Bottled Lightning");
               float prevX = tmp.currentX;
               float prevY = tmp.currentY;
               tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.scale = c.drawScale * Settings.scale * 1.5F;
               tmp.render(sb);
               tmp.currentX = prevX;
               tmp.currentY = prevY;
               Object var10 = null;
            } else if (c.inBottleTornado) {
               AbstractRelic tmp = RelicLibrary.getRelic("Bottled Tornado");
               float prevX = tmp.currentX;
               float prevY = tmp.currentY;
               tmp.currentX = c.current_x + 390.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.currentY = c.current_y + 546.0F * c.drawScale / 3.0F * Settings.scale;
               tmp.scale = c.drawScale * Settings.scale * 1.5F;
               tmp.render(sb);
               tmp.currentX = prevX;
               tmp.currentY = prevY;
               Object var12 = null;
            }
         }
      }
   }

   public void renderHand(SpriteBatch sb, AbstractCard exceptThis) {
      for (AbstractCard c : this.group) {
         if (c != exceptThis) {
            boolean inQueue = false;

            for (CardQueueItem i : AbstractDungeon.actionManager.cardQueue) {
               if (i.card != null && i.card.equals(c)) {
                  this.queued.add(c);
                  inQueue = true;
                  break;
               }
            }

            if (!inQueue) {
               this.inHand.add(c);
            }
         }
      }

      for (AbstractCard cx : this.inHand) {
         cx.render(sb);
      }

      for (AbstractCard cx : this.queued) {
         cx.render(sb);
      }

      this.inHand.clear();
      this.queued.clear();
   }

   public void renderInLibrary(SpriteBatch sb) {
      for (AbstractCard c : this.group) {
         c.renderInLibrary(sb);
      }
   }

   public void renderTip(SpriteBatch sb) {
      for (AbstractCard c : this.group) {
         c.renderCardTip(sb);
      }
   }

   public void renderWithSelections(SpriteBatch sb) {
      for (AbstractCard c : this.group) {
         c.renderWithSelections(sb);
      }
   }

   public void renderDiscardPile(SpriteBatch sb) {
      for (AbstractCard c : this.group) {
         c.render(sb);
      }
   }

   public void moveToDiscardPile(AbstractCard c) {
      this.resetCardBeforeMoving(c);
      c.shrink();
      c.darken(false);
      AbstractDungeon.getCurrRoom().souls.discard(c);
      AbstractDungeon.player.onCardDrawOrDiscard();
   }

   public void empower(AbstractCard c) {
      this.resetCardBeforeMoving(c);
      c.shrink();
      AbstractDungeon.getCurrRoom().souls.empower(c);
   }

   public void moveToExhaustPile(AbstractCard c) {
      for (AbstractRelic r : AbstractDungeon.player.relics) {
         r.onExhaust(c);
      }

      for (AbstractPower p : AbstractDungeon.player.powers) {
         p.onExhaust(c);
      }

      c.triggerOnExhaust();
      this.resetCardBeforeMoving(c);
      AbstractDungeon.effectList.add(new ExhaustCardEffect(c));
      AbstractDungeon.player.exhaustPile.addToTop(c);
      AbstractDungeon.player.onCardDrawOrDiscard();
   }

   public void moveToHand(AbstractCard c, CardGroup group) {
      c.unhover();
      c.lighten(true);
      c.setAngle(0.0F);
      c.drawScale = 0.12F;
      c.targetDrawScale = 0.75F;
      c.current_x = DRAW_PILE_X;
      c.current_y = DRAW_PILE_Y;
      group.removeCard(c);
      AbstractDungeon.player.hand.addToTop(c);
      AbstractDungeon.player.hand.refreshHandLayout();
      AbstractDungeon.player.hand.applyPowers();
   }

   public void moveToHand(AbstractCard c) {
      this.resetCardBeforeMoving(c);
      c.unhover();
      c.lighten(true);
      c.setAngle(0.0F);
      c.drawScale = 0.12F;
      c.targetDrawScale = 0.75F;
      c.current_x = DRAW_PILE_X;
      c.current_y = DRAW_PILE_Y;
      AbstractDungeon.player.hand.addToTop(c);
      AbstractDungeon.player.hand.refreshHandLayout();
      AbstractDungeon.player.hand.applyPowers();
   }

   public void moveToDeck(AbstractCard c, boolean randomSpot) {
      this.resetCardBeforeMoving(c);
      c.shrink();
      AbstractDungeon.getCurrRoom().souls.onToDeck(c, randomSpot);
   }

   public void moveToBottomOfDeck(AbstractCard c) {
      this.resetCardBeforeMoving(c);
      c.shrink();
      AbstractDungeon.getCurrRoom().souls.onToBottomOfDeck(c);
   }

   private void resetCardBeforeMoving(AbstractCard c) {
      if (AbstractDungeon.player.hoveredCard == c) {
         AbstractDungeon.player.releaseCard();
      }

      AbstractDungeon.actionManager.removeFromQueue(c);
      c.unhover();
      c.untip();
      c.stopGlowing();
      this.group.remove(c);
   }

   public boolean isEmpty() {
      return this.group.isEmpty();
   }

   private void discardAll(CardGroup discardPile) {
      for (AbstractCard c : this.group) {
         c.target_x = DISCARD_PILE_X;
         c.target_y = 0.0F;
         discardPile.addToTop(c);
      }

      this.group.clear();
   }

   public void initializeDeck(CardGroup masterDeck) {
      this.clear();
      CardGroup copy = new CardGroup(masterDeck, CardGroup.CardGroupType.DRAW_PILE);
      copy.shuffle(AbstractDungeon.shuffleRng);
      ArrayList<AbstractCard> placeOnTop = new ArrayList<>();

      for (AbstractCard c : copy.group) {
         if (c.isInnate) {
            placeOnTop.add(c);
         } else if (!c.inBottleFlame && !c.inBottleLightning && !c.inBottleTornado) {
            c.target_x = DRAW_PILE_X;
            c.target_y = DRAW_PILE_Y;
            c.current_x = DRAW_PILE_X;
            c.current_y = DRAW_PILE_Y;
            this.addToTop(c);
         } else {
            placeOnTop.add(c);
         }
      }

      for (AbstractCard cx : placeOnTop) {
         this.addToTop(cx);
      }

      if (placeOnTop.size() > AbstractDungeon.player.masterHandSize) {
         AbstractDungeon.actionManager.addToTurnStart(new DrawCardAction(AbstractDungeon.player, placeOnTop.size() - AbstractDungeon.player.masterHandSize));
      }

      placeOnTop.clear();
   }

   public int size() {
      return this.group.size();
   }

   public CardGroup getUpgradableCards() {
      CardGroup retVal = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

      for (AbstractCard c : this.group) {
         if (c.canUpgrade()) {
            retVal.group.add(c);
         }
      }

      return retVal;
   }

   public Boolean hasUpgradableCards() {
      for (AbstractCard c : this.group) {
         if (c.canUpgrade()) {
            return true;
         }
      }

      return false;
   }

   public CardGroup getPurgeableCards() {
      CardGroup retVal = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

      for (AbstractCard c : this.group) {
         if (!c.cardID.equals("Necronomicurse") && !c.cardID.equals("CurseOfTheBell") && !c.cardID.equals("AscendersBane")) {
            retVal.group.add(c);
         }
      }

      return retVal;
   }

   public AbstractCard getSpecificCard(AbstractCard targetCard) {
      return this.group.contains(targetCard) ? targetCard : null;
   }

   public void triggerOnOtherCardPlayed(AbstractCard usedCard) {
      for (AbstractCard c : this.group) {
         if (c != usedCard) {
            c.triggerOnOtherCardPlayed(usedCard);
         }
      }

      for (AbstractPower p : AbstractDungeon.player.powers) {
         p.onAfterCardPlayed(usedCard);
      }
   }

   private void sortWithComparator(Comparator<AbstractCard> comp, boolean ascending) {
      if (ascending) {
         this.group.sort(comp);
      } else {
         this.group.sort(Collections.reverseOrder(comp));
      }
   }

   public void sortByRarity(boolean ascending) {
      this.sortWithComparator(new CardGroup.CardRarityComparator(), ascending);
   }

   public void sortByRarityPlusStatusCardType(boolean ascending) {
      this.sortWithComparator(new CardGroup.CardRarityComparator(), ascending);
      this.sortWithComparator(new CardGroup.StatusCardsLastComparator(), true);
   }

   public void sortByType(boolean ascending) {
      this.sortWithComparator(new CardGroup.CardTypeComparator(), ascending);
   }

   public void sortByAcquisition() {
   }

   public void sortByStatus(boolean ascending) {
      this.sortWithComparator(new CardGroup.CardLockednessComparator(), ascending);
   }

   public void sortAlphabetically(boolean ascending) {
      this.sortWithComparator(new CardGroup.CardNameComparator(), ascending);
   }

   public void sortByCost(boolean ascending) {
      this.sortWithComparator(new CardGroup.CardCostComparator(), ascending);
   }

   public CardGroup getSkills() {
      return this.getCardsOfType(AbstractCard.CardType.SKILL);
   }

   public CardGroup getAttacks() {
      return this.getCardsOfType(AbstractCard.CardType.ATTACK);
   }

   public CardGroup getPowers() {
      return this.getCardsOfType(AbstractCard.CardType.POWER);
   }

   public CardGroup getCardsOfType(AbstractCard.CardType cardType) {
      CardGroup retVal = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

      for (AbstractCard card : this.group) {
         if (card.type == cardType) {
            retVal.addToBottom(card);
         }
      }

      return retVal;
   }

   public CardGroup getGroupedByColor() {
      ArrayList<CardGroup> colorGroups = new ArrayList<>();

      for (AbstractCard.CardColor color : AbstractCard.CardColor.values()) {
         colorGroups.add(new CardGroup(CardGroup.CardGroupType.UNSPECIFIED));
      }

      for (AbstractCard card : this.group) {
         colorGroups.get(card.color.ordinal()).addToTop(card);
      }

      CardGroup retVal = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

      for (CardGroup group : colorGroups) {
         retVal.group.addAll(group.group);
      }

      return retVal;
   }

   public AbstractCard findCardById(String id) {
      for (AbstractCard c : this.group) {
         if (c.cardID.equals(id)) {
            return c;
         }
      }

      return null;
   }

   public static CardGroup getGroupWithoutBottledCards(CardGroup group) {
      CardGroup retVal = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

      for (AbstractCard c : group.group) {
         if (!c.inBottleFlame && !c.inBottleLightning && !c.inBottleTornado) {
            retVal.addToTop(c);
         }
      }

      return retVal;
   }

   private class CardCostComparator implements Comparator<AbstractCard> {
      private CardCostComparator() {
      }

      public int compare(AbstractCard c1, AbstractCard c2) {
         return c1.cost - c2.cost;
      }
   }

   public static enum CardGroupType {
      DRAW_PILE,
      MASTER_DECK,
      HAND,
      DISCARD_PILE,
      EXHAUST_PILE,
      CARD_POOL,
      UNSPECIFIED;
   }

   private class CardLockednessComparator implements Comparator<AbstractCard> {
      private CardLockednessComparator() {
      }

      public int compare(AbstractCard c1, AbstractCard c2) {
         int c1Rank = 0;
         if (UnlockTracker.isCardLocked(c1.cardID)) {
            c1Rank = 2;
         } else if (!UnlockTracker.isCardSeen(c1.cardID)) {
            c1Rank = 1;
         }

         int c2Rank = 0;
         if (UnlockTracker.isCardLocked(c2.cardID)) {
            c2Rank = 2;
         } else if (!UnlockTracker.isCardSeen(c2.cardID)) {
            c2Rank = 1;
         }

         return c1Rank - c2Rank;
      }
   }

   private class CardNameComparator implements Comparator<AbstractCard> {
      private CardNameComparator() {
      }

      public int compare(AbstractCard c1, AbstractCard c2) {
         return c1.name.compareTo(c2.name);
      }
   }

   private class CardRarityComparator implements Comparator<AbstractCard> {
      private CardRarityComparator() {
      }

      public int compare(AbstractCard c1, AbstractCard c2) {
         return c1.rarity.compareTo(c2.rarity);
      }
   }

   private class CardTypeComparator implements Comparator<AbstractCard> {
      private CardTypeComparator() {
      }

      public int compare(AbstractCard c1, AbstractCard c2) {
         return c1.type.compareTo(c2.type);
      }
   }

   private class StatusCardsLastComparator implements Comparator<AbstractCard> {
      private StatusCardsLastComparator() {
      }

      public int compare(AbstractCard c1, AbstractCard c2) {
         if (c1.type == AbstractCard.CardType.STATUS && c2.type != AbstractCard.CardType.STATUS) {
            return 1;
         } else {
            return c1.type != AbstractCard.CardType.STATUS && c2.type == AbstractCard.CardType.STATUS ? -1 : 0;
         }
      }
   }
}
