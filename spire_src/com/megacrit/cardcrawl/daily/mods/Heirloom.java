package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Heirloom extends AbstractDailyMod {
   public static final String ID = "Heirloom";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Heirloom");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Heirloom() {
      super("Heirloom", NAME, DESC, "heirloom.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
