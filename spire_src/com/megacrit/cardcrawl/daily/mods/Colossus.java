package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Colossus extends AbstractDailyMod {
   public static final String ID = "MonsterHunter";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("MonsterHunter");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;
   public static final float modAmount = 1.5F;

   public Colossus() {
      super("MonsterHunter", NAME, DESC, "colossus.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
