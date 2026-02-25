package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Chimera extends AbstractDailyMod {
   public static final String ID = "Chimera";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Chimera");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Chimera() {
      super("Chimera", NAME, DESC, "chimera.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
