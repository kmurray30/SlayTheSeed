package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class NightTerrors extends AbstractDailyMod {
   public static final String ID = "Night Terrors";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Night Terrors");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;
   public static final float HEAL_AMT = 1.0F;
   public static final int MAX_HP_LOSS = 5;

   public NightTerrors() {
      super("Night Terrors", NAME, DESC, "night_terrors.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
