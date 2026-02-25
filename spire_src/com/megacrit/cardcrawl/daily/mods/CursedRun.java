package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class CursedRun extends AbstractDailyMod {
   public static final String ID = "Cursed Run";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Cursed Run");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public CursedRun() {
      super("Cursed Run", NAME, DESC, "cursed_run.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
