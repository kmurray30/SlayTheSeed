package com.megacrit.cardcrawl.blights;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.BlightStrings;

public class Muzzle extends AbstractBlight {
   public static final String ID = "FullBelly";
   private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("FullBelly");
   public static final String NAME;
   public static final String[] DESC = blightStrings.DESCRIPTION;

   public Muzzle() {
      super("FullBelly", NAME, DESC[0], "muzzle.png", true);
   }

   static {
      NAME = blightStrings.NAME;
   }
}
