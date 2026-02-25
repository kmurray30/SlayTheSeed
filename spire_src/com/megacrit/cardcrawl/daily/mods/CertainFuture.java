package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class CertainFuture extends AbstractDailyMod {
   public static final String ID = "Uncertain Future";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Uncertain Future");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public CertainFuture() {
      super("Uncertain Future", NAME, DESC, "certain_future.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
