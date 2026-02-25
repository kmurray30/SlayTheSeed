package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class PurpleCards extends AbstractDailyMod {
   public static final String ID = "Purple Cards";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Purple Cards");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public PurpleCards() {
      super("Purple Cards", NAME, DESC, "purple.png", true, AbstractPlayer.PlayerClass.WATCHER);
   }

   static {
      NAME = modStrings.NAME;
   }
}
