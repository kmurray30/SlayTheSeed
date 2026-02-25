package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Brewmaster extends AbstractDailyMod {
   public static final String ID = "Brewmaster";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Brewmaster");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Brewmaster() {
      super("Brewmaster", NAME, DESC, "brewmaster.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
