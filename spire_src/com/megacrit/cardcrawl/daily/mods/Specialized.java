package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Specialized extends AbstractDailyMod {
   public static final String ID = "Specialized";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Specialized");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Specialized() {
      super("Specialized", NAME, DESC, "specialized.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
