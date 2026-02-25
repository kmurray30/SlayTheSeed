package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Draft extends AbstractDailyMod {
   public static final String ID = "Draft";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Draft");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Draft() {
      super("Draft", NAME, DESC, "draft.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
