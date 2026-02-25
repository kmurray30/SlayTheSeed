package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Endless extends AbstractDailyMod {
   public static final String ID = "Endless";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Endless");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Endless() {
      super("Endless", NAME, DESC, "endless.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
