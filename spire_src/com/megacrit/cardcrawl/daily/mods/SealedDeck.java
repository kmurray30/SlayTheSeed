package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class SealedDeck extends AbstractDailyMod {
   public static final String ID = "SealedDeck";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("SealedDeck");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public SealedDeck() {
      super("SealedDeck", NAME, DESC, "sealed_deck.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
