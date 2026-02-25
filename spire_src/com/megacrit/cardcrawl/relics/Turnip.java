package com.megacrit.cardcrawl.relics;

public class Turnip extends AbstractRelic {
   public static final String ID = "Turnip";

   public Turnip() {
      super("Turnip", "turnip.png", AbstractRelic.RelicTier.RARE, AbstractRelic.LandingSound.FLAT);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new Turnip();
   }
}
