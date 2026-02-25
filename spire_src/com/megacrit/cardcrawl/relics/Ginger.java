package com.megacrit.cardcrawl.relics;

public class Ginger extends AbstractRelic {
   public static final String ID = "Ginger";

   public Ginger() {
      super("Ginger", "ginger.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Ginger();
   }
}
