package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Diverse extends AbstractDailyMod {
   public static final String ID = "Diverse";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Diverse");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;
   public static final int NON_DEFECT_MASTER_MAX_ORBS = 1;

   public Diverse() {
      super("Diverse", NAME, DESC, "diverse.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
