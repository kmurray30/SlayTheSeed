package com.megacrit.cardcrawl.relics;

public class GoldenIdol extends AbstractRelic {
   public static final String ID = "Golden Idol";
   public static final float MULTIPLIER = 0.25F;

   public GoldenIdol() {
      super("Golden Idol", "goldenIdolRelic.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.HEAVY);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new GoldenIdol();
   }
}
