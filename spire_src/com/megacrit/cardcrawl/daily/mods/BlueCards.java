package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class BlueCards extends AbstractDailyMod {
   public static final String ID = "Blue Cards";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Blue Cards");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public BlueCards() {
      super("Blue Cards", NAME, DESC, "blue.png", true, AbstractPlayer.PlayerClass.DEFECT);
   }

   static {
      NAME = modStrings.NAME;
   }
}
