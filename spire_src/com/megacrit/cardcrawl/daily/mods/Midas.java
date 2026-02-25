package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Midas extends AbstractDailyMod {
   public static final String ID = "Midas";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Midas");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;
   public static final float MULTIPLIER = 2.0F;

   public Midas() {
      super("Midas", NAME, DESC, "midas.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
