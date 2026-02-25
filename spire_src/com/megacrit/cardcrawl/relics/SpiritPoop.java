package com.megacrit.cardcrawl.relics;

public class SpiritPoop extends AbstractRelic {
   public static final String ID = "Spirit Poop";

   public SpiritPoop() {
      super("Spirit Poop", "spiritPoop.png", AbstractRelic.RelicTier.SPECIAL, AbstractRelic.LandingSound.MAGICAL);
   }

   @Override
   public String getUpdatedDescription() {
      return this.DESCRIPTIONS[0];
   }

   @Override
   public AbstractRelic makeCopy() {
      return new SpiritPoop();
   }
}
