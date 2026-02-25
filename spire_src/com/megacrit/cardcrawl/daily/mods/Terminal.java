package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Terminal extends AbstractDailyMod {
   public static final String ID = "Terminal";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Terminal");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;
   public static final int ARMOR_AMT = 5;

   public Terminal() {
      super("Terminal", NAME, DESC, "tough_enemies.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
