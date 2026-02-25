package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class TimeDilation extends AbstractDailyMod {
   public static final String ID = "Time Dilation";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Time Dilation");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public TimeDilation() {
      super("Time Dilation", NAME, DESC, "slow_start.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
