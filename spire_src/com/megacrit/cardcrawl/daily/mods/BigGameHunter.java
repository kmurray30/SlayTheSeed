package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class BigGameHunter extends AbstractDailyMod {
   public static final String ID = "Elite Swarm";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Elite Swarm");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public BigGameHunter() {
      super("Elite Swarm", NAME, DESC, "elite_swarm.png", false);
   }

   static {
      NAME = modStrings.NAME;
   }
}
