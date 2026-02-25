package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Flight extends AbstractDailyMod {
   public static final String ID = "Flight";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Flight");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Flight() {
      super("Flight", NAME, DESC, "flight.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
