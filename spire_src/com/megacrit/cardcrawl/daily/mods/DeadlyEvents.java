package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class DeadlyEvents extends AbstractDailyMod {
   public static final String ID = "DeadlyEvents";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("DeadlyEvents");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public DeadlyEvents() {
      super("DeadlyEvents", NAME, DESC, "deadly_events.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
