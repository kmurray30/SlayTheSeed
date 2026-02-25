package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Allstar extends AbstractDailyMod {
   public static final String ID = "Allstar";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Allstar");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Allstar() {
      super("Allstar", NAME, DESC, "all_star.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
