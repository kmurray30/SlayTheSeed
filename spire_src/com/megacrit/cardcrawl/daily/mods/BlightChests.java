package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class BlightChests extends AbstractDailyMod {
   public static final String ID = "Blight Chests";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Blight Chests");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public BlightChests() {
      super("Blight Chests", NAME, DESC, "endless.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
