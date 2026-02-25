package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Ending extends AbstractDailyMod {
   public static final String ID = "The Ending";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("The Ending");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Ending() {
      super("The Ending", NAME, DESC, "endless.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
