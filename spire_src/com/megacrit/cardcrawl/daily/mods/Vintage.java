package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Vintage extends AbstractDailyMod {
   public static final String ID = "Vintage";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Vintage");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Vintage() {
      super("Vintage", NAME, DESC, "vintage.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
