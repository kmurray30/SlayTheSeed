package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class RedCards extends AbstractDailyMod {
   public static final String ID = "Red Cards";
   private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Red Cards");
   public static final String NAME;
   public static final String DESC = modStrings.DESCRIPTION;

   public RedCards() {
      super("Red Cards", NAME, DESC, "red.png", true, AbstractPlayer.PlayerClass.IRONCLAD);
   }

   static {
      NAME = modStrings.NAME;
   }
}
