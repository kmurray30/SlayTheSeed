package com.megacrit.cardcrawl.unlock;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.relics.AbstractRelic;

public class AbstractUnlock implements Comparable<AbstractUnlock> {
   public String title;
   public String key;
   public AbstractUnlock.UnlockType type;
   public AbstractPlayer player = null;
   public AbstractCard card = null;
   public AbstractRelic relic = null;

   public void render(SpriteBatch sb) {
   }

   public int compareTo(AbstractUnlock u) {
      switch (this.type) {
         case CARD:
            return this.card.cardID.compareTo(u.card.cardID);
         case RELIC:
            return this.relic.relicId.compareTo(u.relic.relicId);
         default:
            return this.title.compareTo(u.title);
      }
   }

   public void onUnlockScreenOpen() {
   }

   public static enum UnlockType {
      CARD,
      RELIC,
      LOADOUT,
      CHARACTER,
      MISC;
   }
}
