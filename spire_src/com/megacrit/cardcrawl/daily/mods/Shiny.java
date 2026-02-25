package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Shiny extends AbstractDailyMod {
   public static final String ID = "Shiny";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Shiny");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Shiny() {
      super("Shiny", NAME, DESC, "shiny.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
