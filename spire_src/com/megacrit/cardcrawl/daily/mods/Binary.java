package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Binary extends AbstractDailyMod {
   public static final String ID = "Binary";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Binary");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public Binary() {
      super("Binary", NAME, DESC, "binary.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
