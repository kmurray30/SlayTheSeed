package com.megacrit.cardcrawl.relics;

public class Calipers extends AbstractRelic {
   public static final String ID = "Calipers";
   public static final int BLOCK_LOSS = 15;

   public Calipers() {
      super("Calipers", "calipers.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0] + 15 + this.DESCRIPTIONS[1];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Calipers();
   }
}
