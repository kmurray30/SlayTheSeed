package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class ColorlessCards extends AbstractDailyMod {
   public static final String ID = "Colorless Cards";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Colorless Cards");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public ColorlessCards() {
      super("Colorless Cards", NAME, DESC, "colorless.png", true);
   }

   static {
      NAME = modStrings.NAME;
   }
}
