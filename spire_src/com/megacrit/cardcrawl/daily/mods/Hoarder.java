package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Hoarder extends AbstractDailyMod {
   public static final String ID = "Hoarder";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Hoarder");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Hoarder() {
      super("Hoarder", NAME, DESC, "greed.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
