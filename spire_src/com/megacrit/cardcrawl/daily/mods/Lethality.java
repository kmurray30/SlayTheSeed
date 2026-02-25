package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Lethality extends AbstractDailyMod {
   public static final String ID = "Lethality";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Lethality");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;
   public static final int STR_AMT = 3;

   public Lethality() {
      super("Lethality", NAME, DESC, "lethal_enemies.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
