package com.megacrit.cardcrawl.relics;

public class SneckoSkull extends AbstractRelic {
   public static final String ID = "Snake Skull";
   public static final int EFFECT = 1;

   public SneckoSkull() {
      super("Snake Skull", "snakeSkull.png", AbstractRelic.RelicTier.COMMON, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 1 + this.DESCRIPTIONS[1];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new SneckoSkull();
   }
}
