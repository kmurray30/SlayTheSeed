package com.megacrit.cardcrawl.relics;

public class IceCream extends AbstractRelic {
   public static final String ID = "Ice Cream";

   public IceCream() {
      super("Ice Cream", "iceCream.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new IceCream();
   }
}
