package com.megacrit.cardcrawl.relics;

public class GoldenEye extends AbstractRelic {
   public static final String ID = "GoldenEye";

   public GoldenEye() {
      super("GoldenEye", "golden_eye.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.HEAVY);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 2 + this.DESCRIPTIONS[1];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new GoldenEye();
   }
}
