package com.megacrit.cardcrawl.relics;

public class WhiteBeast extends AbstractRelic {
   public static final String ID = "White Beast Statue";

   public WhiteBeast() {
      super("White Beast Statue", "whiteBeast.png", AbstractRelic.RelicTier.UNCOMMON, AbstractRelic.LandingSound.SOLID);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new WhiteBeast();
   }
}
