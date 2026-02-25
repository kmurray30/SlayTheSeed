package com.megacrit.cardcrawl.cards;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SoulGroup {
   private static final Logger logger = LogManager.getLogger(SoulGroup.class.getName());
   private ArrayList<Soul> souls = new ArrayList<>();
   private static final int DEFAULT_SOUL_CACHE = 20;

   public SoulGroup() {
      for (int i = 0; i < 20; i++) {
         this.souls.add(new Soul());
      }
   }

   public void discard(AbstractCard card, boolean visualOnly) {
      boolean needMoreSouls = true;

      for (Soul s : this.souls) {
         if (s.isReadyForReuse) {
            card.untip();
            card.unhover();
            s.discard(card, visualOnly);
            needMoreSouls = false;
            break;
         }
      }

      if (needMoreSouls) {
         logger.info("Not enough souls, creating...");
         Soul sx = new Soul();
         sx.discard(card, visualOnly);
         this.souls.add(sx);
      }
   }

   public void discard(AbstractCard card) {
      this.discard(card, false);
   }

   public void empower(AbstractCard card) {
      boolean needMoreSouls = true;

      for (Soul s : this.souls) {
         if (s.isReadyForReuse) {
            card.untip();
            card.unhover();
            s.empower(card);
            needMoreSouls = false;
            break;
         }
      }

      if (needMoreSouls) {
         logger.info("Not enough souls, creating...");
         Soul sx = new Soul();
         sx.empower(card);
         this.souls.add(sx);
      }
   }

   public void obtain(AbstractCard card, boolean obtainCard) {
      CardCrawlGame.sound.play("CARD_OBTAIN");
      boolean needMoreSouls = true;

      for (Soul s : this.souls) {
         if (s.isReadyForReuse) {
            if (obtainCard) {
               s.obtain(card);
            }

            needMoreSouls = false;
            break;
         }
      }

      if (needMoreSouls) {
         logger.info("Not enough souls, creating...");
         Soul sx = new Soul();
         if (obtainCard) {
            sx.obtain(card);
         }

         this.souls.add(sx);
      }
   }

   public void shuffle(AbstractCard card, boolean isInvisible) {
      card.untip();
      card.unhover();
      card.darken(true);
      card.shrink(true);
      boolean needMoreSouls = true;

      for (Soul s : this.souls) {
         if (s.isReadyForReuse) {
            s.shuffle(card, isInvisible);
            needMoreSouls = false;
            break;
         }
      }

      if (needMoreSouls) {
         logger.info("Not enough souls, creating...");
         Soul sx = new Soul();
         sx.shuffle(card, isInvisible);
         this.souls.add(sx);
      }
   }

   public void onToBottomOfDeck(AbstractCard card) {
      boolean needMoreSouls = true;

      for (Soul s : this.souls) {
         if (s.isReadyForReuse) {
            card.untip();
            card.unhover();
            s.onToBottomOfDeck(card);
            needMoreSouls = false;
            break;
         }
      }

      if (needMoreSouls) {
         logger.info("Not enough souls, creating...");
         Soul sx = new Soul();
         sx.onToBottomOfDeck(card);
         this.souls.add(sx);
      }
   }

   public void onToDeck(AbstractCard card, boolean randomSpot, boolean visualOnly) {
      boolean needMoreSouls = true;

      for (Soul s : this.souls) {
         if (s.isReadyForReuse) {
            card.untip();
            card.unhover();
            s.onToDeck(card, randomSpot, visualOnly);
            needMoreSouls = false;
            break;
         }
      }

      if (needMoreSouls) {
         logger.info("Not enough souls, creating...");
         Soul sx = new Soul();
         sx.onToDeck(card, randomSpot, visualOnly);
         this.souls.add(sx);
      }
   }

   public void onToDeck(AbstractCard card, boolean randomSpot) {
      this.onToDeck(card, randomSpot, false);
   }

   public void update() {
      for (Soul s : this.souls) {
         if (!s.isReadyForReuse) {
            s.update();
         }
      }
   }

   public void render(SpriteBatch sb) {
      for (Soul s : this.souls) {
         if (!s.isReadyForReuse) {
            s.render(sb);
         }
      }
   }

   public void remove(AbstractCard card) {
      Iterator<Soul> s = this.souls.iterator();

      while (s.hasNext()) {
         Soul derp = s.next();
         if (derp.card == card) {
            s.remove();
            logger.info(derp + " removed.");
            break;
         }
      }
   }

   public static boolean isActive() {
      for (Soul s : AbstractDungeon.getCurrRoom().souls.souls) {
         if (!s.isReadyForReuse) {
            return true;
         }
      }

      return false;
   }
}
