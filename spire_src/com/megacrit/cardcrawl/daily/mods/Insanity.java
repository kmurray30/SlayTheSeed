package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Insanity extends AbstractDailyMod {
   public static final String ID = "Insanity";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Insanity");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Insanity() {
      super("Insanity", NAME, DESC, "restless_journey.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
