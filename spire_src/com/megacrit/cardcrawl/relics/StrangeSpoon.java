package com.megacrit.cardcrawl.relics;

public class StrangeSpoon extends AbstractRelic {
   public static final String ID = "Strange Spoon";
   public static final int DISCARD_CHANCE = 50;

   public StrangeSpoon() {
      super("Strange Spoon", "bigSpoon.png", AbstractRelic.RelicTier.SHOP, AbstractRelic.LandingSound.CLINK);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new StrangeSpoon();
   }
}
