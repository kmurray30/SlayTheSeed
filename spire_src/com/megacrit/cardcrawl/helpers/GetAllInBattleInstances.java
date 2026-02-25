package com.megacrit.cardcrawl.helpers;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import java.util.HashSet;
import java.util.UUID;

public class GetAllInBattleInstances {
   public static HashSet<AbstractCard> get(UUID uuid) {
      HashSet<AbstractCard> cards = new HashSet<>();
      if (AbstractDungeon.player.cardInUse.uuid.equals(uuid)) {
         cards.add(AbstractDungeon.player.cardInUse);
      }

      for (AbstractCard c : AbstractDungeon.player.drawPile.group) {
         if (c.uuid.equals(uuid)) {
            cards.add(c);
         }
      }

      for (AbstractCard cx : AbstractDungeon.player.discardPile.group) {
         if (cx.uuid.equals(uuid)) {
            cards.add(cx);
         }
      }

      for (AbstractCard cxx : AbstractDungeon.player.exhaustPile.group) {
         if (cxx.uuid.equals(uuid)) {
            cards.add(cxx);
         }
      }

      for (AbstractCard cxxx : AbstractDungeon.player.limbo.group) {
         if (cxxx.uuid.equals(uuid)) {
            cards.add(cxxx);
         }
      }

      for (AbstractCard cxxxx : AbstractDungeon.player.hand.group) {
         if (cxxxx.uuid.equals(uuid)) {
            cards.add(cxxxx);
         }
      }

      return cards;
   }
}
